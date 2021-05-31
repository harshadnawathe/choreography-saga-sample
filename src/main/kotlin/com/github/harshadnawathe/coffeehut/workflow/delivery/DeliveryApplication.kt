package com.github.harshadnawathe.coffeehut.workflow.delivery

import com.github.harshadnawathe.coffeehut.domain.delivery.DeliveryService
import com.github.harshadnawathe.coffeehut.domain.delivery.ServeOrderRequest
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
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
            }.publish()

            val orderServed = Sinks.many().unicast().onBackpressureBuffer<OrderServedEvent>()
            val orderServedFlux = serveOrderResultFlux.filter { it.isSuccess }
                .doOnNext { orderServed.tryEmitNext(OrderServedEvent(it.orderId)) }
                .doOnComplete { orderServed.tryEmitComplete() }

            val orderSpilt = Sinks.many().unicast().onBackpressureBuffer<OrderSpiltEvent>()
            val orderSpiltFlux = serveOrderResultFlux.filter { !it.isSuccess }
                .doOnNext { orderSpilt.tryEmitNext(OrderSpiltEvent(it.orderId, it.beverage, it.customerName )) }
                .doOnComplete { orderSpilt.tryEmitComplete() }

            return@ServeOrderFunction Tuples.of(
                orderServed.asFlux().doOnSubscribe {
                    orderServedFlux.subscribe()
                    serveOrderResultFlux.connect()
                },
                orderSpilt.asFlux().doOnSubscribe {
                    orderSpiltFlux.subscribe()
                    serveOrderResultFlux.connect()
                }
            )
        }
}