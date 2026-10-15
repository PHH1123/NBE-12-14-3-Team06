package com.back.nbe12142team06.domain.payment.dto;

import com.back.nbe12142team06.domain.payment.entity.Payment;

import java.util.List;

public record PaymentAnalyticsResponse(
        Long paidAmount,
        Long canceledAmount,
        Long counts
) {
    public PaymentAnalyticsResponse(List<Payment> payments) {
        int paidAmount = 0;
        int canceledAmount = 0;
        for (Payment payment : payments) {
            paidAmount += payment.getBalanceAmount();
            canceledAmount += payment.getAmount() - payment.getBalanceAmount();
        }
        this((long) paidAmount, (long) canceledAmount, (long) payments.size());
    }
}
