package com.back.nbe12142team06.domain.application.repository;

import com.back.nbe12142team06.domain.application.entity.Application;
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus;
import com.back.nbe12142team06.domain.post.entity.Post;
import com.back.nbe12142team06.domain.post.entity.PostStatus;
import com.back.nbe12142team06.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    // 취소되지 않은 동일 공고 중복 지원 방지
    boolean existsByPostAndEscortAndStatusNot(Post post, User escort, ApplicationStatus status);

    // 공고별 지원 목록 조회
    @Query("""
            SELECT a
            FROM Application a
            JOIN FETCH a.escort
            WHERE a.post.id = :postId
              AND a.status <> com.back.nbe12142team06.domain.application.enums.ApplicationStatus.CANCELED
            """)
    List<Application> findAllByPostIdWithEscort(@Param("postId") Long postId);

    // 페이징 조회용
    @Query("""
            SELECT a
            FROM Application a
            JOIN FETCH a.escort
            WHERE a.post.id = :postId
              AND a.status <> com.back.nbe12142team06.domain.application.enums.ApplicationStatus.CANCELED
            ORDER BY a.id DESC
            """)
    Page<Application> findAllByPostIdWithEscort(
            @Param("postId") Long postId,
            Pageable pageable
    );

    List<Application> findAllByPostAndStatus(Post post, ApplicationStatus status);

    // 같은 동행인의 특정 상태 지원 조회
    List<Application> findAllByEscortAndStatus(User escort, ApplicationStatus status);

    // 동행 매니저와 의뢰인 사이에 진행 중인 매칭이 있는지 확인
    // 해당 지원의 상태는 ACCEPTED이면서 공고의 상태는 MATCHED or IN_PROGRESS 인 지원이 1개 이상일때 true
    @Query("""
            select case when count(a) > 0 then true else false end
            from Application a
            where a.escort.id = :escortId
              and a.post.client.id = :clientId
              and a.status = com.back.nbe12142team06.domain.application.enums.ApplicationStatus.ACCEPTED
              and a.post.postStatus in (
                  com.back.nbe12142team06.domain.post.entity.PostStatus.MATCHED,
                  com.back.nbe12142team06.domain.post.entity.PostStatus.IN_PROGRESS
              )
            """)
    boolean hasActiveMatching(@Param("escortId") Long escortId, @Param("clientId") Long clientId);

    @Query("""
            SELECT a
            FROM Application a
            JOIN FETCH a.post
            WHERE a.escort.id = :escortId
            ORDER BY a.id DESC
            """)
    List<Application> findAllByEscortIdWithPost(
            @Param("escortId") Long escortId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Application a WHERE a.id = :id")
    Optional<Application> findByIdWithLock(@Param("id") Long id);

    // 지원 ID로 공고 ID만 조회
    @Query("SELECT a.post.id FROM Application a WHERE a.id = :id")
    Optional<Long> findPostIdById(@Param("id") Long id);

    // 지원 ID로 동행매니저 ID만 조회
    @Query("SELECT a.escort.id FROM Application a WHERE a.id = :id")
    Optional<Long> findEscortIdById(@Param("id") Long id);

    // 동행 매니저의 최근 위치 저장. 위치 컬럼만 바꾸는 조건부 UPDATE 다.
    // 엔티티를 불러와 고치면(더티체킹) 하이버네이트가 status 같은 안 바뀐 컬럼까지 함께 UPDATE 해서,
    // 같은 순간에 처리된 노쇼 취소·승인 등의 상태를 이전 값으로 되돌릴 수 있다. 그래서 위치 컬럼만 직접 바꾼다.
    // 지원이 ACCEPTED, 공고가 IN_PROGRESS 이며 귀가 완료 전일 때만 바뀐다 (0 이면 저장 불가).
    @Modifying
    @Query("""
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
            """)
    int updateLocation(@Param("id") Long id,
                       @Param("lat") BigDecimal lat,
                       @Param("lng") BigDecimal lng,
                       @Param("accuracy") BigDecimal accuracy,
                       @Param("updatedAt") Instant updatedAt);

    // 귀가 완료 시 최근 위치(위도·경도·정확도·갱신 시각)를 모두 삭제한다.
    @Modifying(flushAutomatically = true)
    @Query("""
            update Application a
               set a.lat = null, a.lng = null,
                   a.accuracy = null, a.locationUpdatedAt = null
             where a.id = :applicationId
            """)
    int clearLocation(@Param("applicationId") Long applicationId);

    // 공고 완료·취소·삭제 시 혹시 남아 있는 위치도 삭제한다.
    @Modifying(flushAutomatically = true)
    @Query("""
            update Application a
               set a.lat = null, a.lng = null,
                   a.accuracy = null, a.locationUpdatedAt = null
             where a.post.id = :postId
            """)
    int clearLocationsByPostId(@Param("postId") Long postId);

    // 회원 탈퇴·정지 시 취소할 지원 목록 조회
    @Query("""
            SELECT a
            FROM Application a
            JOIN FETCH a.post p
            WHERE a.escort.id = :escortId
              AND (
                  (a.status = :pending AND p.postStatus = :open)
                  OR
                  (a.status = :accepted AND p.postStatus = :matched)
              )
            """)
    List<Application> findAllCancelableByEscortId(
            @Param("escortId") Long escortId,
            @Param("pending") ApplicationStatus pending,
            @Param("accepted") ApplicationStatus accepted,
            @Param("open") PostStatus open,
            @Param("matched") PostStatus matched
    );

    // 회원 탈퇴 제한: 진행 중인 동행이 있는지 확인
    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Application a
            WHERE a.escort.id = :escortId
              AND a.status = :accepted
              AND a.post.postStatus = :inProgress
            """)
    boolean existsInProgressByEscortId(
            @Param("escortId") Long escortId,
            @Param("accepted") ApplicationStatus accepted,
            @Param("inProgress") PostStatus inProgress
    );

    @Modifying
    @Query("update Application a set " +
            "a.status=ApplicationStatus.REJECTED, a.activePostId=null, a.updatedAt=:now " +
            "where a.post=:post and a.status=ApplicationStatus.PENDING")
    int rejectAllByPost(@Param("post") Post post,
                        @Param("now") LocalDateTime now);

    @Query("select a from Application a " +
            "join a.post p " +
            "join a.escort e " +
            "where p.id=:postId and e.id=:escortId")
    List<Application> findByPostIdAndEscortId(@Param("postId") Long postId,
                                            @Param("escortId") Long escortId);
           
    // 이 공고에 승인(ACCEPTED)된 동행인인지
    boolean existsByPostIdAndEscortIdAndStatus(Long postId, Long escortId, ApplicationStatus status);
}