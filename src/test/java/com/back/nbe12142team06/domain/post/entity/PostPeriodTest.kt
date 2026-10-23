package com.back.nbe12142team06.domain.post.entity

import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class PostPeriodTest {

    private val start = LocalDateTime.of(2026, 10, 21, 9, 0)

    private val end = LocalDateTime.of(2026, 10, 21, 18, 0)

    // 모집 기간만 확인하는 테스트라 나머지 값은 아무 값이나 채운다 (코틀린 생성자는 필수 값을 모두 받는다)
    private val post = Post(
        client = User(
            "periodClient", "pw", "periodClient@test.com", "의뢰인",
            Role.CLIENT, Gender.MALE, LocalDate.of(1990, 1, 1), "010-0000-0000", "서울",
        ),
        title = "제목",
        content = "내용",
        region = "서울",
        hospitalName = "병원",
        hospitalAddress = "주소",
        hospitalLat = BigDecimal.ZERO,
        hospitalLng = BigDecimal.ZERO,
        pickupAddress = "픽업",
        pickupLat = BigDecimal.ZERO,
        pickupLng = BigDecimal.ZERO,
        hourlyPay = 15000,
        recruitStartAt = start,
        recruitEndAt = end,
        escortStartAt = end.plusDays(1),
        escortEndAt = end.plusDays(1).plusHours(3),
    )

    @Test
    @DisplayName("모집 시작 직전에는 지원할 수 없다")
    fun beforeStart() {
        assertFalse(post.isWithinPeriod(start.minusNanos(1)))
    }

    @Test
    @DisplayName("모집 시작 시각에는 지원할 수 있다")
    fun atStart() {
        assertTrue(post.isWithinPeriod(start))
    }

    @Test
    @DisplayName("모집 기간 중에는 지원할 수 있다")
    fun duringPeriod() {
        assertTrue(post.isWithinPeriod(start.plusHours(1)))
    }

    @Test
    @DisplayName("모집 마감 시각에는 지원할 수 있다")
    fun atEnd() {
        assertTrue(post.isWithinPeriod(end))
    }

    @Test
    @DisplayName("모집 마감 직후에는 지원할 수 없다")
    fun afterEnd() {
        assertFalse(post.isWithinPeriod(end.plusNanos(1)))
    }
}
