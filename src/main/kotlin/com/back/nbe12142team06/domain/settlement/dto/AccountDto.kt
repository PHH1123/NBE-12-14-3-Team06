package com.back.nbe12142team06.domain.settlement.dto

import java.math.BigDecimal

data class AccountDto(
    val id: Long,
    val accountNumber: String,
    val name: String,
    val payoutAmount: BigDecimal
)
