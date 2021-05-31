package com.github.harshadnawathe.coffeehut.domain.delivery

import com.github.harshadnawathe.coffeehut.domain.barista.PrepareOrderResult
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono


data class ServeOrderRequest(
    val orderId: String,
    val beverage: String,
    val customerName: String
)

data class ServeOrderResult(
    val orderId: String,
    val beverage: String,
    val customerName: String,
    val isSuccess: Boolean
)

interface DeliveryStatusCheck {
    fun isServeOrderSuccess(orderId: String): Mono<Boolean>
}

@Component
class DeliveryService(
    private val statusCheck: DeliveryStatusCheck
) {

    fun serveOrder(request: ServeOrderRequest): Mono<ServeOrderResult> {
        return statusCheck.isServeOrderSuccess(request.orderId)
            .map { isSuccess ->
                ServeOrderResult(request.orderId, request.beverage, request.customerName, isSuccess)
            }
    }

}