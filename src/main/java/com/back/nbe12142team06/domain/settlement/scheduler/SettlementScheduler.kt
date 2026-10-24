package com.back.nbe12142team06.domain.settlement.scheduler

import com.back.nbe12142team06.domain.settlement.service.SettlementService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SettlementScheduler(
    private val settlementService: SettlementService
) {

    private val log = LoggerFactory.getLogger(SettlementScheduler::class.java)

    // 평일 10시마다 실행
    // 정산 실패했던 건에 대해서도 재시도
    @Scheduled(cron = "0 0 10 * * 1-5")
    fun expireOverduePosts() {
        val counts = settlementService.settlementProcess()

        // 로그
        log.info("정산 스케줄링 총 {}건, 성공 {}건, 실패 {}건", counts[0], counts[1], counts[2])
    }
}
