package com.back.nbe12142team06.domain.notification.entity

enum class NotificationUrl(
    val format: String
) {
    U_APPLICATION_APPLY("/client/posts/%d/applicants"),
    U_APPLICATION_ACCEPT("/escort/%d"),
    U_APPLICATION_REJECT("/mypage/applications"),
    U_APPLICATION_CANCELED("/client/posts/%d"),
    U_APPLICATION_UPDATED_PROGRESS_CLIENT("/client/escort/%d?postId=%d"),
    U_APPLICATION_UPDATED_PROGRESS_ESCORT("/escort/%d"),
    U_APPLICATION_PROGRESS_AT_HOSPITAL("/escort/%d/report/new"),
    U_APPLICATION_PROGRESS_ARRIVED_HOME("/client/escort/%d/review?postId=%d"),
    U_REPORT_WRITE("/escort/%d/report/new"),
    U_REVIEW_WRITE("/mypage/reviews"),
    U_PAYMENT_DONE("/client/posts/%d"),
    U_PAYMENT_CANCELED("/client/posts/%d"),
    U_PAYMENT_PARTIAL_CANCELED("/client/posts/%d"),
    U_PAYMENT_EXTRA_PAY("/client/posts/%d"),
    U_SETTLEMENT_DONE("/escort/%d"),
    U_ESCORT_DELETE_APPLICATION_CANCEL("/client/posts/%d");

    fun of(vararg args: Any): String {
        return format.format(*args)
    }
}
