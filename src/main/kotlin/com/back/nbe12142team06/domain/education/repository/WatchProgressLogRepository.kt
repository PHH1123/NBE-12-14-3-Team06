package com.back.nbe12142team06.domain.education.repository

import com.back.nbe12142team06.domain.education.entity.WatchProgressLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WatchProgressLogRepository : JpaRepository<WatchProgressLog, Long> {

    // 동행 매니저 탈퇴 시 시청 로그 삭제 (진행 상황보다 먼저 지워야 FK 제약에 걸리지 않음)
    @Modifying(flushAutomatically = true)
    @Query(
        """
        delete from WatchProgressLog w
            where w.educationProgress.id in (
                select p.id from EducationProgress p where p.escortProfile.userId = :userId
            )
        """
    )
    fun deleteAllByEscortUserId(@Param("userId") userId: Long)
}