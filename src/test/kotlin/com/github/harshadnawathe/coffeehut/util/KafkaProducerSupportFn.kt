package com.github.harshadnawathe.coffeehut.util

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.KafkaTestUtils

fun testKafkaTemplateForStringValue(broker: EmbeddedKafkaBroker): KafkaTemplate<String, String> {
    val producerProps = KafkaTestUtils.producerProps(broker).also {
        it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
    }

    return KafkaTemplate(
        DefaultKafkaProducerFactory(producerProps)
    )
}
