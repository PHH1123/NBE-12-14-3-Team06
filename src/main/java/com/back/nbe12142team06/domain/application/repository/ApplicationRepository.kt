package com.back.nbe12142team06.domain.application.repository

import com.back.nbe12142team06.domain.application.entity.Application
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus
import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import com.back.nbe12142team06.domain.user.entity.User
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

interface ApplicationRepository : JpaRepository<Application, Long> {
    // 취소되지 않은 동일 공고 중복 지원 방지
    fun existsByPostAndEscortAndStatusNot(
        post: Post,
        escort: User,
        status: ApplicationStatus,
    ): Boolean

    // 공고별 지원 목록 조회
    @Query(
        """
            SELECT a
            FROM Application a
            JOIN FETCH a.escort
            WHERE a.post.id = :postId
              AND a.status <> com.back.nbe12142team06.domain.application.enums.ApplicationStatus.CANCELED
            """
    )
    fun findAllByPostIdWithEscort(@Param("postId") postId: Long?): List<Application>

    // 페이징 조회용: Java의 오버로드와 정렬 유지
    @Query(
        """
            SELECT a
            FROM Application a
            JOIN FETCH a.escort
            WHERE a.post.id = :postId
              AND a.status <> com.back.nbe12142team06.domain.application.enums.ApplicationStatus.CANCELED
            ORDER BY a.id DESC
            """
    )
    fun findAllByPostIdWithEscort(
        @Param("postId") postId: Long?,
        pageable: Pageable,
    ): Page<Application>

    fun findAllByPostAndStatus(post: Post, status: ApplicationStatus): List<Application>

    // 같은 동행인의 특정 상태 지원 조회
    fun findAllByEscortAndStatus(escort: User, status: ApplicationStatus): List<Application>

    // 동행 매니저와 의뢰인 사이에 진행 중인 매칭이 있는지 확인
    @Query(
        """
            select case when count(a) > 0 then true else false end
            from Application a
            where a.escort.id = :escortId
              and a.post.client.id = :clientId
              and a.status = com.back.nbe12142team06.domain.application.enums.ApplicationStatus.ACCEPTED
              and a.post.postStatus in (
                  com.back.nbe12142team06.domain.post.entity.PostStatus.MATCHED,
                  com.back.nbe12142team06.domain.post.entity.PostStatus.IN_PROGRESS
              )
            """
    )
    fun hasActiveMatching(
        @Param("escortId") escortId: Long?,
        @Param("clientId") clientId: Long?,
    ): Boolean

    @Query(
        """
            SELECT a
            FROM Application a
            JOIN FETCH a.post
            WHERE a.escort.id = :escortId
            ORDER BY a.id DESC
            """
    )
    fun findAllByEscortIdWithPost(@Param("escortId") escortId: Long?): List<Application>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Application a WHERE a.id = :id")
    fun findByIdWithLock(@Param("id") id: Long?): Application?

    // 지원 ID로 공고 ID만 조회
    @Query("SELECT a.post.id FROM Application a WHERE a.id = :id")
    fun findPostIdById(@Param("id") id: Long?): Long?

    // 지원 ID로 동행매니저 ID만 조회
    @Query("SELECT a.escort.id FROM Application a WHERE a.id = :id")
    fun findEscortIdById(@Param("id") id: Long?): Long?

    // 위치 컬럼만 조건부 UPDATE하여 지원 상태 변경과의 충돌을 방지한다.
    // ACCEPTED + IN_PROGRESS + 귀가 완료 전인 지원에서만 허용한다.
    @Modifying
    @Query(
        """
            update Application a
               set a.lat = :lat,
                   a.lng = :lng,
                   a.accuracy = :accuracy,
                   a.locationUpdatedAt = :updatedAt
             where a.id = :id
               and a.status = com.back.nbe12142team06.domain.application.enums.ApplicationStatus.ACCEPTED
               and exists (
                    select 1 from Post p
                     where p = a.post
                       and p.postStatus = com.back.nbe12142team06.domain.post.entity.PostStatus.IN_PROGRESS
               )
               and not exists (
                    select 1 from EscortProgressLog progressEntry
                     where progressEntry.application = a
                       and progressEntry.progress = com.back.nbe12142team06.domain.application.enums.EscortProgress.ARRIVED_HOME
               )
            """
    )
    fun updateLocation(
        @Param("id") id: Long?,
        @Param("lat") lat: BigDecimal?,
        @Param("lng") lng: BigDecimal?,
        @Param("accuracy") accuracy: BigDecimal?,
        @Param("updatedAt") updatedAt: Instant?,
    ): Int

    // 귀가 완료 시 최근 위치(위도·경도·정확도·갱신 시각)를 모두 삭제한다.
    @Modifying(flushAutomatically = true)
    @Query(
        """
            update Application a
               set a.lat = null, a.lng = null,
                   a.accuracy = null, a.locationUpdatedAt = null
             where a.id = :applicationId
            """
    )
    fun clearLocation(@Param("applicationId") applicationId: Long?): Int

    // 공고 완료·취소·삭제 시 남은 위치까지 삭제한다.
    @Modifying(flushAutomatically = true)
    @Query(
        """
            update Application a
               set a.lat = null, a.lng = null,
                   a.accuracy = null, a.locationUpdatedAt = null
             where a.post.id = :postId
            """
    )
    fun clearLocationsByPostId(@Param("postId") postId: Long?): Int

    // 회원 탈퇴·정지 시 취소할 지원 목록 조회
    @Query(
        """
            SELECT a
            FROM Application a
            JOIN FETCH a.post p
            WHERE a.escort.id = :escortId
              AND (
                  (a.status = :pending AND p.postStatus = :open)
                  OR
                  (a.status = :accepted AND p.postStatus = :matched)
              )
            """
    )
    fun findAllCancelableByEscortId(
        @Param("escortId") escortId: Long?,
        @Param("pending") pending: ApplicationStatus,
        @Param("accepted") accepted: ApplicationStatus,
        @Param("open") open: PostStatus,
        @Param("matched") matched: PostStatus,
    ): List<Application>

    // 회원 탈퇴 제한: 진행 중인 동행이 있는지 확인
    @Query(
        """
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Application a
            WHERE a.escort.id = :escortId
              AND a.status = :accepted
              AND a.post.postStatus = :inProgress
            """
    )
    fun existsInProgressByEscortId(
        @Param("escortId") escortId: Long?,
        @Param("accepted") accepted: ApplicationStatus,
        @Param("inProgress") inProgress: PostStatus,
    ): Boolean

    @Modifying
    @Query(
        "update Application a set " +
            "a.status=ApplicationStatus.REJECTED, a.activePostId=null, a.updatedAt=:now " +
            "where a.post=:post and a.status=ApplicationStatus.PENDING"
    )
    fun rejectAllByPost(
        @Param("post") post: Post,
        @Param("now") now: LocalDateTime,
    ): Int

    @Query(
        "select count(a) > 0 from Application a " +
            "where a.post.id=:postId and a.escort.id=:escortId"
    )
    fun existsByPostIdAndEscortId(
        @Param("postId") postId: Long?,
        @Param("escortId") escortId: Long?,
    ): Boolean

    // 이 공고에 승인(ACCEPTED)된 동행인인지
    fun existsByPostIdAndEscortIdAndStatus(
        postId: Long?,
        escortId: Long?,
        status: ApplicationStatus,
    ): Boolean
}
