package com.back.nbe12142team06.domain.settlement.scheduler

import com.back.nbe12142team06.domain.settlement.service.SettlementService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * 스케줄러는 정산 처리를 위임하고 집계를 로그로만 남긴다.
 */
class SettlementSchedulerTest {

    private val settlementService: SettlementService = mock()
    private val settlementScheduler = SettlementScheduler(settlementService)

    @Test
    @DisplayName("[SettlementScheduler] 정산 처리를 호출한다")
    fun expireOverduePosts() {
        doReturn(intArrayOf(2, 1, 1)).whenever(settlementService).settlementProcess()

        settlementScheduler.expireOverduePosts()

        verify(settlementService, times(1)).settlementProcess()
    }

    @Test
    @DisplayName("[SettlementScheduler] 정산할 건이 없어도 예외 없이 끝난다")
    fun expireOverduePostsWithoutTarget() {
        doReturn(intArrayOf(0, 0, 0)).whenever(settlementService).settlementProcess()

        settlementScheduler.expireOverduePosts()

        verify(settlementService, times(1)).settlementProcess()
    }
}
