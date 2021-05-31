package com.github.harshadnawathe.coffeehut.workflow.order

import com.github.harshadnawathe.coffeehut.domain.order.CancelOrderRequest
import com.github.harshadnawathe.coffeehut.domain.order.ConfirmOrderRequest
import com.github.harshadnawathe.coffeehut.domain.order.OrderRequest
import com.github.harshadnawathe.coffeehut.domain.order.OrderService
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.util.function.Function

data class OrderAcceptedEvent(
    val orderId: String,
    val beverage: String,
    val customerName: String
)

data class OrderConfirmedEvent(
    val orderId: String,
    val beverage: String,
    val customerName: String
)

data class OrderCancelledEvent(
    val orderId: String
)

@Component
class OrderApplication {

    @Bean
    fun takeOrder(service: OrderService) =
        Function<Flux<OrderRequest>, Flux<OrderAcceptedEvent>> { requests ->
            requests.flatMap {
                service.createNew(it)
            }.map {
                OrderAcceptedEvent(it.id, it.beverage, it.customerName)
            }
        }

    @Bean
    fun confirmOrder(service: OrderService) =
        Function<Flux<ConfirmOrderRequest>, Flux<OrderConfirmedEvent>> { requests ->
            requests.flatMap {
                service.confirm(it)
            }.map {
                OrderConfirmedEvent(it.id, it.beverage, it.customerName)
            }
        }

    @Bean
    fun cancelOrder(service: OrderService) =
        Function<Flux<CancelOrderRequest>,Flux<OrderCancelledEvent>> { requests ->
            requests.flatMap {
                service.cancel(it)
            }.map {
                OrderCancelledEvent(it.id)
            }
        }
}