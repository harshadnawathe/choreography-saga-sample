package com.github.harshadnawathe.coffeehut.workflow.payment

import com.github.harshadnawathe.coffeehut.domain.payment.CompletePaymentRequest
import com.github.harshadnawathe.coffeehut.domain.payment.InitiatePaymentRequest
import com.github.harshadnawathe.coffeehut.domain.payment.PaymentService
import com.github.harshadnawathe.coffeehut.domain.payment.RefundRequest
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.util.function.Tuple2
import reactor.util.function.Tuples
import java.util.function.Function

data class PaymentInitiatedEvent(
    val paymentId: String,
    val orderId: String,
    val amount: Double
)

data class PaymentCompletedEvent(
    val paymentId: String,
    val orderId: String
)

data class PaymentFailedEvent(
    val paymentId: String,
    val orderId: String
)

data class PaymentRefundedEvent(
    val paymentId: String,
    val orderId: String,
    val amount: Double
)

typealias CompletePaymentFunction = Function<Flux<CompletePaymentRequest>, Tuple2<Flux<PaymentCompletedEvent>, Flux<PaymentFailedEvent>>>

@Component
class PaymentApplication {

    @Bean
    fun initiatePayment(service: PaymentService) =
        Function<Flux<InitiatePaymentRequest>, Flux<PaymentInitiatedEvent>> { requests ->
            requests.flatMap {
                service.createNew(it)
            }.map {
                PaymentInitiatedEvent(it.id, it.orderId, it.amount)
            }
        }

    @Bean
    fun completePayment(service: PaymentService) =
        CompletePaymentFunction { requests ->
            val paymentConfirmationFlux = requests.flatMap {
                service.complete(it)
            }.publish().autoConnect(2)

            return@CompletePaymentFunction Tuples.of(
                paymentConfirmationFlux.filter { it.isSuccess }.map { PaymentCompletedEvent(it.paymentId, it.orderId) },
                paymentConfirmationFlux.filter { !it.isSuccess }.map { PaymentFailedEvent(it.paymentId, it.orderId) }
            )
        }

    @Bean
    fun refundPayment(service: PaymentService) =
        Function<Flux<RefundRequest>, Flux<PaymentRefundedEvent>> { requests ->
            requests.flatMap { service.refund(it) }
                .map { PaymentRefundedEvent(it.paymentId, it.orderId, it.amount) }
        }
}