package com.back.nbe12142team06.domain.payment.client;

import com.back.nbe12142team06.domain.payment.dto.TossConfirmResponse;
import com.back.nbe12142team06.global.exception.InternalServerErrorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("prod")
public class TossPaymentClientProd implements TossPaymentClient {

    private final RestClient tossRestClient;
    private final ObjectMapper objectMapper;

    public ResponseEntity<TossConfirmResponse> callApiConfirm(String tossPaymentKey, String tossOrderId, String amount) {

        String requestBody = objectMapper.createObjectNode()
                .put("paymentKey", tossPaymentKey)
                .put("orderId", tossOrderId)
                .put("amount", amount)
                .toPrettyString();

        ResponseEntity<TossConfirmResponse> response;
        try {
            response = tossRestClient.post()
                    .uri("/v1/payments/confirm")
                    .body(requestBody)
                    .retrieve()
                    .toEntity(TossConfirmResponse.class);
        } catch (RuntimeException e) {
            throw new InternalServerErrorException(43, e.getMessage());
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new InternalServerErrorException(43, "토스 결제 승인 API 호출 실패, 결제 승인에 실패했습니다.");
        }

        log.info("토스 결제 승인 요청 성공 - tossOrderId: {}, amount: {}", tossOrderId, amount);

        return response;
    }

    public ResponseEntity<TossConfirmResponse> callApiCancel(String cancelReason, String tossPaymentKey, String amount) {

        // 요청 DTO를 JSON으로 변환
        String requestBody = objectMapper.createObjectNode()
                .put("cancelReason", cancelReason)
                .put("cancelAmount", amount)
                .toPrettyString();

        ResponseEntity<TossConfirmResponse> response;
        try {
            response = tossRestClient.post()
                    .uri("/v1/payments/%s/cancel".formatted(tossPaymentKey))
                    .body(requestBody)
                    .retrieve()
                    .toEntity(TossConfirmResponse.class);
        } catch (RuntimeException e) {
            throw new InternalServerErrorException(44, e.getMessage());
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new InternalServerErrorException(44, "토스 결제 취소 API 호출 실패, 결제 취소에 실패했습니다.");
        }

        log.info("토스 결제 취소 요청 성공 - cancelReason: {}, amount: {}", cancelReason, amount);

        return response;
    }
}
