package com.github.harshadnawathe.coffeehut.domain.order

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "order")
class Order(
    val beverage: String,
    val customerName: String
) {
    lateinit var id: String
        private set

    @PersistenceConstructor
    constructor(id: String, beverage: String, customerName: String) : this(beverage, customerName) {
        this.id = id
    }
}