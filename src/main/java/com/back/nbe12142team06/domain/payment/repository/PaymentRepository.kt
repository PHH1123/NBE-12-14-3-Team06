package com.back.nbe12142team06.domain.payment.repository

import com.back.nbe12142team06.domain.payment.entity.Payment
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus
import com.back.nbe12142team06.domain.payment.repository.dto.PaymentDailyAnalyticsDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.*

interface PaymentRepository : JpaRepository<Payment, Long> {
    @Query(
        "select pay " +
                "from Payment pay " +
                "join Post post on pay.post=post " +
                "join User u on post.client=u " +
                "where post.id=:postId and u.id=:userId and pay.paymentStatus=PaymentStatus.READY"
    )
    fun findByPostIdAndUserIdAndReady(
        @Param("postId") postId: Long,
        @Param("userId") userId: Long
    ): Payment?


    @Query(
        "select pay " +
                "from Payment pay " +
                "join Post post on pay.post=post " +
                "join User u on post.client=u " +
                "where u.id=:userId"
    )
    fun findAllByUserId(@Param("userId") userId: Long): List<Payment>

    @Query(
        "select pay " +
                "from Payment pay " +
                "join Post post on pay.post=post " +
                "where post.id=:postId and pay.paymentStatus in ('DONE', 'PARTIAL_CANCELED') " +
                "order by pay.balanceAmount desc"
    )
    fun findSuccessPayByPostId(@Param("postId") postId: Long): List<Payment>

    @Query(
        "select pay " +
                "from Payment pay " +
                "join fetch pay.post post " +
                "join fetch post.client c " +
                "where pay.id=:paymentId"
    )

    fun findByIdFetchJoin(@Param("paymentId") paymentId: Long): Payment?

    @Modifying(clearAutomatically = true)
    @Query("update Payment p set p.paymentStatus='DONE', p.paymentKey=:paymentKey where p.post.id=:postId")
    fun testStatusDoneAndPutPaymentKey(@Param("postId") postId: Long,
                                       @Param("paymentKey") paymentKey: String): Int

    fun findByPostId(postId: Long): Payment?

    @Query("select p from Payment p where p.paymentStatus=PaymentStatus.DELETED")
    fun findDeletedAll(): List<Payment>

    @Query(
        "select p from Payment p " +
                "join p.post.client c on c.id=:userId " +
                "where p.paymentStatus=PaymentStatus.READY"
    )
    fun findNotPaidByUserId(@Param("userId") userId: Long): List<Payment>

    @Modifying
    @Query(
        "update Payment p " +
                "set p.paymentStatus=PaymentStatus.IN_PROGRESS " +
                "where p.id=:paymentId and p.paymentStatus=PaymentStatus.READY"
    )
    fun paymentInProgress(@Param("paymentId") paymentId: Long): Int

    fun findByPostIdAndPaymentStatus(postId: Long, paymentStatus: PaymentStatus): List<Payment>

    @Query(
        "select cast(p.approvedAt as LocalDate), sum(p.amount) paidAmount, count(p) counts " +
                "from Payment p " +
                "where p.approvedAt >= :startDate and p.approvedAt < :endDate " +
                "and p.paymentStatus in (PaymentStatus.DONE, PaymentStatus.PARTIAL_CANCELED) " +
                "group by cast(p.approvedAt as LocalDate) " +
                "order by cast(p.approvedAt as LocalDate)"
    )
    fun findDailyAnalytics(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<PaymentDailyAnalyticsDto>

    @Query(
        "select p " +
                "from Payment p " +
                "where p.approvedAt >= :startDate and p.approvedAt < :endDate " +
                "and p.paymentStatus in (PaymentStatus.DONE, PaymentStatus.PARTIAL_CANCELED)"
    )
    fun findAnalyticsByPeriod(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<Payment>

    @Query(
        "select pay " +
                "from Payment pay " +
                "join fetch pay.post post " +
                "join fetch post.client " +
                "where pay.approvedAt >= :startDate and pay.approvedAt < :endDate"
    )
    fun findPaymentsForAdmin(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime,
        pageable: Pageable
    ): Page<Payment>

    // 회원 탈퇴 제한 - 동행 완료 후 미결제 추가 결제가 있는지 확인
    @Query(
        """
    SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
    FROM Payment p
    WHERE p.post.client.id = :userId
      AND p.paymentStatus = PaymentStatus.READY
      AND p.post.postStatus = PostStatus.COMPLETED
    
    """
    )
    fun existsUnpaidExtraByUserId(@Param("userId") userId: Long): Boolean
}
