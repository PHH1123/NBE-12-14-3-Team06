package com.back.nbe12142team06.domain.settlement.dto;

import com.back.nbe12142team06.domain.settlement.entity.Settlement;
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus;

import java.math.BigDecimal;
import java.util.List;

public record SettlementAnalyticsResponse(
        long totalSettlementAmount,
        long settlementCount,
        long totalSettledAmount,
        long settledCount,
        long totalUnSettledAmount,
        long unSettledCount,
        long totalPlatformFee,
        long platformFeeCount,
        long totalDonationAmount,
        long donationCount,
        long totalErrorSettlementAmount,
        long errorSettlementCount
) {
    public SettlementAnalyticsResponse(List<Settlement> settlementList) {
        BigDecimal totalSettlementAmount = BigDecimal.ZERO;
        long settlementCount = 0L;
        BigDecimal totalSettledAmount = BigDecimal.ZERO;
        long settledCount = 0L;
        BigDecimal totalUnSettledAmount = BigDecimal.ZERO;
        long unSettledCount = 0L;
        BigDecimal totalPlatformFee = BigDecimal.ZERO;
        long platformFeeCount = 0L;
        BigDecimal totalDonationAmount = BigDecimal.ZERO;
        long donationCount = 0L;
        BigDecimal totalErrorSettlementAmount = BigDecimal.ZERO;
        long errorSettlementCount = 0L;

        for (Settlement settlement : settlementList) {
            totalSettlementAmount = totalSettlementAmount.add(settlement.getPayoutAmount());
            settlementCount++;

            if (settlement.getSettlementStatus().equals(SettlementStatus.COMPLETED)) {
                totalSettledAmount = totalSettledAmount.add(settlement.getPayoutAmount());
                settledCount++;
                totalPlatformFee = totalPlatformFee.add(settlement.getPlatformFee());
                platformFeeCount++;
                totalDonationAmount = totalDonationAmount.add(settlement.getPenaltyAmount());
                if (settlement.getPenaltyAmount().compareTo(BigDecimal.ZERO) != 0) {
                    donationCount++;
                }
            } else if (settlement.getSettlementStatus().equals(SettlementStatus.PROCESSING)) {
                totalErrorSettlementAmount = totalErrorSettlementAmount.add(settlement.getPayoutAmount());
                errorSettlementCount++;
            } else {
                totalUnSettledAmount = totalUnSettledAmount.add(settlement.getPayoutAmount());
                unSettledCount++;
            }
        }

        this(
                totalSettlementAmount.intValue(),
                settlementCount,
                totalSettledAmount.intValue(),
                settledCount,
                totalUnSettledAmount.intValue(),
                unSettledCount,
                totalPlatformFee.intValue(),
                platformFeeCount,
                totalDonationAmount.intValue(),
                donationCount,
                totalErrorSettlementAmount.intValue(),
                errorSettlementCount
        );
    }
}