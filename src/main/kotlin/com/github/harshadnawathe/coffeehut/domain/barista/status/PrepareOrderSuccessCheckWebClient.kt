package com.github.harshadnawathe.coffeehut.domain.barista.status

import com.github.harshadnawathe.coffeehut.domain.barista.PrepareOrderSuccessCheck
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
@ConfigurationProperties(prefix = "coffeehut.barista.client.http")
class PrepareOrderSuccessCheckWebClient(
    webClientBuilder: WebClient.Builder
) : PrepareOrderSuccessCheck {

    lateinit var baseUrl : String

    private val webClient by lazy {
        webClientBuilder.baseUrl(baseUrl).build()
    }

    override fun isPrepareOrderSuccess(orderId: String): Mono<Boolean> {
        return webClient.get()
            .uri("/{orderId}/status", orderId)
            .exchangeToMono { response ->
                response.bodyToMono(Boolean::class.java)
            }
    }
}