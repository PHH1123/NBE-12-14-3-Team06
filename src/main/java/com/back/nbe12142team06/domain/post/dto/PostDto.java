package com.back.nbe12142team06.domain.post.dto;

import com.back.nbe12142team06.domain.post.entity.Post;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PostDto(
        Long id,
        Long clientUserId,
        String client_id,
        boolean isOwner,
        String title,
        String content,
        String patientNote,
        String region,
        String hospitalName,
        String hospitalAddress,
        BigDecimal hospitalLat,
        BigDecimal hospitalLng,
        String pickupAddress,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        int hourlyPay,
        LocalDateTime recruitStartAt,
        LocalDateTime recruitEndAt,
        LocalDateTime escortStartAt,
        LocalDateTime escortEndAt,
        BigDecimal escortHours,
        BigDecimal totalPay,
        String postStatus,
        boolean reportRequired,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean canViewSensitive //민감정보 권한
) {
    public PostDto(Post post) {
        this(post, true, false);
    }
    public PostDto(Post post, boolean canViewSensitive) {
        this(post, canViewSensitive, false);
    }
    public PostDto(Post post, boolean canViewSensitive, boolean isOwner) {
        this(
                post.getId(),
                post.getClient().getId(),
                maskName(post.getClient().getName()),
                isOwner,
                post.getTitle(),
                post.getContent(),
                canViewSensitive ? post.getPatientNote() : null,
                post.getRegion(),
                post.getHospitalName(),
                post.getHospitalAddress(),
                post.getHospitalLat(),
                post.getHospitalLng(),
                post.getPickupAddress(),
                post.getPickupLat(),
                post.getPickupLng(),
                post.getHourlyPay(),
                post.getRecruitStartAt(),
                post.getRecruitEndAt(),
                post.getEscortStartAt(),
                post.getEscortEndAt(),
                post.getEscortHours(),
                post.getTotalPay(),
                post.getPostStatus().getDescription(),
                post.isReportRequired(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                canViewSensitive
        );
    }


    // 이름은 화면에서만 마스킹한다. 원본 이름과 로그인 아이디는 변경하지 않는다.
    private static String maskName(String name) {
        if (name == null || name.isBlank()) return "***";
        int[] chars = name.codePoints().toArray();
        if (chars.length < 2) return "*"; // 예상하지 못한 데이터에 대한 방어 처리
        String first = new String(chars, 0, 1);
        if (chars.length == 2) return first + "*";
        String last = new String(chars, chars.length - 1, 1);
        return first + "*".repeat(chars.length - 2) + last;
    }

}