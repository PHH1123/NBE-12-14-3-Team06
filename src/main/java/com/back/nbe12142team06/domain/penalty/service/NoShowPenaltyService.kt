package com.back.nbe12142team06.domain.penalty.service

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.penalty.entity.NoShowPenalty
import com.back.nbe12142team06.domain.penalty.entity.NoShowPenaltyStatus
import com.back.nbe12142team06.domain.penalty.repository.NoShowPenaltyRepository
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.global.exception.InvalidException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class NoShowPenaltyService(
    private val noShowPenaltyRepository: NoShowPenaltyRepository
) {

    private val log = LoggerFactory.getLogger(NoShowPenaltyService::class.java)

    @Transactional
    fun noShow(application: Application) {
        // 패널티 데이터 생성
        val noShowPenalty = noShowPenaltyRepository.save(
            NoShowPenalty(
                application = application
            )
        )
        log.info("노쇼 데이터 생성 - NoShowPenaltyId: ${noShowPenalty.id}")
    }

    @Transactional
    fun applyPenalty(
        escortId: Long,
        penaltyAmount: BigDecimal,
        baseAmount: BigDecimal,
        settlement: Settlement,
        noShowPenalty: NoShowPenalty
    ) {
        if (noShowPenaltyRepository.applyIfPending(noShowPenalty.id!!, penaltyAmount, baseAmount, settlement) == 0) {
            throw InvalidException(141, "이미 패널티가 적용되었습니다.")
        }
        noShowPenalty.updateNoShow(penaltyAmount, baseAmount, settlement)
        log.info("노쇼 패널티 적용 완료 - escortId: $escortId, settlementId: ${settlement.id}")
    }

    fun getNoShowPenalty(escortId: Long): NoShowPenalty? {
        return noShowPenaltyRepository.findByEscortIdAndStatus(escortId).firstOrNull()
    }

    // [관리자] 노쇼 패널티 정산금 조회
    fun getAll(startDate: LocalDate?, endDate: LocalDate?): List<NoShowPenalty> {
        val startDateTime =
            (startDate ?: LocalDate.now().minusDays(7)).atStartOfDay()
        val endDateTime =
            (endDate ?: LocalDate.now()).plusDays(1).atStartOfDay()

        return noShowPenaltyRepository.findAllByStatusAndDate(startDateTime, endDateTime)
    }
}
