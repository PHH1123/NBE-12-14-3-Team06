package com.back.nbe12142team06.domain.payment.client;

import com.back.nbe12142team06.domain.payment.dto.TossConfirmResponse;
import org.springframework.http.ResponseEntity;

public interface TossPaymentClient {

    ResponseEntity<TossConfirmResponse> callApiConfirm(String tossPaymentKey, String tossOrderId, String amount);

    ResponseEntity<TossConfirmResponse> callApiCancel(String cancelReason, String tossPaymentKey, String amount);
}
