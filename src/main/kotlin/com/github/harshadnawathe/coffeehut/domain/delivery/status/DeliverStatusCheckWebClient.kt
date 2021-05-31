package com.github.harshadnawathe.coffeehut.domain.delivery.status

import com.github.harshadnawathe.coffeehut.domain.delivery.DeliveryStatusCheck
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
@ConfigurationProperties(prefix = "coffeehut.delivery.client.http")
class DeliverStatusCheckWebClient(
    webClientBuilder: WebClient.Builder
) : DeliveryStatusCheck {

    lateinit var baseUrl : String

    private val webClient by lazy {
        webClientBuilder.baseUrl(baseUrl).build()
    }

    override fun isServeOrderSuccess(orderId: String): Mono<Boolean> {
        return webClient.get()
            .uri("/{orderId}/status", orderId)
            .exchangeToMono { response ->
                response.bodyToMono(Boolean::class.java)
            }
    }
}