package com.github.harshadnawathe.coffeehut.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.fail
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@Component
class KafkaTestListener(
    private val mapper: ObjectMapper
) {
    private val queue = LinkedBlockingQueue<ConsumerRecord<String, String>>()

    fun next(timeout: Long, unit: TimeUnit): ConsumerRecord<String, String>? = queue.poll(timeout, unit)

    fun <T> expect(
        event: Class<T>,
        onTopic: String,
        timeout: Long = 10,
        unit: TimeUnit = TimeUnit.SECONDS
    ) : T {
        val record = next(timeout, unit) ?: fail {
            "Expected event ${event.name} is not available"
        }

        Assertions.assertThat(record.topic()).isEqualTo(onTopic)
        return mapper.readValue(
            record.value(),
            event
        )
    }

    @KafkaListener(
        id = "kafkaTestListener",
        topics = ["#{'\${test.listener.topics:''}'.split('\\s*,\\s*')}"],
        groupId = "kafka-test-listener-group",
        properties = [
            "${ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG}=org.apache.kafka.common.serialization.StringDeserializer",
            "${ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG}=org.apache.kafka.common.serialization.StringDeserializer"
        ]
    )
    private fun enqueue(record: ConsumerRecord<String, String>) {
        LOG.info("Received ${record.value()} on topic ${record.topic()}")
        queue.add(record)
    }

    companion object {
        @JvmStatic
        val LOG: Logger = LoggerFactory.getLogger(KafkaTestListener::class.java)
    }
}