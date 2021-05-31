package com.github.harshadnawathe.coffeehut.domain.payment

import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2

data class InitiatePaymentRequest(
    val orderId: String,
    val beverage: String
)

data class CompletePaymentRequest(
    val paymentId: String,
    val amount: Double
)

data class PaymentConfirmation(
    val paymentId: String,
    val orderId: String,
    val isSuccess: Boolean
)

data class PaymentResult(
    val paymentId: String,
    val isSuccess: Boolean
)

data class RefundRequest(
    val orderId: String
)

data class RefundConfirmation(
    val orderId: String,
    val paymentId: String,
    val amount: Double
)

interface PaymentGateway {
    fun completePayment(paymentId: String) : Mono<PaymentResult>
}

@Component
class PaymentService(
    private val repository: PaymentRepository,
    private val paymentGateway: PaymentGateway
) {

    fun createNew(request: InitiatePaymentRequest): Mono<Payment> {
        val amount = request.beverage.length.toDouble()
        return repository.save(Payment(request.orderId, amount))
    }

    fun complete(request: CompletePaymentRequest): Mono<PaymentConfirmation> {
        return repository.findById(request.paymentId)
            .zipWith(paymentGateway.completePayment(request.paymentId))
            .map { (payment, confirmation) ->
                PaymentConfirmation(payment.id, payment.orderId, confirmation.isSuccess)
            }
    }

    fun refund(request: RefundRequest): Mono<RefundConfirmation> {
        return repository.findByOrderId(request.orderId)
            .map {
                RefundConfirmation(it.orderId, it.id, it.amount)
            }
    }
}