package com.back.nbe12142team06.domain.user.repository

import com.back.nbe12142team06.domain.user.entity.EscortGradeHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EscortGradeHistoryRepository : JpaRepository<EscortGradeHistory, Long> {

    fun findAllByEscortProfileUserId(userId: Long): List<EscortGradeHistory>

    // 동행 매니저 탈퇴 시 등급 이력 삭제
    @Modifying(flushAutomatically = true)
    @Query(
        """
        delete from EscortGradeHistory h
        where h.escortProfile.userId = :userId
        """
    )
    fun deleteAllByEscortUserId(@Param("userId") userId: Long)
}