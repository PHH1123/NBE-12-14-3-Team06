package com.back.nbe12142team06.domain.settlement.dto;

import com.back.nbe12142team06.domain.application.entity.Application;
import com.back.nbe12142team06.domain.settlement.entity.Settlement;
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus;
import com.back.nbe12142team06.domain.user.entity.User;

import java.time.LocalDate;

public record SettlementAdminResponse(
        Long id,
        int payoutAmount,
        int platformFee,
        int penaltyAmount,
        SettlementStatus status,
        LocalDate settledAt,
        Long applicationId,
        Long userId,
        String name
) {
    public SettlementAdminResponse(Settlement settlement) {
        Application application = null;
        User user = null;
        if (settlement.getApplication() != null) {
            application = settlement.getApplication();
        }

        if (settlement.getEscort() != null) {
            user = settlement.getEscort();
        }

        this(
                settlement.getId(),
                settlement.getPayoutAmount(),
                settlement.getPlatformFee(),
                settlement.getPenaltyAmount(),
                settlement.getSettlementStatus(),
                settlement.getSettledDate(),
                application == null ? null : application.getId(),
                user == null ? null : user.getId(),
                user == null ? null : user.getName()
        );
    }
}
