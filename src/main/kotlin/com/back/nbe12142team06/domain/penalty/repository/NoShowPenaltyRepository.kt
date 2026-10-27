package com.back.nbe12142team06.domain.penalty.repository

import com.back.nbe12142team06.domain.penalty.entity.NoShowPenalty
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDateTime

interface NoShowPenaltyRepository : JpaRepository<NoShowPenalty, Long> {
    @Query(
        "select p from NoShowPenalty p " +
                "where p.application.escort.id=:escortId and p.status = 'PENDING' " +
                "order by p.id"
    )
    fun findByEscortIdAndStatus(@Param("escortId") escortId: Long): List<NoShowPenalty>

    @Query(
        "select p from NoShowPenalty p " +
                "where p.status='APPLIED' and p.createdAt >= :startDateTime and p.createdAt < :endDateTime"
    )
    fun findAllByStatusAndDate(
        @Param("startDateTime") startDateTime: LocalDateTime,
        @Param("endDateTime") endDateTime: LocalDateTime
    ): List<NoShowPenalty>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update NoShowPenalty p " +
                "set p.status=NoShowPenaltyStatus.APPLIED, p.amount=:amount, p.baseAmount=:baseAmount, p.settlement=:settlement " +
                "where p.id=:id and p.status=NoShowPenaltyStatus.PENDING"
    )
    fun applyIfPending(
        @Param("id") id: Long,
        @Param("amount") amount: BigDecimal,
        @Param("baseAmount") baseAmount: BigDecimal,
        @Param("settlement") settlement: Settlement
    ): Int
}
