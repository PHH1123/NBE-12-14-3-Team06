package com.back.nbe12142team06.domain.review.entity

enum class ReviewTag(
    val description: String, // 화면에 표시할 문구
    val positive: Boolean // 긍정/부정 구분
) {
    // 긍정
    KIND("친절해요", true),
    PUNCTUAL("시간을 잘 지켜요", true),
    DETAILED_REPORT("보고서가 꼼꼼해요", true),
    GOOD_COMMUNICATION("소통이 잘 돼요", true),
    CAREFUL("어르신을 세심하게 챙겨요", true),

    // 부정
    LATE("시간 약속이 아쉬워요", false),
    POOR_COMMUNICATION("소통이 잘 안 됐어요", false),
    UNKIND("응대가 아쉬웠어요", false),
    INSUFFICIENT_REPORT("보고서 내용이 부족해요", false);
}