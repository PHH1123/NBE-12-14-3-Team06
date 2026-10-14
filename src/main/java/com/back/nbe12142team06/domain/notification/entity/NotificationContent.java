package com.back.nbe12142team06.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationContent {
    C_APPLICATION_APPLY("%s님이 %s 공고에 지원하셨습니다."),
    C_APPLICATION_ACCEPT("%s 공고에 매칭되었습니다."),
    C_APPLICATION_REJECT("%s 공고에 거절되었습니다."),
    C_APPLICATION_CANCELED("%s님이 %s 공고 지원을 취소하셨습니다."),
    C_APPLICATION_UPDATED_PROGRESS("현재 동행 상태는 %s입니다."),
    C_APPLICATION_PROGRESS_AT_HOSPITAL("%s에 도착하셨습니다. 진료 후 보고서를 작성해주세요."),
    C_APPLICATION_PROGRESS_ARRIVED_HOME("%s 동행이 완료되었습니다. 리뷰를 작성해주세요."),
    C_REPORT_WRITE("%s 보고서가 작성되었습니다."),
    C_REVIEW_WRITE("%s 동행 리뷰가 작성되었습니다."),
    C_PAYMENT_DONE("%s 공고의 결제가 완료되었습니다. 결제 금액: %s"),
    C_PAYMENT_CANCELED("%s 공고의 결제가 취소되었습니다. 결제 금액이 들어오기까지는 1~2일 걸릴 수 있습니다. 취소 금액: %s"),
    C_PAYMENT_PARTIAL_CANCELED("%s 동행의 결제가 부분 환불되었습니다. 환불 금액: %s"),
    C_PAYMENT_EXTRA_PAY("%s 동행의 추가 결제요청드립니다. 추가 결제 금액: %s"),
    C_SETTLEMENT_DONE("정산이 완료되었습니다. 정산 금액: %s");

    private final String format;

    public String of(Object... args) {
        return String.format(format, args);
    }
}
