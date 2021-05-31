package com.github.harshadnawathe.coffeehut.domain.order

import org.springframework.data.mongodb.repository.ReactiveMongoRepository


interface OrderRepository : ReactiveMongoRepository<Order, String>