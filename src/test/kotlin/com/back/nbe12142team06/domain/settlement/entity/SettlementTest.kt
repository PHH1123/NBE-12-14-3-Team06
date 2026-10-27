package com.back.nbe12142team06.domain.settlement.entity

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.global.exception.InvalidException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Settlement 엔티티의 상태 변경 테스트.
 * 전이 규칙 자체는 SettlementStatusTransitionTest 에서 전수 검증한다.
 */
class SettlementTest {

    private fun createEscort(): User = User(
        "escort01", "password1!", "escort01@email.com", "동행매니저",
        Role.ESCORT, Gender.MALE, LocalDate.of(2000, 1, 1), "010-5678-5678", "서울"
    )

    private fun createSettlement(status: SettlementStatus, id: Long? = 1L): Settlement {
        val escort = createEscort()
        return Settlement(
            id = id,
            payoutAmount = BigDecimal.valueOf(135_000),
            platformFee = BigDecimal.valueOf(15_000),
            penaltyAmount = BigDecimal.ZERO,
            settlementStatus = status,
            settledDate = LocalDate.now().plusDays(1),
            application = Application(Post(), escort),
            escort = escort
        )
    }

    @Test
    @DisplayName("[Settlement] 정산 중이던 건은 정산 완료로 바뀐다")
    fun settlementCompleted() {
        val settlement = createSettlement(SettlementStatus.PROCESSING)

        settlement.settlementCompleted()

        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.COMPLETED)
    }

    @Test
    @DisplayName("[Settlement] 정산 전 상태에서 바로 정산 완료로 바꿀 수 없다")
    fun settlementCompletedFromPending() {
        val settlement = createSettlement(SettlementStatus.PENDING)

        assertThatThrownBy { settlement.settlementCompleted() }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("'정산 전' 상태의 정산은 '정산 완료' 상태로 바꿀 수 없습니다.")

        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.PENDING)
    }

    @Test
    @DisplayName("[Settlement] 정산 중이던 건은 정산 실패로 바뀐다")
    fun settlementFailed() {
        val settlement = createSettlement(SettlementStatus.PROCESSING)

        settlement.settlementFailed()

        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.FAILED)
    }

    @Test
    @DisplayName("[Settlement] 정산 완료된 건은 실패로 되돌릴 수 없다")
    fun settlementFailedFromCompleted() {
        val settlement = createSettlement(SettlementStatus.COMPLETED)

        assertThatThrownBy { settlement.settlementFailed() }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("'정산 완료' 상태의 정산은 '정산 실패' 상태로 바꿀 수 없습니다.")
    }

    @Test
    @DisplayName("[Settlement] 정산 예정일을 변경한다")
    fun settlementDateUpdate() {
        val settlement = createSettlement(SettlementStatus.PENDING)
        val newDate = LocalDate.now().plusDays(7)

        settlement.settlementDateUpdate(newDate)

        assertThat(settlement.settledDate).isEqualTo(newDate)
    }

    @Test
    @DisplayName("[Settlement] 저장되지 않아 id가 없으면 requiredId 는 예외를 던진다")
    fun requiredIdWithoutId() {
        val settlement = createSettlement(SettlementStatus.PENDING, id = null)

        assertThatThrownBy { settlement.requiredId }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("ID가 NULL입니다.")
    }
}
