package com.github.harshadnawathe.coffeehut.domain.barista

import org.springframework.stereotype.Component
import reactor.core.publisher.Mono


sealed class PrepareOrderResult(val orderId: String) {

    class Beverage(
        orderId: String,
        val beverage: String,
        val customerName: String
    ) : PrepareOrderResult(orderId)

    class NoBeverage(
        orderId: String
    ) : PrepareOrderResult(orderId)
}

interface PrepareOrderSuccessCheck {
    fun isPrepareOrderSuccess(orderId: String) : Mono<Boolean>
}

@Component
class BaristaService(
    private val successCheck: PrepareOrderSuccessCheck
) {

    fun prepareOrder(request: PrepareOrderRequest): Mono<PrepareOrderResult> {
        return successCheck.isPrepareOrderSuccess(request.orderId)
            .map { isSuccess ->
                if(isSuccess) {
                    PrepareOrderResult.Beverage(request.orderId, request.beverage, request.customerName)
                }else{
                    PrepareOrderResult.NoBeverage(request.orderId)
                }
            }
    }
}

data class PrepareOrderRequest(
    val orderId: String,
    val beverage: String,
    val customerName: String
)