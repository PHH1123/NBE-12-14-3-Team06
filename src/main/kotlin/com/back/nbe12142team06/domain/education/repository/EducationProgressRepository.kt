package com.back.nbe12142team06.domain.education.repository

import com.back.nbe12142team06.domain.education.entity.EducationProgress
import com.back.nbe12142team06.domain.education.entity.EducationVideo
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface EducationProgressRepository : JpaRepository<EducationProgress, Long> {

    // 필수 영상 중 시청 완료한 영상 수
    fun countByEscortProfileAndCompletedTrueAndEducationVideoRequiredTrue(escortProfile: EscortProfile): Long

    fun findByEscortProfileAndEducationVideo(
        escortProfile: EscortProfile,
        educationVideo: EducationVideo,
    ): EducationProgress?

    @EntityGraph(attributePaths = ["educationVideo"])
    fun findAllByEscortProfileUserIdOrderByEducationVideoIdAsc(userId: Long): List<EducationProgress>

    @EntityGraph(attributePaths = ["educationVideo"])
    fun findByEscortProfileUserIdAndEducationVideoId(userId: Long, videoId: Long): EducationProgress?

    @Modifying(flushAutomatically = true)
    @Query(
        """
        delete from EducationProgress e
            where e.escortProfile.userId = :userId
        """
    )
    fun deleteAllByEscortUserId(@Param("userId") userId: Long)
}