package com.back.nbe12142team06.domain.settlement.dto

import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus

@JvmRecord
data class SettlementAnalyticsResponse(
    val totalSettlementAmount: Long,
    val settlementCount: Long,
    val totalSettledAmount: Long,
    val settledCount: Long,
    val totalUnSettledAmount: Long,
    val unSettledCount: Long,
    val totalPlatformFee: Long,
    val platformFeeCount: Long,
    val totalDonationAmount: Long,
    val donationCount: Long,
    val totalErrorSettlementAmount: Long,
    val errorSettlementCount: Long,
) {
    companion object {
        @JvmStatic
        fun from(settlementList: List<Settlement>): SettlementAnalyticsResponse {

            val completed = settlementList.filter { it.settlementStatus == SettlementStatus.COMPLETED }
            val processing = settlementList.filter { it.settlementStatus == SettlementStatus.PROCESSING }
            val unsettled = settlementList - completed.toSet() - processing.toSet()
            return SettlementAnalyticsResponse(
                totalSettlementAmount = settlementList.sumOf { it.payoutAmount }.toLong(),
                settlementCount = settlementList.size.toLong(),
                totalSettledAmount = completed.sumOf { it.payoutAmount }.toLong(),
                settledCount = completed.size.toLong(),
                totalUnSettledAmount = unsettled.sumOf { it.payoutAmount }.toLong(),
                unSettledCount = unsettled.size.toLong(),
                totalPlatformFee = completed.sumOf { it.platformFee }.toLong(),
                platformFeeCount = completed.size.toLong(),
                totalDonationAmount = completed.sumOf { it.penaltyAmount }.toLong(),
                donationCount = completed.count { it.penaltyAmount.signum() != 0 }.toLong(),
                totalErrorSettlementAmount = processing.sumOf { it.payoutAmount }.toLong(),
                errorSettlementCount = processing.size.toLong()
            )
        }
    }
}