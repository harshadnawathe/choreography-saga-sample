package com.github.harshadnawathe.coffeehut.workflow.delivery

import com.github.harshadnawathe.coffeehut.domain.delivery.ServeOrderRequest
import com.github.harshadnawathe.coffeehut.util.PathMatchingDispatcher
import com.github.harshadnawathe.coffeehut.util.enqueue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
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
        "spring.cloud.function.definition=serveOrder"
    ]
)
class DeliveryApplicationTest {

    @Autowired
    lateinit var context: ApplicationContext

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
    fun `should serve the order and emit OrderServedEvent`() {
        mockServer.enqueue(
            "/delivery/order-always-success/status",
            MockResponse().addHeader("Content-Type", "application/json").setBody("true").setResponseCode(200)
        )

        input.send(
            MessageBuilder.withPayload(ServeOrderRequest("order-always-success", "Abc", "Xyz"))
                .build()
        )

        val orderServedMessage = output.receive(1000, "order-served-event-channel")
        JSONAssert.assertEquals("{\"orderId\": \"order-always-success\"}", String(orderServedMessage.payload), true)

        val orderSpiltMessage = output.receive(1000, "order-spilt-event-channel")
        assertThat(orderSpiltMessage).isNull()
    }

    @Test
    fun `should not serve the order and emit OrderSpiltEvent`() {
        mockServer.enqueue(
            "/delivery/order-always-fail/status",
            MockResponse().addHeader("Content-Type", "application/json").setBody("false").setResponseCode(200)
        )

        input.send(
            MessageBuilder.withPayload(ServeOrderRequest("order-always-fail", "Abc", "Xyz"))
                .build()
        )

        val orderSpiltMessage = output.receive(1000, "order-spilt-event-channel")
        JSONAssert.assertEquals(
            "{\"orderId\": \"order-always-fail\", \"beverage\":\"Abc\", \"customerName\":\"Xyz\"}",
            String(orderSpiltMessage.payload),
            true
        )

        val orderServedMessage = output.receive(1000, "order-served-event-channel")
        assertThat(orderServedMessage).isNull()
    }
}

