package com.github.harshadnawathe.coffeehut.workflow.order

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.harshadnawathe.coffeehut.domain.order.CancelOrderRequest
import com.github.harshadnawathe.coffeehut.domain.order.ConfirmOrderRequest
import com.github.harshadnawathe.coffeehut.domain.order.OrderRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
        "spring.cloud.function.definition=takeOrder;confirmOrder;cancelOrder"
    ]
)
class OrderApplicationTest {

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

    @AfterEach
    internal fun tearDown() {
        output.clear()
    }

    @Test
    fun `should take order and emit OrderAcceptedEvent`() {
        input.send(
            MessageBuilder.withPayload(OrderRequest("Abc", "Xyz")).build(),
            "order-request-channel"
        )

        val orderAcceptedMessage = output.receive(5000, "order-accepted-event-channel")
        val orderAcceptedEvent = mapper.readValue(orderAcceptedMessage.payload, OrderAcceptedEvent::class.java)

        assertThat(orderAcceptedEvent.orderId).isNotBlank()
        assertThat(orderAcceptedEvent.beverage).isEqualTo("Abc")
        assertThat(orderAcceptedEvent.customerName).isEqualTo("Xyz")
    }

    @Test
    fun `should confirm order and emit OrderConfirmedEvent`() {
        input.send(
            MessageBuilder.withPayload(OrderRequest("Abc", "Xyz")).build(),
            "order-request-channel"
        )

        val orderAcceptedMessage = output.receive(5000, "order-accepted-event-channel")
        val orderAcceptedEvent = mapper.readValue(orderAcceptedMessage.payload, OrderAcceptedEvent::class.java)


        input.send(
            MessageBuilder.withPayload(ConfirmOrderRequest(orderAcceptedEvent.orderId, "some-payment")).build(),
            "confirm-order-request-channel"
        )

        val orderConfirmedMessage = output.receive(5000, "order-confirmed-event-channel")
        val orderConfirmedEvent = mapper.readValue(orderConfirmedMessage.payload, OrderConfirmedEvent::class.java)

        assertThat(orderConfirmedEvent.orderId).isEqualTo(orderAcceptedEvent.orderId)
        assertThat(orderConfirmedEvent.beverage).isEqualTo("Abc")
        assertThat(orderConfirmedEvent.customerName).isEqualTo("Xyz")
    }

    @Test
    fun `should cancel order and emit OrderCancelledEvent`() {
        input.send(
            MessageBuilder.withPayload(OrderRequest("Abc", "Xyz")).build(),
            "order-request-channel"
        )

        val orderAcceptedMessage = output.receive(5000, "order-accepted-event-channel")
        val orderAcceptedEvent = mapper.readValue(orderAcceptedMessage.payload, OrderAcceptedEvent::class.java)


        input.send(
            MessageBuilder.withPayload(CancelOrderRequest(orderAcceptedEvent.orderId)).build(),
            "cancel-order-request-channel"
        )

        val orderCancelledMessage = output.receive(5000, "order-cancelled-event-channel")
        val orderCancelledEvent = mapper.readValue(orderCancelledMessage.payload, OrderCancelledEvent::class.java)

        assertThat(orderCancelledEvent.orderId).isEqualTo(orderAcceptedEvent.orderId)
    }
}