package com.back.nbe12142team06.domain.payment.dto;

import com.back.nbe12142team06.domain.payment.entity.Payment;
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus;
import com.back.nbe12142team06.domain.post.entity.Post;
import com.back.nbe12142team06.domain.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentAdminResponse(
        Long id,
        int amount,
        int hourlyPaySnapshot,
        BigDecimal hours,
        String orderId,
        PaymentStatus paymentStatus,
        LocalDateTime approvedAt,
        LocalDateTime canceledAt,
        String cancelReason,
        Long postId,
        String postTitle,
        Long userId,
        String username
) {
    public PaymentAdminResponse(Payment payment) {
        Post post = null;
        User user = null;
        if (payment.getPost() != null) {
             post = payment.getPost();
        }
        if (post.getClient() != null) {
            user = post.getClient();
        }
        this(
                payment.getId(),
                payment.getAmount(),
                payment.getHourlyPaySnapshot(),
                payment.getHours(),
                payment.getOrderId(),
                payment.getPaymentStatus(),
                payment.getApprovedAt(),
                payment.getCanceledAt(),
                payment.getCancelReason(),
                post == null ? null : post.getId(),
                post == null ? null : post.getTitle(),
                user == null ? null : user.getId(),
                user == null ? null : user.getName()
        );
    }
}
