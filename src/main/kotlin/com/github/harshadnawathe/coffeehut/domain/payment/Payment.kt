package com.github.harshadnawathe.coffeehut.domain.payment

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "payment")
class Payment(
    @Indexed val orderId: String,
    val amount: Double
) {
    @Id
    lateinit var id: String
        private set

    @PersistenceConstructor
    constructor(id: String, orderId: String, amount: Double) : this(orderId, amount) {
        this.id = id
    }
}