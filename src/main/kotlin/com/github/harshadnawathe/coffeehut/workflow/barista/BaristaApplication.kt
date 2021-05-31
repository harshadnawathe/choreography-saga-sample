package com.github.harshadnawathe.coffeehut.workflow.barista

import com.github.harshadnawathe.coffeehut.domain.barista.BaristaService
import com.github.harshadnawathe.coffeehut.domain.barista.PrepareOrderRequest
import com.github.harshadnawathe.coffeehut.domain.barista.PrepareOrderResult
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import java.util.function.Function


data class OrderPreparedEvent(
    val orderId: String,
    val beverage: String,
    val customerName: String
)

data class OrderPreparationFailedEvent(
    val orderId: String
)

typealias PrepareOrderFunction = Function<Flux<PrepareOrderRequest>, Tuple2<Flux<OrderPreparedEvent>, Flux<OrderPreparationFailedEvent>>>

@Component
class BaristaApplication {

    @Bean
    fun prepareOrder(service: BaristaService) =
        PrepareOrderFunction { requests ->

            val beveragesFlux = requests.concatMap { request ->
                service.prepareOrder(request)
            }.map {
                when (it) {
                    is PrepareOrderResult.Beverage -> OrderPreparedEvent(it.orderId, it.beverage, it.customerName)
                    is PrepareOrderResult.NoBeverage -> OrderPreparationFailedEvent(it.orderId)
                }
            }.publish()

            val orderPrepared = Sinks.many().unicast().onBackpressureBuffer<OrderPreparedEvent>()
            val orderPreparedFlux = beveragesFlux.filter { it is OrderPreparedEvent }
                .cast(OrderPreparedEvent::class.java)
                .doOnNext { orderPrepared.tryEmitNext(it) }
                .doOnComplete { orderPrepared.tryEmitComplete() }


            val orderPreparationFailed = Sinks.many().unicast().onBackpressureBuffer<OrderPreparationFailedEvent>()
            val orderPreparationFailedFlux = beveragesFlux.filter { it is OrderPreparationFailedEvent }
                .cast(OrderPreparationFailedEvent::class.java)
                .doOnNext { orderPreparationFailed.tryEmitNext(it) }
                .doOnComplete { orderPreparationFailed.tryEmitComplete() }

            return@PrepareOrderFunction Tuples.of(
                orderPrepared.asFlux().doOnSubscribe {
                    orderPreparedFlux.subscribe()
                    beveragesFlux.connect()
                },
                orderPreparationFailed.asFlux().doOnSubscribe {
                    orderPreparationFailedFlux.subscribe()
                    beveragesFlux.connect()
                }
            )
        }


}