package com.github.harshadnawathe.coffeehut.workflow

import com.github.harshadnawathe.coffeehut.domain.order.OrderRequest
import com.github.harshadnawathe.coffeehut.domain.payment.CompletePaymentRequest
import com.github.harshadnawathe.coffeehut.util.KafkaTestListener
import com.github.harshadnawathe.coffeehut.util.PathMatchingDispatcher
import com.github.harshadnawathe.coffeehut.util.enqueue
import com.github.harshadnawathe.coffeehut.util.testKafkaTemplateForStringValue
import com.github.harshadnawathe.coffeehut.workflow.barista.OrderPreparationFailedEvent
import com.github.harshadnawathe.coffeehut.workflow.barista.OrderPreparedEvent
import com.github.harshadnawathe.coffeehut.workflow.delivery.OrderServedEvent
import com.github.harshadnawathe.coffeehut.workflow.delivery.OrderSpiltEvent
import com.github.harshadnawathe.coffeehut.workflow.order.OrderAcceptedEvent
import com.github.harshadnawathe.coffeehut.workflow.order.OrderCancelledEvent
import com.github.harshadnawathe.coffeehut.workflow.order.OrderConfirmedEvent
import com.github.harshadnawathe.coffeehut.workflow.payment.PaymentCompletedEvent
import com.github.harshadnawathe.coffeehut.workflow.payment.PaymentInitiatedEvent
import com.github.harshadnawathe.coffeehut.workflow.payment.PaymentRefundedEvent
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.messaging.support.MessageBuilder
import org.springframework.test.context.TestPropertySource


@SpringBootTest
@EmbeddedKafka(partitions = 1)
@TestPropertySource(
    properties = [
        "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
        "test.listener.topics=order-accepted-event-channel," +
                "payment-initiated-event-channel," +
                "payment-completed-event-channel,payment-failed-event-channel," +
                "order-confirmed-event-channel," +
                "order-prepared-event-channel,order-preparation-failed-event-channel," +
                "order-served-event-channel,order-spilt-event-channel," +
                "order-cancelled-event-channel," +
                "payment-refunded-event-channel"
    ]
)
class WorkflowTest {
    @Autowired
    lateinit var context: ApplicationContext

    @Autowired
    lateinit var listener: KafkaTestListener

    private val mockServer = MockWebServer().apply {
        dispatcher = PathMatchingDispatcher()
    }

    @BeforeEach
    fun setUp() {
        mockServer.start(8400)
    }

    @AfterEach
    internal fun tearDown() {
        mockServer.shutdown()
    }

    private val kafka
        get() = testKafkaTemplateForStringValue(context.getBean(EmbeddedKafkaBroker::class.java))

    @Test
    fun `end to end happy path works`() {
        mockServer.enqueue(
            "/payment/{paymentId}/status",
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("true")
                .setResponseCode(200)
        )

        mockServer.enqueue(
            "/barista/{orderId}/status",
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("true")
                .setResponseCode(200)
        )

        mockServer.enqueue(
            "/delivery/{orderId}/status",
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("true")
                .setResponseCode(200)
        )

        kafka.send(
            MessageBuilder.withPayload(OrderRequest("Americano", "Tony Stark"))
                .setHeader(KafkaHeaders.TOPIC, "order-request-channel")
                .build()
        )

        val orderAcceptedEvent = listener.expect(
            event = OrderAcceptedEvent::class.java,
            onTopic = "order-accepted-event-channel"
        )
        assertThat(orderAcceptedEvent.customerName).isEqualTo("Tony Stark")
        assertThat(orderAcceptedEvent.beverage).isEqualTo("Americano")

        val paymentInitiatedEvent = listener.expect(
            event = PaymentInitiatedEvent::class.java,
            onTopic = "payment-initiated-event-channel"
        )
        assertThat(paymentInitiatedEvent.orderId).isEqualTo(orderAcceptedEvent.orderId)

        kafka.send(
            MessageBuilder.withPayload(
                CompletePaymentRequest(paymentInitiatedEvent.paymentId, paymentInitiatedEvent.amount)
            ).setHeader(KafkaHeaders.TOPIC, "complete-payment-request-channel").build()
        )

        val paymentCompletedEvent = listener.expect(
            event = PaymentCompletedEvent::class.java,
            onTopic = "payment-completed-event-channel"
        )
        assertThat(paymentCompletedEvent.orderId).isEqualTo(orderAcceptedEvent.orderId)
        assertThat(paymentCompletedEvent.paymentId).isEqualTo(paymentInitiatedEvent.paymentId)

        val orderConfirmedEvent = listener.expect(
            event = OrderConfirmedEvent::class.java,
            onTopic = "order-confirmed-event-channel"
        )
        assertThat(orderConfirmedEvent.orderId).isEqualTo(orderAcceptedEvent.orderId)
        assertThat(orderConfirmedEvent.beverage).isEqualTo(orderAcceptedEvent.beverage)
        assertThat(orderConfirmedEvent.customerName).isEqualTo(orderAcceptedEvent.customerName)

        val orderPreparedEvent = listener.expect(
            event = OrderPreparedEvent::class.java,
            onTopic = "order-prepared-event-channel"
        )
        assertThat(orderPreparedEvent.orderId).isEqualTo(orderAcceptedEvent.orderId)
        assertThat(orderPreparedEvent.beverage).isEqualTo(orderAcceptedEvent.beverage)
        assertThat(orderPreparedEvent.customerName).isEqualTo(orderAcceptedEvent.customerName)

        val orderServedEvent = listener.expect(
            event = OrderServedEvent::class.java,
            onTopic = "order-served-event-channel"
        )
        assertThat(orderServedEvent.orderId).isEqualTo(orderAcceptedEvent.orderId)
    }

    @Test
    fun `recovery flow works when preparation fails`() {
        mockServer.enqueue(
            "/payment/{paymentId}/status",
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("true")
                .setResponseCode(200)
        )

        mockServer.enqueue(
            "/barista/{orderId}/status",
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("false")
                .setResponseCode(200)
        )

        kafka.send(
            MessageBuilder.withPayload(OrderRequest("Cafe Latte", "Peter Parker"))
                .setHeader(KafkaHeaders.TOPIC, "order-request-channel")
                .build()
        )

        val (orderId, _, _) = listener.expect(
            event = OrderAcceptedEvent::class.java,
            onTopic = "order-accepted-event-channel"
        )

        val (paymentId, _, amount) = listener.expect(
            event = PaymentInitiatedEvent::class.java,
            onTopic = "payment-initiated-event-channel"
        )

        kafka.send(
            MessageBuilder.withPayload(
                CompletePaymentRequest(paymentId, amount)
            ).setHeader(KafkaHeaders.TOPIC, "complete-payment-request-channel").build()
        )

        listener.expect(event = PaymentCompletedEvent::class.java, onTopic = "payment-completed-event-channel")
        listener.expect(event = OrderConfirmedEvent::class.java, onTopic = "order-confirmed-event-channel")

        val orderPreparationFailedEvent = listener.expect(
            event = OrderPreparationFailedEvent::class.java,
            onTopic = "order-preparation-failed-event-channel"
        )
        assertThat(orderPreparationFailedEvent.orderId).isEqualTo(orderId)

        val orderCancelledEvent = listener.expect(
            event = OrderCancelledEvent::class.java,
            onTopic = "order-cancelled-event-channel"
        )
        assertThat(orderCancelledEvent.orderId).isEqualTo(orderId)

        val paymentRefundedEvent = listener.expect(
            event = PaymentRefundedEvent::class.java,
            onTopic = "payment-refunded-event-channel"
        )
        assertThat(paymentRefundedEvent.orderId).isEqualTo(orderId)
        assertThat(paymentRefundedEvent.paymentId).isEqualTo(paymentId)
        assertThat(paymentRefundedEvent.amount).isEqualTo(amount)
    }

    @Test
    fun `compensation flow works when server order fails`() {
        mockServer.enqueue(
            "/payment/{paymentId}/status",
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("true")
                .setResponseCode(200)
        )

        mockServer.enqueue(
            "/barista/{orderId}/status",
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("true")
                .setResponseCode(200)
        )

        mockServer.enqueue(
            "/delivery/{orderId}/status",
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("false")
                .setResponseCode(200),
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("true")
                .setResponseCode(200)
        )

        kafka.send(
            MessageBuilder.withPayload(OrderRequest("Espresso", "Stephen Strange"))
                .setHeader(KafkaHeaders.TOPIC, "order-request-channel")
                .build()
        )

        val (orderId, beverage, customerName) = listener.expect(
            event = OrderAcceptedEvent::class.java,
            onTopic = "order-accepted-event-channel"
        )

        val (paymentId, _, amount) = listener.expect(
            event = PaymentInitiatedEvent::class.java,
            onTopic = "payment-initiated-event-channel"
        )

        kafka.send(
            MessageBuilder.withPayload(
                CompletePaymentRequest(paymentId, amount)
            ).setHeader(KafkaHeaders.TOPIC, "complete-payment-request-channel").build()
        )

        listener.expect(event = PaymentCompletedEvent::class.java, onTopic = "payment-completed-event-channel")
        listener.expect(event = OrderConfirmedEvent::class.java, onTopic = "order-confirmed-event-channel")
        listener.expect(event = OrderPreparedEvent::class.java, onTopic = "order-prepared-event-channel")
        listener.expect(event = OrderSpiltEvent::class.java, onTopic = "order-spilt-event-channel")

        val orderPreparedEvent = listener.expect(
            event = OrderPreparedEvent::class.java,
            onTopic = "order-prepared-event-channel"
        )
        assertThat(orderPreparedEvent.orderId).isEqualTo(orderId)
        assertThat(orderPreparedEvent.beverage).isEqualTo(beverage)
        assertThat(orderPreparedEvent.customerName).isEqualTo(customerName)

        listener.expect(event = OrderServedEvent::class.java, onTopic = "order-served-event-channel")
    }
}
