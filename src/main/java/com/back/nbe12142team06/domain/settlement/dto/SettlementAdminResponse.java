package com.back.nbe12142team06.domain.settlement.dto;

import com.back.nbe12142team06.domain.settlement.entity.Settlement;
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus;

import java.util.List;

public record SettlementAdminResponse(
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
    public SettlementAdminResponse(List<Settlement> settlementList) {
        long totalSettlementAmount = 0L;
        long settlementCount = 0L;
        long totalSettledAmount = 0L;
        long settledCount = 0L;
        long totalUnSettledAmount = 0L;
        long unSettledCount = 0L;
        long totalPlatformFee = 0L;
        long platformFeeCount = 0L;
        long totalDonationAmount = 0L;
        long donationCount = 0L;
        long totalErrorSettlementAmount = 0L;
        long errorSettlementCount = 0L;

        for (Settlement settlement : settlementList) {
            totalSettlementAmount += settlement.getPayoutAmount();
            settlementCount++;

            if (settlement.getSettlementStatus().equals(SettlementStatus.COMPLETED)) {
                totalSettledAmount += settlement.getPayoutAmount();
                settledCount++;
                totalPlatformFee += settlement.getPlatformFee();
                platformFeeCount++;
                totalDonationAmount += settlement.getPenaltyAmount();
                if (settlement.getPenaltyAmount() != 0) {
                    donationCount++;
                }
            } else if (settlement.getSettlementStatus().equals(SettlementStatus.PROCESSING)) {
                totalErrorSettlementAmount += settlement.getPayoutAmount();
                errorSettlementCount++;
            } else {
                totalUnSettledAmount += settlement.getPayoutAmount();
                unSettledCount++;
            }
        }

        this(
                totalSettlementAmount,
                settlementCount,
                totalSettledAmount,
                settledCount,
                totalUnSettledAmount,
                unSettledCount,
                totalPlatformFee,
                platformFeeCount,
                totalDonationAmount,
                donationCount,
                totalErrorSettlementAmount,
                errorSettlementCount
        );
    }
}