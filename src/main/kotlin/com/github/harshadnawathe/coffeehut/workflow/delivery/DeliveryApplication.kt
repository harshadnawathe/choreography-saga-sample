package com.github.harshadnawathe.coffeehut.workflow.delivery

import com.github.harshadnawathe.coffeehut.domain.delivery.DeliveryService
import com.github.harshadnawathe.coffeehut.domain.delivery.ServeOrderRequest
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import java.util.function.Function

data class OrderServedEvent(
    val orderId: String
)

data class OrderSpiltEvent(
    val orderId: String,
    val beverage: String,
    val customerName: String
)

typealias ServeOrderFunction = Function<Flux<ServeOrderRequest>, Tuple2<Flux<OrderServedEvent>, Flux<OrderSpiltEvent>>>

@Component
class DeliveryApplication {

    @Bean
    fun serveOrder(service: DeliveryService) =
        ServeOrderFunction { requests ->
            val serveOrderResultFlux = requests.concatMap {
                service.serveOrder(it)
            }.publish().autoConnect(2)

            return@ServeOrderFunction Tuples.of(
                serveOrderResultFlux.filter { it.isSuccess }.map { OrderServedEvent(it.orderId) },
                serveOrderResultFlux.filter { !it.isSuccess }
                    .map { OrderSpiltEvent(it.orderId, it.beverage, it.customerName) }
            )
        }
}