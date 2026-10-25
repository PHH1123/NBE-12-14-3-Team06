package com.back.nbe12142team06.domain.settlement.service

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.domain.penalty.entity.NoShowPenalty
import com.back.nbe12142team06.domain.penalty.service.NoShowPenaltyService
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.settlement.client.SettlementClient
import com.back.nbe12142team06.domain.settlement.client.SettlementClientResponse
import com.back.nbe12142team06.domain.settlement.dto.AccountDto
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.global.exception.InternalServerErrorException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import jakarta.persistence.PersistenceException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * SettlementService 의 검증·분기·금액 계산 테스트.
 * DB 와 Spring 컨텍스트 없이 경계(리포지토리·금융결제원 API·알림·패널티)만 대역으로 바꿔 돌린다.
 * 커밋 경계가 걸린 흐름은 SettlementNotTransactionServiceTest 에서 다룬다.
 */
class SettlementServiceUnitTest {

    private val settlementRepository: SettlementRepository = mock()
    private val noShowPenaltyService: NoShowPenaltyService = mock()
    private val noticeService: NoticeService = mock()
    private val settlementClient: SettlementClient = mock()

    private val settlementPersistenceService = spy(SettlementPersistenceService(settlementRepository))

    private val settlementService = SettlementService(
        settlementPersistenceService, settlementRepository, noShowPenaltyService, noticeService, settlementClient
    )

    private val settlementId = 11L
    private val escortId = 7L

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
        return Post.builder()
            .id(3L)
            .title("정형외과 동행 구합니다")
            .content("무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.")
            .region("서울")
            .hospitalName("서울성모병원")
            .hospitalAddress("서울 서초구 반포대로 222")
            .hospitalLat(BigDecimal.valueOf(37.5012743))
            .hospitalLng(BigDecimal.valueOf(127.0051893))
            .pickupAddress("서울 서초구 잠원동 10-1")
            .pickupLat(BigDecimal.valueOf(37.5160000))
            .pickupLng(BigDecimal.valueOf(127.0200000))
            .hourlyPay(15_000)
            .recruitStartAt(LocalDateTime.now().plusDays(1))
            .recruitEndAt(LocalDateTime.now().plusDays(6))
            .escortStartAt(LocalDateTime.now().plusDays(7))
            .escortEndAt(LocalDateTime.now().plusDays(7).plusHours(10))
            .client(client)
            .build()
    }

    private fun createApplication(escort: User): Application =
        Application.builder().id(5L).post(createPost(createUser("client01", Role.CLIENT, 8L))).escort(escort).build()

    private fun createSettlement(status: SettlementStatus, escort: User = createUser("escort01", Role.ESCORT, escortId)): Settlement {
        return Settlement(
            id = settlementId,
            payoutAmount = BigDecimal.valueOf(135_000),
            platformFee = BigDecimal.valueOf(15_000),
            penaltyAmount = BigDecimal.ZERO,
            settlementStatus = status,
            settledDate = LocalDate.now(),
            application = createApplication(escort),
            escort = escort
        )
    }

    private fun createAccountDto(id: Long = settlementId) =
        AccountDto(id, "123-000000-123", "동행매니저", BigDecimal.valueOf(135_000))

    private fun clientResponse(resCnt: Int) = SettlementClientResponse(
        api_tran_id = "2ffd133a-d17a-431d-a6a5",
        api_tran_dtm = "20190910101921567",
        rsp_code = "A0000",
        rsp_message = "",
        wd_bank_code_std = "097",
        wd_bank_code_sub = "1230001",
        wd_bank_name = "오픈은행",
        wd_account_num_masked = "000-1230000-***",
        wd_print_content = "정산 완료",
        wd_account_holder_name = "병원 동행 서비스 가지",
        res_cnt = resCnt,
        res_list = emptyList()
    )

    // 정산 요청이 외부 API 까지 도달하도록 공통 스텁
    private fun stubRequestFlow(settlement: Settlement) {
        doReturn(createAccountDto()).whenever(settlementRepository).findByIdAndState(any(), any())
        doReturn(1).whenever(settlementRepository).updateProcessing(any())
        doReturn(settlement).whenever(settlementRepository).fetchJoinById(any())
    }

    @Test
    @DisplayName("[SettlementService] 정산 생성 시 수수료 10%를 떼고 지급액을 계산한다")
    fun createSettlementAmount() {
        val escort = createUser("escort01", Role.ESCORT, escortId)
        doAnswer { it.arguments[0] as Settlement }.whenever(settlementRepository).save(any())
        doReturn(null).whenever(noShowPenaltyService).getNoShowPenalty(any())

        val settlement = settlementService.createSettlement(
            BigDecimal.valueOf(150_000), createApplication(escort), escort, LocalDate.now().plusDays(1)
        )

        assertThat(settlement.payoutAmount).isEqualByComparingTo(BigDecimal.valueOf(135_000))
        assertThat(settlement.platformFee).isEqualByComparingTo(BigDecimal.valueOf(15_000))
        // 지급액은 원 단위로 확정된다 (DB 의 암묵적 반올림에 맡기지 않는다)
        assertThat(settlement.payoutAmount.scale()).isEqualTo(0)
        // 지급액 + 수수료는 결제 금액과 정확히 같아야 한다
        assertThat(settlement.payoutAmount.add(settlement.platformFee))
            .isEqualByComparingTo(BigDecimal.valueOf(150_000))
        assertThat(settlement.penaltyAmount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.PENDING)
        assertThat(settlement.settledDate).isEqualTo(LocalDate.now().plusDays(1))
    }

    @Test
    @DisplayName("[SettlementService] 정산 생성 - 원 단위로 나뉘지 않는 금액은 지급액을 버림한다")
    fun createSettlementAmountWithRemainder() {
        val escort = createUser("escort01", Role.ESCORT, escortId)
        doAnswer { it.arguments[0] as Settlement }.whenever(settlementRepository).save(any())
        doReturn(null).whenever(noShowPenaltyService).getNoShowPenalty(any())

        // 11,352원 × 1.1시간 = 12,487.2원 → 90%는 11,238.48원
        val settlement = settlementService.createSettlement(
            BigDecimal("12487.2"), createApplication(escort), escort, LocalDate.now()
        )

        // 지급액·수수료 모두 원 단위로 버림한다 (11,238.48 -> 11,238 / 1,249.2 -> 1,249)
        assertThat(settlement.payoutAmount).isEqualByComparingTo(BigDecimal.valueOf(11_238))
        assertThat(settlement.platformFee).isEqualByComparingTo(BigDecimal.valueOf(1_249))
        assertThat(settlement.payoutAmount.scale()).isEqualTo(0)
        assertThat(settlement.platformFee.scale()).isEqualTo(0)
        // 합은 결제 금액의 원 단위 금액과 같다 (원 미만 0.2원은 버려진다)
        assertThat(settlement.payoutAmount.add(settlement.platformFee))
            .isEqualByComparingTo(BigDecimal.valueOf(12_487))
    }

    @Test
    @DisplayName("[SettlementService] 노쇼 패널티가 있으면 지급액에서 10%를 더 차감하고 패널티를 적용한다")
    fun createSettlementWithPenalty() {
        val escort = createUser("escort01", Role.ESCORT, escortId)
        val application = createApplication(escort)
        val penalty: NoShowPenalty = mock()

        doAnswer { it.arguments[0] as Settlement }.whenever(settlementRepository).save(any())
        doReturn(penalty).whenever(noShowPenaltyService).getNoShowPenalty(escortId)

        val settlement = settlementService.createSettlement(
            BigDecimal.valueOf(150_000), application, escort, LocalDate.now()
        )

        // 135,000 에서 패널티 13,500 을 뺀 121,500
        assertThat(settlement.payoutAmount).isEqualByComparingTo(BigDecimal.valueOf(121_500))
        assertThat(settlement.penaltyAmount).isEqualByComparingTo(BigDecimal.valueOf(13_500))

        // 패널티 기준 금액은 차감 전 지급액(135,000)
        verify(noShowPenaltyService, times(1)).applyPenalty(
            eq(escortId),
            argThat<BigDecimal> { compareTo(BigDecimal.valueOf(13_500)) == 0 },
            argThat<BigDecimal> { compareTo(BigDecimal.valueOf(135_000)) == 0 },
            eq(settlement),
            eq(penalty)
        )
    }

    @Test
    @DisplayName("[SettlementService] 정산 요청 성공 시 동행인에게 알림을 보낸다")
    fun requestSendsNotice() {
        val settlement = createSettlement(SettlementStatus.PROCESSING)
        stubRequestFlow(settlement)
        doReturn(clientResponse(1)).whenever(settlementClient).settlementRequest(any())

        settlementService.request(escortId, settlementId)

        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.COMPLETED)
        verify(noticeService, times(1)).send(eq(escortId), any(), anyOrNull())
    }

    @Test
    @DisplayName("[SettlementService] 정산 요청 - 이미 정산 중이면 외부 API 를 호출하지 않는다")
    fun requestRejectedWhenAlreadyProcessing() {
        doReturn(createAccountDto()).whenever(settlementRepository).findByIdAndState(any(), any())
        doReturn(0).whenever(settlementRepository).updateProcessing(any())

        assertThatThrownBy { settlementService.request(escortId, settlementId) }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("이미 정산 중이거나 정산이 완료되었습니다.")

        verify(settlementClient, never()).settlementRequest(any())
    }

    @Test
    @DisplayName("[SettlementService] 정산 요청 - 정산 데이터가 없으면 조회 단계에서 실패한다")
    fun requestWithoutSettlement() {
        doReturn(null).whenever(settlementRepository).findByIdAndState(any(), any())

        assertThatThrownBy { settlementService.request(escortId, settlementId) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("정산 데이터를 찾을 수 없습니다.")

        verify(settlementRepository, never()).updateProcessing(any())
    }

    @Test
    @DisplayName("[SettlementService] 정산 요청 - 입금 건수가 0이면 실패 처리한다")
    fun requestWithoutDeposit() {
        val settlement = createSettlement(SettlementStatus.PROCESSING)
        stubRequestFlow(settlement)
        // 외부 API 는 응답했지만 입금이 한 건도 안 된 경우
        doReturn(clientResponse(0)).whenever(settlementClient).settlementRequest(any())

        assertThatThrownBy { settlementService.request(escortId, settlementId) }
            .isInstanceOf(InternalServerErrorException::class.java)
            .hasMessage("정산에 실패했습니다.")

        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.FAILED)
        verify(noticeService, never()).send(any(), any(), anyOrNull())
    }

    @Test
    @DisplayName("[SettlementService] 정산 요청 - 외부 API 예외 시 실패 처리한다")
    fun requestWithApiError() {
        val settlement = createSettlement(SettlementStatus.PROCESSING)
        stubRequestFlow(settlement)
        doThrow(InternalServerErrorException(73, "정산 외부 API 호출을 실패했습니다."))
            .whenever(settlementClient).settlementRequest(any())

        assertThatThrownBy { settlementService.request(escortId, settlementId) }
            .isInstanceOf(InternalServerErrorException::class.java)
            .hasMessage("정산에 실패했습니다.")

        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.FAILED)
    }

    @Test
    @DisplayName("[SettlementService] 정산 스케줄러 - 성공 건을 집계한다")
    fun settlementProcessSuccess() {
        val settlement = createSettlement(SettlementStatus.PROCESSING)
        doReturn(listOf(createAccountDto())).whenever(settlementRepository).findAllByStatusAndDate()
        doReturn(1).whenever(settlementRepository).updateProcessing(any())
        doReturn(settlement).whenever(settlementRepository).fetchJoinById(any())
        doReturn(clientResponse(1)).whenever(settlementClient).settlementRequest(any())

        val counts = settlementService.settlementProcess()

        assertThat(counts[0]).isEqualTo(1)
        assertThat(counts[1]).isEqualTo(1)
        assertThat(counts[2]).isEqualTo(0)
        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.COMPLETED)
    }

    @Test
    @DisplayName("[SettlementService] 정산 스케줄러 - 이미 정산 중인 건은 건너뛴다")
    fun settlementProcessSkipsProcessing() {
        doReturn(listOf(createAccountDto())).whenever(settlementRepository).findAllByStatusAndDate()
        doReturn(0).whenever(settlementRepository).updateProcessing(any())

        val counts = settlementService.settlementProcess()

        // 건너뛴 건은 성공·실패 어디에도 세지 않는다
        assertThat(counts[0]).isEqualTo(0)
        assertThat(counts[1]).isEqualTo(0)
        assertThat(counts[2]).isEqualTo(0)
        verify(settlementClient, never()).settlementRequest(any())
    }

    @Test
    @DisplayName("[SettlementService] 정산 스케줄러 - 외부 API 예외는 실패로 집계하고 다음 건을 계속 처리한다")
    fun settlementProcessCountsFailure() {
        val failedSettlement = createSettlement(SettlementStatus.PROCESSING)
        val succeedSettlement = createSettlement(SettlementStatus.PROCESSING)

        doReturn(listOf(createAccountDto(1L), createAccountDto(2L)))
            .whenever(settlementRepository).findAllByStatusAndDate()
        doReturn(1).whenever(settlementRepository).updateProcessing(any())
        doReturn(failedSettlement).whenever(settlementRepository).fetchJoinById(1L)
        doReturn(succeedSettlement).whenever(settlementRepository).fetchJoinById(2L)
        doThrow(InternalServerErrorException(73, "정산 외부 API 호출을 실패했습니다."))
            .whenever(settlementClient).settlementRequest(
                argThat { this is com.back.nbe12142team06.domain.settlement.client.SettlementClientRequest }
            )

        val counts = settlementService.settlementProcess()

        assertThat(counts[0]).isEqualTo(2)
        assertThat(counts[1]).isEqualTo(0)
        assertThat(counts[2]).isEqualTo(2)
        assertThat(failedSettlement.settlementStatus).isEqualTo(SettlementStatus.FAILED)
        assertThat(succeedSettlement.settlementStatus).isEqualTo(SettlementStatus.FAILED)
    }

    @Test
    @DisplayName("[SettlementService] 정산 스케줄러 - 입금 건수가 0이면 실패로 집계하고 다음 건을 계속 처리한다")
    fun settlementProcessWithoutDeposit() {
        val settlement = createSettlement(SettlementStatus.PROCESSING)
        doReturn(listOf(createAccountDto())).whenever(settlementRepository).findAllByStatusAndDate()
        doReturn(1).whenever(settlementRepository).updateProcessing(any())
        doReturn(settlement).whenever(settlementRepository).fetchJoinById(any())
        doReturn(clientResponse(0)).whenever(settlementClient).settlementRequest(any())

        val counts = settlementService.settlementProcess()

        assertThat(counts[0]).isEqualTo(1)
        assertThat(counts[1]).isEqualTo(0)
        assertThat(counts[2]).isEqualTo(1)
        // 실패로 끝내고 COMPLETED 전이를 시도하지 않는다
        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.FAILED)
    }

    @Test
    @DisplayName("[SettlementService] 정산 스케줄러 - 송금 후 DB 반영이 실패하면 실패로 집계하고 계속 처리한다")
    fun settlementProcessWithDbFailure() {
        val settlement = createSettlement(SettlementStatus.PROCESSING)
        doReturn(listOf(createAccountDto())).whenever(settlementRepository).findAllByStatusAndDate()
        doReturn(1).whenever(settlementRepository).updateProcessing(any())
        doReturn(settlement).whenever(settlementRepository).fetchJoinById(any())
        doReturn(clientResponse(1)).whenever(settlementClient).settlementRequest(any())
        doThrow(PersistenceException("스레드 고갈"))
            .whenever(settlementPersistenceService).updateSettlement(any(), eq(SettlementStatus.COMPLETED))

        val counts = settlementService.settlementProcess()

        assertThat(counts[0]).isEqualTo(1)
        assertThat(counts[1]).isEqualTo(0)
        assertThat(counts[2]).isEqualTo(1)
        // 송금은 성공했으므로 상태는 PROCESSING 으로 남는다 (수동 확인 대상)
        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.PROCESSING)
    }

    @Test
    @DisplayName("[SettlementService] 정산 목록 - 기간을 생략하면 어제부터 오늘까지 조회한다")
    fun findAllWithDefaultPeriod() {
        val pageable = PageRequest.of(0, 10)
        doReturn(PageImpl(emptyList<Long>(), pageable, 0))
            .whenever(settlementRepository).findIdsByUserAndDate(any(), any(), any(), any())

        val result = settlementService.findAll(escortId, null, null, pageable)

        assertThat(result.content).isEmpty()
        verify(settlementRepository, times(1)).findIdsByUserAndDate(
            eq(escortId), eq(LocalDate.now().minusDays(1)), eq(LocalDate.now().plusDays(1)), eq(pageable)
        )
        // 조회할 id 가 없으면 상세 조회를 하지 않는다
        verify(settlementRepository, never()).findAllByIds(any())
    }

    @Test
    @DisplayName("[SettlementService] 정산 목록 - 조회한 id 순서대로 응답을 만든다")
    fun findAllMapsResponse() {
        val pageable = PageRequest.of(0, 10)
        val settlement = createSettlement(SettlementStatus.COMPLETED)

        doReturn(PageImpl(listOf(settlementId), pageable, 1))
            .whenever(settlementRepository).findIdsByUserAndDate(any(), any(), any(), any())
        doReturn(listOf(settlement)).whenever(settlementRepository).findAllByIds(any())

        val result = settlementService.findAll(
            escortId, LocalDate.now().minusDays(7), LocalDate.now(), pageable
        )

        assertThat(result.content).hasSize(1)
        assertThat(result.content.first().id).isEqualTo(settlementId)
        assertThat(result.content.first().payoutAmount).isEqualTo(135_000)
        assertThat(result.content.first().post.id).isEqualTo(3L)
        // endDate 는 하루를 더해 조회한다
        verify(settlementRepository, times(1)).findIdsByUserAndDate(
            eq(escortId), eq(LocalDate.now().minusDays(7)), eq(LocalDate.now().plusDays(1)), eq(pageable)
        )
    }

    @Test
    @DisplayName("[SettlementService] 미정산 건이 있는지 조회한다")
    fun hasUnsettled() {
        doReturn(true).whenever(settlementRepository)
            .existsByEscortIdAndSettlementStatusNot(any(), any())

        assertThat(settlementService.hasUnsettled(escortId)).isTrue()
        verify(settlementRepository, times(1))
            .existsByEscortIdAndSettlementStatusNot(escortId, SettlementStatus.COMPLETED)
    }

    @Test
    @DisplayName("[SettlementService] 관리자 정산 요청 - 정산 전 건은 정산된다")
    fun requestForAdmin() {
        val settlement = createSettlement(SettlementStatus.PROCESSING)
        doReturn(createAccountDto()).whenever(settlementRepository).findByIdAndState(any())
        doReturn(1).whenever(settlementRepository).updateProcessingForAdmin(any())
        doReturn(settlement).whenever(settlementRepository).fetchJoinById(any())
        doReturn(clientResponse(1)).whenever(settlementClient).settlementRequest(any())

        settlementService.requestForAdmin(settlementId)

        assertThat(settlement.settlementStatus).isEqualTo(SettlementStatus.COMPLETED)
    }

    @Test
    @DisplayName("[SettlementService] 관리자 정산 요청 - 정산 중인 건은 선점에 실패해 거부된다")
    fun requestForAdminRejectedWhenProcessing() {
        doReturn(createAccountDto()).whenever(settlementRepository).findByIdAndState(any())
        // updateProcessingForAdmin 도 PENDING/FAILED 만 선점하므로 정산 중이면 0건이다
        // (Swagger 설명은 "PROCESSING 이어도 정산된다" 인데 구현은 updateProcessing 과 조건이 같다)
        doReturn(0).whenever(settlementRepository).updateProcessingForAdmin(any())

        assertThatThrownBy { settlementService.requestForAdmin(settlementId) }
            .isInstanceOf(InvalidException::class.java)
            .hasMessage("이미 정산 중이거나 정산이 완료되었습니다.")

        verify(settlementClient, never()).settlementRequest(any())
    }

    @Test
    @DisplayName("[SettlementService] 정산 생성 - 패널티도 원 단위로 버림한다")
    fun createSettlementPenaltyWithRemainder() {
        val escort = createUser("escort01", Role.ESCORT, escortId)
        val penalty: NoShowPenalty = mock()
        doAnswer { it.arguments[0] as Settlement }.whenever(settlementRepository).save(any())
        doReturn(penalty).whenever(noShowPenaltyService).getNoShowPenalty(escortId)

        // 11,352원 × 1.1시간 = 12,487.2원 → 90%는 11,238.48원, 그 10%는 1,123.8원
        val settlement = settlementService.createSettlement(
            BigDecimal("12487.2"), createApplication(escort), escort, LocalDate.now()
        )

        // 패널티도 지급액과 같은 기준(원 단위 버림)으로 떼야 1원이 새지 않는다
        assertThat(settlement.penaltyAmount).isEqualByComparingTo(BigDecimal.valueOf(1_123))
        assertThat(settlement.penaltyAmount.scale()).isEqualTo(0)
        assertThat(settlement.payoutAmount).isEqualByComparingTo(BigDecimal.valueOf(10_115))
        assertThat(settlement.platformFee).isEqualByComparingTo(BigDecimal.valueOf(1_249))

        // 패널티 기준 금액은 패널티를 떼기 전 지급액(11,238)이다
        verify(noShowPenaltyService, times(1)).applyPenalty(
            eq(escortId),
            argThat<BigDecimal> { compareTo(BigDecimal.valueOf(1_123)) == 0 },
            argThat<BigDecimal> { compareTo(BigDecimal.valueOf(11_238)) == 0 },
            eq(settlement),
            eq(penalty)
        )
    }

    @Test
    @DisplayName("[SettlementService] 정산 생성 - 지급액 + 수수료 + 패널티는 결제 금액을 넘지 않는다")
    fun createSettlementAmountInvariantWithPenalty() {
        val escort = createUser("escort01", Role.ESCORT, escortId)
        val penalty: NoShowPenalty = mock()
        doAnswer { it.arguments[0] as Settlement }.whenever(settlementRepository).save(any())
        doReturn(penalty).whenever(noShowPenaltyService).getNoShowPenalty(escortId)

        // 딱 나뉘는 금액과 원 미만이 남는 금액 모두 결제 금액을 초과해서는 안 된다
        listOf(BigDecimal.valueOf(150_000) to 150_000, BigDecimal("12487.2") to 12_487).forEach { (payout, paid) ->
            val settlement = settlementService.createSettlement(
                payout, createApplication(escort), escort, LocalDate.now()
            )
            val total = settlement.payoutAmount
                .add(settlement.platformFee)
                .add(settlement.penaltyAmount)

            assertThat(total).isLessThanOrEqualTo(BigDecimal.valueOf(paid.toLong()))
            // 버려지는 금액은 원 미만 단위뿐이다
            assertThat(BigDecimal.valueOf(paid.toLong()).subtract(total)).isLessThan(BigDecimal.ONE)
        }
    }
}
