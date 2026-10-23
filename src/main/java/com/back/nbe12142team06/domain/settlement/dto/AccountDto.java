package com.back.nbe12142team06.domain.settlement.dto;

import java.math.BigDecimal;

public record AccountDto(Long id, String accountNumber, String name, BigDecimal payoutAmount) {
}
