package com.back.nbe12142team06.domain.settlement.service

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.notification.entity.NotificationContent
import com.back.nbe12142team06.domain.notification.entity.NotificationUrl
import com.back.nbe12142team06.domain.notification.service.NoticeService
import com.back.nbe12142team06.domain.penalty.entity.NoShowPenalty
import com.back.nbe12142team06.domain.penalty.service.NoShowPenaltyService
import com.back.nbe12142team06.domain.settlement.client.SettlementClient
import com.back.nbe12142team06.domain.settlement.client.SettlementClientRequest
import com.back.nbe12142team06.domain.settlement.client.SettlementClientResponse
import com.back.nbe12142team06.domain.settlement.dto.AccountDto
import com.back.nbe12142team06.domain.settlement.dto.SettlementResponse
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.global.exception.ForbiddenException
import com.back.nbe12142team06.global.exception.InternalServerErrorException
import com.back.nbe12142team06.global.exception.InvalidException
import com.back.nbe12142team06.global.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class SettlementService(
    private val settlementPersistenceService: SettlementPersistenceService,
    private val settlementRepository: SettlementRepository,
    private val noShowPenaltyService: NoShowPenaltyService,
    private val noticeService: NoticeService,
    private val settlementClient: SettlementClient
) {

    private val log = LoggerFactory.getLogger(SettlementService::class.java)

    // 단일 정산 요청
    fun request(userId: Long, settlementId: Long) {
        val accountDto = settlementPersistenceService.findAccountDto(userId, settlementId)

        // 정산 중 상태 변경
        val row = settlementPersistenceService.processingSettlement(settlementId)

        // 정산 요청
        requestSettlement(settlementId, row, accountDto)
    }

    private fun requestSettlement(
        settlementId: Long,
        row: Int,
        accountDto: AccountDto,
    ) {
        // 변경 된 행이 0개라면 정산 중이거나 정산이 완료 된 경우
        if (row == 0) {
            throw InvalidException(71, "이미 정산 중이거나 정산이 완료되었습니다.")
        }

        val response: SettlementClientResponse
        try {
            response = settlementApi(accountDto.payoutAmount, accountDto.name, accountDto.accountNumber)
        } catch (e: RuntimeException) {
            // 금융 결제원 API 요청 에러
            settlementPersistenceService.updateSettlement(settlementId, SettlementStatus.FAILED)
            log.error("정산 실패 - 금융 결제원 API 요청 실패, settlementId: {}", settlementId, e)
            throw InternalServerErrorException(72, "정산에 실패했습니다.")
        }

        if (response.res_cnt < 1) {
            settlementPersistenceService.updateSettlement(settlementId, SettlementStatus.FAILED)
            log.error("정산 실패 - 금융 결제원 API 요청 실패, settlementId: {}", settlementId)
            throw InternalServerErrorException(72, "정산에 실패했습니다.")
        }

        val settlement: Settlement
        try {
            settlement = settlementPersistenceService.updateSettlement(settlementId, SettlementStatus.COMPLETED)
            log.info("정산 성공 - settlementId: {}", settlementId)
        } catch (e: RuntimeException) {
            log.error("정산 DB 변경 실패, 정산은 성공 - settlementId: {}", accountDto.id)
            throw InternalServerErrorException(71, "정산 데이터 저장 중 DB 에러가 발생했습니다.")
        }

        // 알림
        noticeService.send(
            settlement.escort.id,
            NotificationContent.C_SETTLEMENT_DONE.of(settlement.payoutAmount.toString()),
            NotificationUrl.U_SETTLEMENT_DONE.of(settlement.application.id!!)
        )
    }

    // 정산 외부 API 로직(목으로 대체)
    @Transactional(readOnly = true)
    fun findAll(
        userId: Long,
        startDate: LocalDate?,
        endDate: LocalDate?,
        pageable: Pageable
    ): Page<SettlementResponse> {

        val idPage: Page<Long> = settlementRepository.findIdsByUserAndDate(
            userId = userId,
            startDate = startDate ?: LocalDate.now().minusDays(1),
            endDate = (endDate ?: LocalDate.now()).plusDays(1),
            pageable = pageable
        )

        if (idPage.isEmpty) {
            return Page.empty(pageable)
        }

        val byId = settlementRepository.findAllByIds(idPage.getContent())
            .associateBy { it.requiredId }

        return idPage.map { id -> SettlementResponse(byId.getValue(id)) }
    }

    @Transactional(readOnly = true)
    fun findSettlement(userId: Long, settlementId: Long): SettlementResponse {
        val settlement: Settlement = settlementRepository.findByIdOrNull(settlementId)
            ?: throw NotFoundException(71, "정산 데이터를 찾을 수 없습니다.")

        if (settlement.escort.id != userId) {
            throw ForbiddenException(71, "정산 요청할 권한이 없습니다.")
        }

        return SettlementResponse(settlement)
    }

    // 정산 스케줄링
    fun settlementProcess(): IntArray {
        val accountDtoList = settlementPersistenceService.findAllByStatusAndDate()
        var successCount = 0
        var failedCount = 0

        for (accountDto in accountDtoList) {
            // 정산 중 상태 변경
            val row = settlementPersistenceService.processingSettlement(accountDto.id)

            // 변경 된 행이 0개라면 정산 중이거나 정산이 완료 된 경우
            if (row == 0) {
                continue
            }

            val response: SettlementClientResponse
            try {
                response = settlementApi(accountDto.payoutAmount, accountDto.name, accountDto.accountNumber)
            } catch (e: RuntimeException) {
                // 금융 결제원 API 요청 에러
                settlementPersistenceService.updateSettlement(accountDto.id, SettlementStatus.FAILED)
                log.error("정산 실패 - 금융 결제원 API 요청 실패, settlementId: {}", accountDto.id, e)
                failedCount++
                continue
            }

            if (response.res_cnt < 1) {
                // 정산 성공 0건
                settlementPersistenceService.updateSettlement(accountDto.id, SettlementStatus.FAILED)
                failedCount++
                log.error("정산 실패 - 금융 결제원 API 요청 실패, settlementId: {}", accountDto.id)
                continue
            }

            try {
                settlementPersistenceService.updateSettlement(accountDto.id, SettlementStatus.COMPLETED)
            } catch (e: RuntimeException) {
                log.error("정산 DB 변경 실패, 송금은 성공 - settlementId: {}", accountDto.id)
                failedCount++
                continue
            }

            successCount++
            log.info("정산 성공 - settlementId: {}", accountDto.id)
        }

        return intArrayOf(successCount + failedCount, successCount, failedCount)
    }

    private fun settlementApi(amount: BigDecimal, name: String, account: String): SettlementClientResponse =
        settlementClient.settlementRequest(
            SettlementClientRequest(
                account,
                name,
                amount.toInt()
            )
        ) as SettlementClientResponse

    @Transactional
    fun createSettlement(
        payoutAmount: BigDecimal,
        application: Application,
        escort: User,
        settledDate: LocalDate
    ): Settlement {
        var settlementAmount = payoutAmount.multiply(BigDecimal.valueOf(0.9)).setScale(0, RoundingMode.DOWN)
        val platformFee = payoutAmount.subtract(settlementAmount).setScale(0, RoundingMode.DOWN)

        // 패널티 적용해야 하는지 확인
        val noShowPenalty: NoShowPenalty? = noShowPenaltyService.getNoShowPenalty(escort.id)

        // 패널티 적용 시 정산 금액 차감
        var penaltyAmount = BigDecimal.ZERO
        noShowPenalty?.let {
            penaltyAmount = settlementAmount.multiply(BigDecimal.valueOf(0.1)).setScale(0, RoundingMode.DOWN)
            settlementAmount = settlementAmount.subtract(penaltyAmount)
        }

        val settlement = Settlement(
            payoutAmount = settlementAmount,
            platformFee = platformFee,
            penaltyAmount = penaltyAmount,
            settledDate = settledDate,
            application = application,
            escort = escort
        )

        val savedSettlement = settlementRepository.save(settlement)

        // 패널티 데이터 업데이트
        noShowPenalty?.let {
            noShowPenaltyService.applyPenalty(
                escortId = escort.id, penaltyAmount,
                baseAmount = settlementAmount.add(penaltyAmount), settlement, noShowPenalty
            )
        }

        return savedSettlement
    }

    fun getAnalytics(startDate: LocalDate?, endDate: LocalDate?): List<Settlement> {
        val startDateTime: LocalDateTime =
            (startDate ?: LocalDate.now().minusDays(7)).atStartOfDay()
        val endDateTime: LocalDateTime =
            (endDate ?: LocalDate.now()).plusDays(1).atStartOfDay()

        return settlementPersistenceService.getAnalytics(startDateTime, endDateTime)
    }

    fun getSettlementForAdmin(startDate: LocalDate?, endDate: LocalDate?, pageable: Pageable): Page<Settlement> {
        val startDateTime: LocalDateTime =
            (startDate ?: LocalDate.now().minusDays(7)).atStartOfDay()
        val endDateTime: LocalDateTime =
            (endDate ?: LocalDate.now()).plusDays(1).atStartOfDay()

        return settlementPersistenceService.getSettlementForAdmin(startDateTime, endDateTime, pageable)
    }

    fun requestForAdmin(settlementId: Long) {
        val accountDto = settlementPersistenceService.findAccountDto(settlementId)

        val row = settlementPersistenceService.processingSettlementForAdmin(settlementId)

        requestSettlement(settlementId, row, accountDto)
    }

    fun hasUnsettled(userId: Long): Boolean {
        return this.settlementRepository.existsByEscortIdAndSettlementStatusNot(userId, SettlementStatus.COMPLETED)
    }
}
