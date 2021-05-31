package com.github.harshadnawathe.coffeehut.domain.payment

import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface PaymentRepository : ReactiveMongoRepository<Payment, String> {
    fun findByOrderId(orderId: String) : Mono<Payment>
}