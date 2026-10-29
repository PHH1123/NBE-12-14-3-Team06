package com.back.nbe12142team06.domain.application.dto

import java.math.BigDecimal
import java.time.Instant

/** 한 사람의 최근 위치 1건. */
data class LocationPoint(
    val lat: BigDecimal?,
    val lng: BigDecimal?,
    val accuracy: BigDecimal?,
    val updatedAt: Instant?,
)

/**
 * 동행 중 의뢰인과 동행 매니저의 최근 위치.
 * 아직 위치를 보내지 않은 쪽은 null 이다.
 */
data class ApplicationLocationResponse(
    val escort: LocationPoint?,
    val client: LocationPoint?,
    // 두 위치가 모두 최근(stale-seconds 이내)이고 반경(radius-meters) 안에 있으면 true. 출발 버튼 활성화 기준이다.
    val nearby: Boolean,
    // 두 사람 사이의 거리(미터). 한쪽이라도 없거나 오래됐으면 null.
    val distanceMeters: Int?,
)
