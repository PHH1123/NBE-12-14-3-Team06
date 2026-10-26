package com.back.nbe12142team06.domain.post.repository

import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.post.entity.PostStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface PostRepository : JpaRepository<Post, Long> {

    @Query(
        "SELECT p FROM Post p JOIN FETCH p.client " +
            // 선택 조건(null 가능)은 PostgreSQL 이 파라미터 타입을 추측하지 못해 오류가 나므로(bytea / could not determine data type) CAST 로 타입을 명시한다
            "WHERE (CAST(:keyword AS string) IS NULL OR p.title LIKE CONCAT('%', CAST(:keyword AS string), '%') " +
            "       OR p.hospitalName LIKE CONCAT('%', CAST(:keyword AS string), '%') " +
            "       OR p.region LIKE CONCAT('%', CAST(:keyword AS string), '%')) " +
            "AND (CAST(:region AS string) IS NULL OR p.region = :region) " +
            "AND (CAST(:dateFrom AS LocalDateTime) IS NULL OR p.escortStartAt >= :dateFrom) " +
            "AND (CAST(:dateTo AS LocalDateTime) IS NULL OR p.escortStartAt <= :dateTo) " +
            "AND (CAST(:minPay AS Integer) IS NULL OR p.hourlyPay >= :minPay) " +
            "AND (CAST(:maxPay AS Integer) IS NULL OR p.hourlyPay <= :maxPay) " +
            "AND ((:openOnly = TRUE AND p.postStatus = com.back.nbe12142team06.domain.post.entity.PostStatus.OPEN) " +
            "     OR (:openOnly = FALSE AND p.postStatus <> com.back.nbe12142team06.domain.post.entity.PostStatus.OPEN)) " +
            "AND EXISTS (" +
            "    SELECT 1 FROM Payment pay " +
            "    WHERE pay.post = p " +
            "      AND pay.paymentStatus = com.back.nbe12142team06.domain.payment.entity.PaymentStatus.DONE " +
            "      AND pay.id = (SELECT MAX(pay2.id) FROM Payment pay2 WHERE pay2.post = p)" +
            ")",
    )
    fun search(
        @Param("keyword") keyword: String?,
        @Param("region") region: String?,
        @Param("dateFrom") dateFrom: LocalDateTime?,
        @Param("dateTo") dateTo: LocalDateTime?,
        @Param("minPay") minPay: Int?,
        @Param("maxPay") maxPay: Int?,
        @Param("openOnly") openOnly: Boolean,
        pageable: Pageable,
    ): Page<Post>

    @Query(
        "SELECT p " +
            "FROM Post p JOIN FETCH p.client " +
            "WHERE EXISTS (" +
            "    SELECT 1 FROM Payment pay " +
            "    WHERE pay.post = p " +
            "      AND pay.paymentStatus = com.back.nbe12142team06.domain.payment.entity.PaymentStatus.DONE " +
            "      AND pay.id = (SELECT MAX(pay2.id) FROM Payment pay2 WHERE pay2.post = p)" +
            ") " +
            "ORDER BY p.id DESC",
    )
    fun findAllWithClient(pageable: Pageable): Page<Post>

    @Query(
        "SELECT p " +
            "FROM Post p JOIN FETCH p.client " +
            "WHERE p.id = :id",
    )
    fun findByIdWithClient(@Param("id") id: Long): Optional<Post>

    // 내가 작성한 공고 목록 — 결제 상태와 무관하게(READY·PARTIAL_CANCELED 등도) 본인 소유 공고는 전부 보여줘야 하므로
    // search()/findAllWithClient() 의 "결제 DONE만" 조건을 넣지 않는다.
    @Query(
        "SELECT p " +
            "FROM Post p JOIN FETCH p.client " +
            "WHERE p.client.id = :clientId " +
            "ORDER BY p.id DESC",
    )
    fun findAllByClientId(@Param("clientId") clientId: Long, pageable: Pageable): Page<Post>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :id")
    fun findByIdWithLock(@Param("id") id: Long): Post?

    //상태코드 만료처리
    fun findAllByPostStatusAndRecruitEndAtBefore(postStatus: PostStatus, dateTime: LocalDateTime): List<Post>

    // 회원 탈퇴·정지 시 취소할 공고 조회
    fun findAllByClientIdAndPostStatusIn(clientId: Long, statuses: Collection<PostStatus>): List<Post>

    fun existsByClientIdAndPostStatus(clientId: Long, postStatus: PostStatus): Boolean
}
