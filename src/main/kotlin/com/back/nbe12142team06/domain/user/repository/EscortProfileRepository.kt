package com.back.nbe12142team06.domain.user.repository

import com.back.nbe12142team06.domain.user.entity.EscortProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.*

interface EscortProfileRepository : JpaRepository<EscortProfile, Long> {

    // 응답 DTO 가 user 를 쓰므로 함께 조회 (LazyInitializationException 방지)
    @Query("select p from EscortProfile p join fetch p.user where p.userId = :userId")
    fun findByIdWithUser(@Param("userId") userId: Long): EscortProfile?

    // 회원 탈퇴 시 동행 매니저 프로필 삭제
    @Modifying(flushAutomatically = true)
    @Query("delete from EscortProfile p where p.user.id = :userId")
    fun deleteByUserId(@Param("userId") userId: Long)

    // 교육 이수 처리 (이미 인증된 프로필은 0 반환)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update EscortProfile e
        set e.verified = true, e.verifiedAt = :now, e.version = e.version + 1
        where e.userId = :userId and e.verified = false
        """
    )
    fun verify(@Param("userId") userId: Long, @Param("now") now: LocalDateTime): Int

    // 리뷰 평점 누적
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update EscortProfile e
        set e.ratingSum = e.ratingSum + :rating, e.ratingCount = e.ratingCount + 1,
            e.version = e.version + 1
        where e.userId = :userId
        """
    )
    fun addRating(@Param("userId") userId: Long, @Param("rating") rating: Int): Int

    // 동행 완료 횟수 증가
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update EscortProfile e
        set e.completedCount = e.completedCount + 1, e.version = e.version + 1
        where e.userId = :userId
        """
    )
    fun increaseCompletedCount(@Param("userId") userId: Long): Int
}