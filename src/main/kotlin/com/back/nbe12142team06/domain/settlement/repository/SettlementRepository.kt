package com.back.nbe12142team06.domain.settlement.repository

import com.back.nbe12142team06.domain.settlement.dto.AccountDto
import com.back.nbe12142team06.domain.settlement.entity.Settlement
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

interface SettlementRepository : JpaRepository<Settlement, Long> {
    @Query(
        "select s.id, ep.accountNumber, e.name, s.payoutAmount " +
                "from Settlement s " +
                "join s.escort e " +
                "join EscortProfile ep on ep.userId=e.id " +
                "where s.id=:settlementId and e.id=:userId " +
                "and (s.settlementStatus='PENDING' or s.settlementStatus='FAILED' or s.settlementStatus='PROCESSING')"
    )
    fun findByIdAndState(
        @Param("userId") userId: Long,
        @Param("settlementId") settlementId: Long
    ): AccountDto?

    @Query(
        "select s " +
                "from Settlement s " +
                "join fetch s.escort u " +
                "join fetch s.application a " +
                "join fetch a.post p " +
                "where u.id=:userId and s.settledDate between :startDate and :endDate"
    )
    fun findAllByUserIdAndDate(
        @Param("userId") userId: Long,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        pageable: Pageable
    ): Page<Settlement>

    @Query(
        "select s.id from Settlement s " +
                "where s.escort.id=:userId and s.settledDate between :startDate and :endDate"
    )
    fun findIdsByUserAndDate(
        @Param("userId") userId: Long,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        pageable: Pageable
    ): Page<Long>

    @Query(
        "select s " +
                "from Settlement s " +
                "join fetch s.escort u " +
                "join fetch s.application a " +
                "join fetch a.post p " +
                "where s.id in :ids"
    )
    fun findAllByIds(@Param("ids") ids: List<Long>): List<Settlement>

    @Query(
        "select s.id, ep.accountNumber, e.name, s.payoutAmount " +
                "from Settlement s " +
                "join s.escort e " +
                "join EscortProfile ep on ep.userId=e.id " +
                "where (s.settlementStatus='PENDING' or s.settlementStatus='FAILED') and s.settledDate <= current_date"
    )
    fun findAllByStatusAndDate(): List<AccountDto>

    // clearAutomatically는 1차 캐시를 비워줌 -> 테스트에서 검증할 때 status 반영이 안되서 추가
    @Modifying(clearAutomatically = true)
    @Query("update Settlement s set s.settlementStatus=:status where s.id=:id")
    fun updateStatus(
        @Param("id") id: Long,
        @Param("status") status: SettlementStatus
    ): Int

    @Modifying(clearAutomatically = true)
    @Query(
        "update Settlement s " +
                "set s.settlementStatus=SettlementStatus.PROCESSING " +
                "where s.id=:settlementId " +
                "and (s.settlementStatus=SettlementStatus.PENDING or s.settlementStatus=SettlementStatus.FAILED)"
    )
    fun updateProcessing(@Param("settlementId") settlementId: Long): Int

    @Query(
        "select s from Settlement s " +
                "join fetch s.application " +
                "join fetch s.escort " +
                "where s.id=:id"
    )
    fun findJoinFetchById(@Param("id") id: Long): Settlement?

    @Query(
        "select s from Settlement s " +
                "where s.createdAt >= :startDate and s.createdAt < :endDate"
    )
    fun findAnalytics(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<Settlement>

    @Query(
        "select s from Settlement s " +
                "join fetch s.escort " +
                "where s.createdAt >= :startDate and s.createdAt < :endDate"
    )
    fun findByPeriod(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime,
        pageable: Pageable
    ): Page<Settlement>

    @Query(
        "select s.id, ep.accountNumber, e.name, s.payoutAmount " +
                "from Settlement s " +
                "join s.escort e " +
                "join EscortProfile ep on ep.userId=e.id " +
                "where s.id=:settlementId " +
                "and (s.settlementStatus='PENDING' or s.settlementStatus='FAILED' or s.settlementStatus='PROCESSING')"
    )
    fun findByIdAndState(@Param("settlementId") settlementId: Long): AccountDto?

    @Modifying(clearAutomatically = true)
    @Query(
        "update Settlement s " +
                "set s.settlementStatus=SettlementStatus.PROCESSING " +
                "where s.id=:settlementId " +
                "and s.settlementStatus in (SettlementStatus.PENDING, SettlementStatus.FAILED)"
    )
    fun updateProcessingForAdmin(@Param("settlementId") settlementId: Long): Int

    fun existsByEscortIdAndSettlementStatusNot(escortId: Long, status: SettlementStatus): Boolean

    @Query("select s from Settlement s join fetch s.escort where s.id=:settlementId")
    fun fetchJoinById(@Param("settlementId") settlementId: Long): Settlement?
}
