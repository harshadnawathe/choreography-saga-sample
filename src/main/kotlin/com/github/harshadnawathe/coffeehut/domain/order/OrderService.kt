package com.github.harshadnawathe.coffeehut.domain.order

import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

data class OrderRequest(
    val beverage: String,
    val customerName: String
)

data class ConfirmOrderRequest(
    val orderId: String,
    val paymentId: String
)

data class CancelOrderRequest(
    val orderId: String
)

@Component
class OrderService(
    private val repository: OrderRepository
) {

    fun createNew(request: OrderRequest): Mono<Order> {
        return repository.save(Order(request.beverage, request.customerName))
    }

    fun confirm(request: ConfirmOrderRequest): Mono<Order> {
        return repository.findById(request.orderId)
    }

    fun cancel(request: CancelOrderRequest) : Mono<Order> {
        return repository.findById(request.orderId)
    }
}