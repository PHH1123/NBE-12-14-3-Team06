package com.back.nbe12142team06.domain.settlement.dto

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 정산 응답 DTO 의 매핑·집계 테스트. 엔티티만 있으면 되므로 DB 없이 돌린다.
 */
class SettlementDtoTest {

    private fun createUser(username: String, role: Role, id: Long): User {
        val user = User(
            username, "password1!", "$username@email.com",
            if (role == Role.ESCORT) "동행매니저" else "의뢰인",
            role, Gender.MALE, LocalDate.of(1990, 1, 1), "010-1234-1234", "서울"
        )
        val idField = User::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(user, id)
        return user
    }

    private fun createPost(client: User): Post {
        return Post(
            id = 3L,
            title = "정형외과 동행 구합니다",
            content = "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.",
            region = "서울",
            hospitalName = "서울성모병원",
            hospitalAddress = "서울 서초구 반포대로 222",
            hospitalLat = BigDecimal.valueOf(37.5012743),
            hospitalLng = BigDecimal.valueOf(127.0051893),
            pickupAddress = "서울 서초구 잠원동 10-1",
            pickupLat = BigDecimal.valueOf(37.5160000),
            pickupLng = BigDecimal.valueOf(127.0200000),
            hourlyPay = 15_000,
            recruitStartAt = LocalDateTime.now().plusDays(1),
            recruitEndAt = LocalDateTime.now().plusDays(6),
            escortStartAt = LocalDateTime.now().plusDays(7),
            escortEndAt = LocalDateTime.now().plusDays(7).plusHours(10),
            client = client,
        )
    }

    private fun createSettlement(
        status: SettlementStatus,
        payoutAmount: BigDecimal = BigDecimal.valueOf(135_000),
        platformFee: BigDecimal = BigDecimal.valueOf(15_000),
        penaltyAmount: BigDecimal = BigDecimal.ZERO
    ): Settlement {
        val escort = createUser("escort01", Role.ESCORT, 7L)
        val client = createUser("client01", Role.CLIENT, 8L)
        val application = Application(id = 5L, post = createPost(client), escort = escort)

        return Settlement(
            id = 11L,
            payoutAmount = payoutAmount,
            platformFee = platformFee,
            penaltyAmount = penaltyAmount,
            settlementStatus = status,
            settledDate = LocalDate.now().plusDays(1),
            application = application,
            escort = escort
        )
    }

    @Test
    @DisplayName("[SettlementAnalyticsResponse] 상태별로 정산·미정산·오류 금액을 나눠 집계한다")
    fun analyticsResponse() {
        val settlements = listOf(
            // 정산 완료 - 수수료 15,000 / 기부 0
            createSettlement(SettlementStatus.COMPLETED),
            // 정산 완료 - 수수료 15,000 / 기부 13,500
            createSettlement(
                SettlementStatus.COMPLETED,
                payoutAmount = BigDecimal.valueOf(121_500),
                penaltyAmount = BigDecimal.valueOf(13_500)
            ),
            // 정산 전 - 미정산
            createSettlement(SettlementStatus.PENDING),
            // 정산 실패 - 미정산
            createSettlement(SettlementStatus.FAILED, payoutAmount = BigDecimal.valueOf(90_000)),
            // 정산 중 - 오류로 집계
            createSettlement(SettlementStatus.PROCESSING, payoutAmount = BigDecimal.valueOf(45_000))
        )

        val result = SettlementAnalyticsResponse.from(settlements)

        assertThat(result.totalSettlementAmount).isEqualTo(526_500L)
        assertThat(result.settlementCount).isEqualTo(5L)
        assertThat(result.totalSettledAmount).isEqualTo(256_500L)
        assertThat(result.settledCount).isEqualTo(2L)
        // 미정산 = 전체 - 완료 - 진행중 (정산 전 135,000 + 실패 90,000)
        assertThat(result.totalUnSettledAmount).isEqualTo(225_000L)
        assertThat(result.unSettledCount).isEqualTo(2L)
        // 수수료·기부는 정산 완료 건만 센다
        assertThat(result.totalPlatformFee).isEqualTo(30_000L)
        assertThat(result.platformFeeCount).isEqualTo(2L)
        assertThat(result.totalDonationAmount).isEqualTo(13_500L)
        assertThat(result.donationCount).isEqualTo(1L)
        assertThat(result.totalErrorSettlementAmount).isEqualTo(45_000L)
        assertThat(result.errorSettlementCount).isEqualTo(1L)
    }

    @Test
    @DisplayName("[SettlementAnalyticsResponse] 정산 데이터가 없으면 모두 0")
    fun analyticsResponseEmpty() {
        val result = SettlementAnalyticsResponse.from(emptyList())

        assertThat(result.totalSettlementAmount).isEqualTo(0L)
        assertThat(result.settlementCount).isEqualTo(0L)
        assertThat(result.totalSettledAmount).isEqualTo(0L)
        assertThat(result.totalUnSettledAmount).isEqualTo(0L)
        assertThat(result.totalPlatformFee).isEqualTo(0L)
        assertThat(result.totalDonationAmount).isEqualTo(0L)
        assertThat(result.donationCount).isEqualTo(0L)
        assertThat(result.totalErrorSettlementAmount).isEqualTo(0L)
    }

    @Test
    @DisplayName("[SettlementResponse] 엔티티를 사용자 응답으로 매핑 - 공고 정보 포함")
    fun settlementResponse() {
        val settlement = createSettlement(SettlementStatus.COMPLETED)

        val result = SettlementResponse(settlement)

        assertThat(result.id).isEqualTo(11L)
        assertThat(result.payoutAmount).isEqualTo(135_000)
        assertThat(result.platformFee).isEqualTo(15_000)
        assertThat(result.penaltyAmount).isEqualTo(0)
        assertThat(result.status).isEqualTo(SettlementStatus.COMPLETED)
        assertThat(result.settledAt).isEqualTo(LocalDate.now().plusDays(1))
        assertThat(result.post.id).isEqualTo(3L)
        assertThat(result.post.title).isEqualTo("정형외과 동행 구합니다")
        assertThat(result.post.totalPay).isEqualByComparingTo(BigDecimal.valueOf(150_000))
    }

    @Test
    @DisplayName("[SettlementAdminResponse] 엔티티를 관리자 응답으로 매핑 - 동행인 정보 포함")
    fun settlementAdminResponse() {
        val settlement = createSettlement(
            SettlementStatus.PROCESSING,
            payoutAmount = BigDecimal.valueOf(121_500),
            penaltyAmount = BigDecimal.valueOf(13_500)
        )

        val result = SettlementAdminResponse(settlement)

        assertThat(result.id).isEqualTo(11L)
        assertThat(result.payoutAmount).isEqualTo(121_500)
        assertThat(result.platformFee).isEqualTo(15_000)
        assertThat(result.penaltyAmount).isEqualTo(13_500)
        assertThat(result.status).isEqualTo(SettlementStatus.PROCESSING)
        assertThat(result.settledAt).isEqualTo(LocalDate.now().plusDays(1))
        assertThat(result.applicationId).isEqualTo(5L)
        assertThat(result.userId).isEqualTo(7L)
        assertThat(result.name).isEqualTo("동행매니저")
    }
}
