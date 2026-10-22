package com.back.nbe12142team06.domain.payment.service;

import com.back.nbe12142team06.domain.application.entity.Application;
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus;
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository;
import com.back.nbe12142team06.domain.notification.service.NoticeService;
import com.back.nbe12142team06.domain.payment.client.TossPaymentClient;
import com.back.nbe12142team06.domain.payment.dto.PaymentCancelRequest;
import com.back.nbe12142team06.domain.payment.dto.PaymentConfirmRequest;
import com.back.nbe12142team06.domain.payment.dto.SaveAmountRequest;
import com.back.nbe12142team06.domain.payment.dto.TossConfirmResponse;
import com.back.nbe12142team06.domain.payment.entity.Payment;
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus;
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository;
import com.back.nbe12142team06.domain.payment.repository.dto.PaymentDailyAnalyticsDto;
import com.back.nbe12142team06.domain.post.entity.Post;
import com.back.nbe12142team06.domain.post.entity.PostStatus;
import com.back.nbe12142team06.domain.settlement.service.SettlementService;
import com.back.nbe12142team06.global.exception.InternalServerErrorException;
import com.back.nbe12142team06.global.exception.InvalidException;
import com.back.nbe12142team06.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.back.nbe12142team06.domain.notification.entity.NotificationContent.*;
import static com.back.nbe12142team06.domain.notification.entity.NotificationUrl.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentPersistenceService paymentPersistenceService;
    private final TossPaymentClient tossPaymentClient;
    private final SettlementService settlementService;
    private final NoticeService noticeService;

    @CacheEvict(cacheNames = "postList", allEntries = true)
    public Payment confirm(PaymentConfirmRequest request, Long paymentId, Long userId) {

        Payment payment = paymentPersistenceService.confirmUpdate(userId, paymentId, request.amount());

        String tossPaymentKey = request.paymentKey();
        String tossOrderId = request.orderId();
        String amount = request.amount();

        // 2. 외부 API 호출
        ResponseEntity<TossConfirmResponse> response;
        try {
            response = tossPaymentClient.callApiConfirm(tossPaymentKey, tossOrderId, amount);
        } catch (InternalServerErrorException e) {
            // 결제 실패 시 결제 데이터 하드 삭제
            paymentPersistenceService.failedConfirm(paymentId);
            throw e;
        }
        // 3. DB 반영
        try {
            payment = paymentPersistenceService.paymentSaveDb(response, paymentId, tossPaymentKey, tossOrderId);
        } catch (NotFoundException e) {
            cancel(userId, paymentId, new PaymentCancelRequest("서버 에러 발생"));
            log.error("결제 승인 실패", e);
            throw e;
        } catch (RuntimeException ex) {
            cancel(userId, paymentId, new PaymentCancelRequest("서버 에러 발생"));
            log.error("결제 승인 실패", ex);
            throw new InternalServerErrorException(41, "결제 승인 도중 서버 에러가 발생했습니다.");
        }

        log.info("결제 승인 성공 - paymentId: {}, userId: {}", paymentId, userId);

        // 알림
        noticeService.send(payment.getPost().getClient(),
                C_PAYMENT_DONE.of(payment.getPost().getTitle(), String.valueOf(payment.getAmount())),
                U_PAYMENT_DONE.of(payment.getPost().getId()));

        return payment;
    }

    public List<Payment> findAll(Long userId) {
        return paymentRepository.findAllByUserId(userId);
    }

    public Payment findById(Long userId, Long paymentId) {
        return paymentPersistenceService.findById(userId, paymentId);
    }

    public Payment cancel(Payment payment, PaymentCancelRequest request, int cancelAmount, boolean isPartialCancel, boolean createNewPayment) {
        if (payment.getPaymentStatus().equals(PaymentStatus.CANCELED)) {
            throw new InvalidException(44, "이미 취소된 결제입니다.");
        }

        if (!(payment.getPaymentStatus().equals(PaymentStatus.DONE) ||
                payment.getPaymentStatus().equals(PaymentStatus.PARTIAL_CANCELED))) {
            throw new InvalidException(45, "결제 완료 상태가 아닙니다.");
        }

        String tossPaymentKey = payment.getPaymentKey();
        String cancelReason = request.cancelReason();

        // 외부 API 요청
        ResponseEntity<TossConfirmResponse> response =
                tossPaymentClient.callApiCancel(cancelReason, tossPaymentKey, String.valueOf(cancelAmount));

        // DB 반영
        try {
            if (payment.getBalanceAmount() > cancelAmount || isPartialCancel) {
                paymentPersistenceService.paymentPartialCancelDb(payment.getId(), cancelReason, cancelAmount);
            } else {
                paymentPersistenceService.paymentCancelDb(payment.getId(), cancelReason, createNewPayment);
            }
        } catch (NotFoundException e) {
            log.error("결제 취소 실패", e);
            throw e;
        } catch (RuntimeException ex) {
            log.error("결제 취소 실패", ex);
            throw new InternalServerErrorException(42, "결제 취소 도중 서버 에러가 발생했습니다.");
        }

        log.info("결제 취소 성공 - paymentId: {}, cancelAmount: {}", payment.getId(), cancelAmount);

        return payment;
    }

    @CacheEvict(cacheNames = "postList", allEntries = true)
    public Payment cancel(Long userId, Long paymentId, PaymentCancelRequest request) {
        Payment payment = paymentPersistenceService.findById(userId, paymentId);

        // 매칭되었거나 동행 완료인 경우 결제 취소 불가
        Post post = payment.getPost();
        if (!(post.getPostStatus().equals(PostStatus.OPEN) ||
                post.getPostStatus().equals(PostStatus.EXPIRED))) {
            throw new InvalidException(46, "이미 종료된 동행이거나 매칭되었기 때문에 결제를 취소할 수 없습니다.");
        }

        int amount = payment.getAmount();
        Payment canceledPayment = this.cancel(payment, request, amount, false, true);

        // 지원한 사용자 모두 거절
        paymentPersistenceService.rejectApplication(post);

        // 알림
        noticeService.send(post.getClient(),
                C_PAYMENT_CANCELED.of(post.getTitle(), String.valueOf(payment.getAmount())),
                U_PAYMENT_CANCELED.of(post.getId()));

        return canceledPayment;
    }

    // 공고 기반 결제 취소
    @CacheEvict(cacheNames = "postList", allEntries = true)
    public void cancel(Post post) {
        List<Payment> payments = paymentPersistenceService.findByPostId(post.getId());

        for (Payment payment : payments) {
            cancel(
                    payment,
                    new PaymentCancelRequest("노쇼로 인해 동행 취소, 공고 모집 기한이 만료되어 취소되었습니다."),
                    payment.getBalanceAmount(),
                    false, false
            );
        }
    }

    public void validPayment(Post post, Application application, LocalDate settledDate) {
        int payoutAmount = validPayment(post);
        // 정산 데이터 생성
        settlementService.createSettlement(payoutAmount, application, application.getEscort(), settledDate);
    }

    public int validPayment(Post post) {
        // 결제 완료를 모두 가져옴
        List<Payment> payments = paymentPersistenceService.findByPostId(post.getId());

        // 결제 완료된 결제가 없는 경우
        if (payments.isEmpty()) {
            paymentPersistenceService.updateAmountForModifyPost(post.getId(), post);
            return post.getTotalPay().intValue();
        }

        int balanceAmount = 0;
        for (Payment payment : payments) {
            balanceAmount += payment.getAmount();
        }

        balanceAmount -= post.getTotalPay().intValue();
        int payoutAmount = post.getTotalPay().intValue();

        // 추가 결제 플로우
        if (balanceAmount < 0) {
            // 결제 데이터 생성
            Payment newPayment = Payment.builder()
                    .post(post)
                    .hourlyPaySnapshot(post.getHourlyPay())
                    .hours(post.getEscortHours())
                    .amount(Math.abs(balanceAmount))
                    .build();
            paymentPersistenceService.createPayment(newPayment);

            // 알림
            noticeService.send(post.getClient(),
                    C_PAYMENT_EXTRA_PAY.of(post.getTitle(), String.valueOf(newPayment.getAmount())),
                    U_PAYMENT_EXTRA_PAY.of(post.getId()));
        }

        // 부분 취소 플로우
        else if (balanceAmount > 0) {
            // 취소 로직 결제 데이터 생성 없애기
            cancel(
                    payments.getFirst(),
                    new PaymentCancelRequest("결제 금액: %s, 이용 금액: %s".formatted(balanceAmount + payoutAmount, payoutAmount)),
                    balanceAmount, true, false
            );

            // 알림
            noticeService.send(post.getClient(),
                    C_PAYMENT_PARTIAL_CANCELED.of(post.getTitle(), String.valueOf(balanceAmount)),
                    U_PAYMENT_PARTIAL_CANCELED.of(post.getId()));
        }

        return payoutAmount;
    }

    public Payment createPayment(Post post) {
        Payment payment = Payment.builder()
                .post(post)
                .hourlyPaySnapshot(post.getHourlyPay())
                .hours(post.getEscortHours())
                .amount(post.getTotalPay().intValue())
                .build();
        return paymentPersistenceService.createPayment(payment);
    }

    public Payment findByPostIdAndReady(Long postId, Long userId) {
        return paymentRepository.findByPostIdAndUserIdAndReady(postId, userId).orElse(null);
    }

    public void updateAmount(Long paymentId, String amount) {
        paymentPersistenceService.updateAmount(paymentId, amount);
    }
    // 결제 - 공고 삭제 상태 변경

    @CacheEvict(cacheNames = "postList", allEntries = true)
    public Payment cancelPostAndPayment(Long postId) {
        return paymentPersistenceService.updateDeleteStatus(postId);
    }

    public int[] cancelPostAndPaymentCallApi() {
        List<Payment> payments = paymentPersistenceService.findDeletedAll();
        int succeedCount = 0;
        int failedCount = 0;

        for (Payment payment : payments) {

            if (payment.getPaymentKey() == null) {
                paymentPersistenceService.paymentCancelDb(payment.getId(), "미결제 공고 삭제", false);
                continue;
            }

            String tossPaymentKey = payment.getPaymentKey();
            String cancelReason = "공고 삭제로 인한 결제 취소";

            // 외부 API 요청
            try {
                ResponseEntity<TossConfirmResponse> response =
                        tossPaymentClient.callApiCancel(cancelReason, tossPaymentKey, String.valueOf(payment.getBalanceAmount()));
            } catch (RuntimeException e) {
                failedCount++;
                continue;
            }

            // DB 반영
            try {
                paymentPersistenceService.paymentCancelDb(payment.getId(), cancelReason, false);
            } catch (NotFoundException e) {
                log.error("결제 취소 - DB 저장 실패", e);
                failedCount++;
                continue;
            } catch (RuntimeException ex) {
                log.error("결제 취소 - DB 저장 실패", ex);
                failedCount++;
                continue;
            }

            succeedCount++;
            log.info("결제 취소 성공 - paymentId: {}, cancelAmount: {}", payment.getId(), payment.getBalanceAmount());
        }

        return new int[]{succeedCount + failedCount, succeedCount, failedCount};
    }

    public boolean validNotPaid(Long userId) {
        List<Payment> payments = paymentPersistenceService.getNotPaid(userId);

        return payments.isEmpty();
    }

    public List<PaymentDailyAnalyticsDto> getDailyPaymentAnalytics(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        if (startDate != null) {
            startDateTime = startDate.atStartOfDay();
        } else {
            startDateTime = LocalDate.now().minusDays(7).atStartOfDay();
        }

        if (endDate != null) {
            endDateTime = endDate.plusDays(1).atStartOfDay();
        } else {
            endDateTime = LocalDate.now().plusDays(1).atStartOfDay();
        }

        return paymentPersistenceService.getDailyAnalytics(startDateTime, endDateTime);
    }

    public List<Payment> getPaymentAnalyticsByPeriod(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        if (startDate != null) {
            startDateTime = startDate.atStartOfDay();
        } else {
            startDateTime = LocalDate.now().minusDays(7).atStartOfDay();
        }

        if (endDate != null) {
            endDateTime = endDate.atStartOfDay();
        } else {
            endDateTime = LocalDate.now().plusDays(1).atStartOfDay();
        }

        return paymentPersistenceService.getAnalyticsByPeriod(startDateTime, endDateTime);
    }

    public Page<Payment> getPaymentsForAdmin(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        if (startDate != null) {
            startDateTime = startDate.atStartOfDay();
        } else {
            startDateTime = LocalDate.now().minusDays(7).atStartOfDay();
        }

        if (endDate != null) {
            endDateTime = endDate.atStartOfDay();
        } else {
            endDateTime = LocalDate.now().plusDays(1).atStartOfDay();
        }

        return paymentPersistenceService.getPaymentsForAdmin(startDateTime, endDateTime, pageable);
    }

    @CacheEvict(cacheNames = "postList", allEntries = true)
    public void updateStatusForAdmin(Long paymentId, PaymentStatus status) {
        paymentPersistenceService.updateStatusForAdmin(paymentId, status);
    }
    // 동행 완료 후 추가 결제를 하지 않은 공고가 있는 지 확인

    public boolean hasUnpaidExtraPayment(Long userId) {
        return this.paymentRepository.existsUnpaidExtraByUserId(userId);
    }
}
