package com.back.nbe12142team06.global.restclient

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.*

@Configuration
class GlobalRestClient {

    @Bean
    fun tossRestClient(
        @Value("\${custom.toss.secret-key}") tossSecretKey: String,
    ): RestClient {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(TIMEOUT)
            setReadTimeout(TIMEOUT)
        }

        // 토스 인증: "시크릿키:" 를 Base64 로 인코딩한 Basic 인증
        val basicAuth = Base64.getEncoder().encodeToString("$tossSecretKey:".toByteArray())

        return RestClient.builder()
            .baseUrl(TOSS_BASE_URL)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $basicAuth")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(factory)
            .build()
    }

    companion object {
        private const val TOSS_BASE_URL = "https://api.tosspayments.com"
        private val TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}