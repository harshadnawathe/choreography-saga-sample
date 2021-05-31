package com.github.harshadnawathe.coffeehut.workflow.barista

import com.github.harshadnawathe.coffeehut.domain.barista.PrepareOrderRequest
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
        "spring.cloud.function.definition=prepareOrder"
    ]
)
class BaristaApplicationTest {

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
    fun `should process request and emit OrderPreparedEvent`() {
        mockServer.enqueue(
            "/barista/order-always-success/status",
            MockResponse().addHeader("Content-Type", "application/json")
                .setBody("true")
                .setResponseCode(200)
        )

        input.send(
            MessageBuilder.withPayload(PrepareOrderRequest("order-always-success", "some-beverage", "Abc")).build()
        )

        val orderPreparedMessage = output.receive(1000, "order-prepared-event-channel")
        JSONAssert.assertEquals(
            "{\"orderId\":\"order-always-success\", \"beverage\":\"some-beverage\", \"customerName\":\"Abc\"}",
            String(orderPreparedMessage.payload),
            true
        )

        val orderPreparationFailedMessage = output.receive(1000, "order-preparation-failed-event-channel")
        assertThat(orderPreparationFailedMessage).isNull()
    }

    @Test
    fun `should process request and emit OrderPreparationFailedEvent`() {
        mockServer.enqueue(
            "/barista/order-always-fail/status",
            MockResponse().addHeader("Content-Type", "application/json").setBody("false").setResponseCode(200)
        )

        input.send(MessageBuilder.withPayload(PrepareOrderRequest("order-always-fail", "some-beverage", "Abc")).build())

        val orderPreparationFailedMessage = output.receive(1000, "order-preparation-failed-event-channel")
        JSONAssert.assertEquals(
            "{\"orderId\":\"order-always-fail\"}",
            String(orderPreparationFailedMessage.payload),
            true
        )

        val orderPreparedMessage = output.receive(1000, "order-prepared-event-channel")
        assertThat(orderPreparedMessage).isNull()
    }
}