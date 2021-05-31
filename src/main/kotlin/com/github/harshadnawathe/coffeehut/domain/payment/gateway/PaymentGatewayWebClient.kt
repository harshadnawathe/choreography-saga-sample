package com.github.harshadnawathe.coffeehut.domain.payment.gateway

import com.github.harshadnawathe.coffeehut.domain.payment.PaymentGateway
import com.github.harshadnawathe.coffeehut.domain.payment.PaymentResult
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
@ConfigurationProperties(prefix = "coffeehut.payment.client.http")
class PaymentGatewayWebClient(
    webClientBuilder: WebClient.Builder
) : PaymentGateway {

    lateinit var baseUrl : String

    private val webClient by lazy {
        webClientBuilder.baseUrl(baseUrl).build()
    }

    override fun completePayment(paymentId: String): Mono<PaymentResult> {
        return webClient.get()
            .uri("/{paymentId}/status", paymentId)
            .exchangeToMono { response ->
                response.bodyToMono(Boolean::class.java)
            }
            .map {
                PaymentResult(paymentId, it)
            }
    }
}