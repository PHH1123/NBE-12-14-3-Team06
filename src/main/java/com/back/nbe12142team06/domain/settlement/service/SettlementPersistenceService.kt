package com.back.nbe12142team06.domain.settlement.service

import com.back.nbe12142team06.domain.settlement.dto.AccountDto
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository
import com.back.nbe12142team06.global.exception.NotFoundException
import lombok.RequiredArgsConstructor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class SettlementPersistenceService(
    private val settlementRepository: SettlementRepository
) {

    fun findAllByStatusAndDate(): List<AccountDto> = settlementRepository.findAllByStatusAndDate()

    @Transactional
    fun updateSettlement(settlementId: Long, status: SettlementStatus): Settlement {
        val settlement: Settlement = settlementRepository.fetchJoinById(settlementId)
            ?: throw NotFoundException(71, "정산 데이터를 찾을 수 없습니다.")

        when (status) {
            SettlementStatus.COMPLETED -> settlement.settlementCompleted()
            SettlementStatus.FAILED -> settlement.settlementFailed()
            else -> {}
        }

        return settlement
    }

    fun findAccountDto(userId: Long, settlementId: Long): AccountDto =
        settlementRepository.findByIdAndState(userId, settlementId)
            ?: throw NotFoundException(71, "정산 데이터를 찾을 수 없습니다.")

    @Transactional
    fun processingSettlement(settlementId: Long): Int =
        settlementRepository.updateProcessing(settlementId)

    fun findById(settlementId: Long): Settlement =
        settlementRepository.findJoinFetchById(settlementId)
            ?: throw NotFoundException(71, "정산 데이터를 찾을 수 없습니다.")

    fun getAnalytics(startDate: LocalDateTime, endDate: LocalDateTime): List<Settlement> =
        settlementRepository.findAnalytics(startDate, endDate)


    fun getSettlementForAdmin(startDate: LocalDateTime, endDate: LocalDateTime, pageable: Pageable): Page<Settlement> =
        settlementRepository.findByPeriod(startDate, endDate, pageable)


    fun findAccountDto(settlementId: Long): AccountDto =
        settlementRepository.findByIdAndState(settlementId)
            ?: throw NotFoundException(71, "정산 데이터를 찾을 수 없습니다.")


    @Transactional
    fun processingSettlementForAdmin(settlementId: Long): Int =
        settlementRepository.updateProcessingForAdmin(settlementId)
}
