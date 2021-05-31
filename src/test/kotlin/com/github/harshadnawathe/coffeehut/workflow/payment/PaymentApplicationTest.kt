package com.github.harshadnawathe.coffeehut.workflow.payment

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.harshadnawathe.coffeehut.domain.payment.CompletePaymentRequest
import com.github.harshadnawathe.coffeehut.domain.payment.InitiatePaymentRequest
import com.github.harshadnawathe.coffeehut.domain.payment.RefundRequest
import com.github.harshadnawathe.coffeehut.util.PathMatchingDispatcher
import com.github.harshadnawathe.coffeehut.util.enqueue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest
import org.springframework.cloud.stream.binder.test.InputDestination
import org.springframework.cloud.stream.binder.test.OutputDestination
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.messaging.support.MessageBuilder
import org.springframework.test.context.TestPropertySource

@FunctionalSpringBootTest
@Import(TestChannelBinderConfiguration::class)
@TestPropertySource(
    properties = [
        "spring.cloud.function.definition=initiatePayment;completePayment;refundPayment"
    ]
)
class PaymentApplicationTest {
    @Autowired
    lateinit var context: ApplicationContext

    @Autowired
    lateinit var mapper: ObjectMapper

    val input: InputDestination by lazy {
        context.getBean(InputDestination::class.java)
    }
    val output: OutputDestination by lazy {
        context.getBean(OutputDestination::class.java)
    }

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
        output.clear()
    }

    @Test
    fun `should initiate payment and emit PaymentInitiated`() {
        input.send(
            MessageBuilder.withPayload(InitiatePaymentRequest("some-order", "some-beverage")).build(),
            "initiate-payment-request-channel"
        )

        val message = output.receive(5000, "payment-initiated-event-channel")

        val event = mapper.readValue(message.payload, PaymentInitiatedEvent::class.java)
        assertThat(event.amount).isEqualTo(13.0)
        assertThat(event.orderId).isEqualTo("some-order")
    }

    @Test
    fun `should complete Payment and emit PaymentCompleted`() {
        mockServer.enqueue(
            "/payment/{paymentId}/status",
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("true")
                .setResponseCode(200)
        )
        input.send(
            MessageBuilder.withPayload(InitiatePaymentRequest("some-order", "some-beverage")).build(),
            "initiate-payment-request-channel"
        )
        val paymentInitiatedMessage = output.receive(5000, "payment-initiated-event-channel")
        val paymentInitiatedEvent = mapper.readValue(paymentInitiatedMessage.payload, PaymentInitiatedEvent::class.java)

        input.send(
            MessageBuilder.withPayload(
                CompletePaymentRequest(
                    paymentInitiatedEvent.paymentId,
                    paymentInitiatedEvent.amount
                )
            ).build(),
            "complete-payment-request-channel"
        )

        val paymentCompletedMessage = output.receive(5000, "payment-completed-event-channel")
        val paymentCompletedEvent = mapper.readValue(
            paymentCompletedMessage.payload,
            PaymentCompletedEvent::class.java
        )

        assertThat(paymentCompletedEvent.orderId).isEqualTo("some-order")
        assertThat(paymentCompletedEvent.paymentId).isEqualTo(paymentInitiatedEvent.paymentId)

        val paymentFailedMessage = output.receive(5000, "payment-failed-event-channel")
        assertThat(paymentFailedMessage).isNull()
    }

    @Test
    fun `should not complete Payment and emit PaymentFailedEvent`() {
        mockServer.enqueue(
            "/payment/{paymentId}/status",
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("false")
                .setResponseCode(200)
        )
        input.send(
            MessageBuilder.withPayload(InitiatePaymentRequest("some-order", "some-beverage")).build(),
            "initiate-payment-request-channel"
        )
        val paymentInitiatedMessage = output.receive(5000, "payment-initiated-event-channel")
        val paymentInitiatedEvent = mapper.readValue(paymentInitiatedMessage.payload, PaymentInitiatedEvent::class.java)

        input.send(
            MessageBuilder.withPayload(
                CompletePaymentRequest(
                    paymentInitiatedEvent.paymentId,
                    paymentInitiatedEvent.amount
                )
            ).build(),
            "complete-payment-request-channel"
        )

        val paymentFailedMessage = output.receive(5000, "payment-failed-event-channel")
        val paymentFailedEvent = mapper.readValue(
            paymentFailedMessage.payload,
            PaymentFailedEvent::class.java
        )
        assertThat(paymentFailedEvent.orderId).isEqualTo("some-order")
        assertThat(paymentFailedEvent.paymentId).isEqualTo(paymentInitiatedEvent.paymentId)

        val paymentCompletedMessage = output.receive(5000, "payment-completed-event-channel")
        assertThat(paymentCompletedMessage).isNull()
    }

    @Test
    fun `should process refund and emit PaymentRefundedEvent`() {
        mockServer.enqueue(
            "/payment/{paymentId}/status",
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("true")
                .setResponseCode(200)
        )
        input.send(
            MessageBuilder.withPayload(InitiatePaymentRequest("other-order", "some-beverage")).build(),
            "initiate-payment-request-channel"
        )
        val paymentInitiatedMessage = output.receive(5000, "payment-initiated-event-channel")
        val paymentInitiatedEvent = mapper.readValue(paymentInitiatedMessage.payload, PaymentInitiatedEvent::class.java)

        input.send(
            MessageBuilder.withPayload(
                RefundRequest(
                    "other-order"
                )
            ).build(),
            "refund-payment-request-channel"
        )

        val paymentRefundedMessage = output.receive(5000, "payment-refunded-event-channel")
        val paymentRefundedEvent = mapper.readValue(
            paymentRefundedMessage.payload,
            PaymentRefundedEvent::class.java
        )

        assertThat(paymentRefundedEvent.orderId).isEqualTo("other-order")
        assertThat(paymentRefundedEvent.paymentId).isEqualTo(paymentInitiatedEvent.paymentId)
        assertThat(paymentRefundedEvent.amount).isEqualTo(paymentInitiatedEvent.amount)
    }
}