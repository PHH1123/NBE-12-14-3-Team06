package com.back.nbe12142team06.domain.penalty.service

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.penalty.entity.NoShowPenalty
import com.back.nbe12142team06.domain.penalty.repository.NoShowPenaltyRepository
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

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
        noShowPenalty.updateNoShow(penaltyAmount, baseAmount, settlement)
        log.info("노쇼 패널티 적용 완료 - escortId: $escortId, settlementId: ${settlement.id}")
    }

    fun getNoShowPenalty(escortId: Long): NoShowPenalty? {
        return noShowPenaltyRepository.findByEscortIdAndStatus(escortId).firstOrNull()
    }

    // [관리자] 노쇼 패널티 정산금 조회
    fun getAll(startDate: LocalDate, endDate: LocalDate): List<NoShowPenalty> {
        return noShowPenaltyRepository.findAllByStatusAndDate(startDate, endDate)
    }
}
