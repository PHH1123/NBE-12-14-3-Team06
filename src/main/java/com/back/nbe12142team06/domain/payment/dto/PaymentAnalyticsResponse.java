package com.back.nbe12142team06.domain.payment.dto;

import com.back.nbe12142team06.domain.payment.entity.Payment;

import java.util.List;

public record PaymentAnalyticsResponse(
        Long paidAmount,
        Long canceledAmount,
        Long counts
) {
    public PaymentAnalyticsResponse(List<Payment> payments) {
        long paidAmount = 0;
        long canceledAmount = 0;
        for (Payment payment : payments) {
            paidAmount += payment.getBalanceAmount().intValue();
            canceledAmount += payment.getAmount().subtract(payment.getBalanceAmount()).intValue();
        }
        this(paidAmount, canceledAmount, (long) payments.size());
    }
}
