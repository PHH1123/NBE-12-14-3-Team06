package com.back.nbe12142team06.domain.payment.repository;

import com.back.nbe12142team06.domain.payment.entity.Payment;
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus;
import com.back.nbe12142team06.domain.payment.repository.dto.PaymentDailyAnalyticsDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select pay " +
            "from Payment pay " +
            "join Post post on pay.post=post " +
            "join User u on post.client=u " +
            "where post.id=:postId and u.id=:userId and pay.paymentStatus=PaymentStatus.READY")
    Optional<Payment> findByPostIdAndUserIdAndReady(@Param("postId") Long postId,
                                                    @Param("userId") Long userId);


    @Query("select pay " +
            "from Payment pay " +
            "join Post post on pay.post=post " +
            "join User u on post.client=u " +
            "where u.id=:userId")
    List<Payment> findAllByUserId(@Param("userId") Long userId);

    @Query("select pay " +
            "from Payment pay " +
            "join Post post on pay.post=post " +
            "where post.id=:postId and pay.paymentStatus in ('DONE', 'PARTIAL_CANCELED') " +
            "order by pay.balanceAmount desc")
    List<Payment> findSuccessPayByPostId(@Param("postId") Long postId);

    @Query("select pay " +
            "from Payment pay " +
            "join fetch pay.post post " +
            "join fetch post.client c " +
            "where pay.id=:paymentId")
    Optional<Payment> findByIdFetchJoin(@Param("paymentId") Long paymentId);

    @Modifying(clearAutomatically = true)
    @Query("update Payment p set p.paymentStatus='DONE' where p.post.id=:postId")
    int testStatusDone(@Param("postId") Long postId);

    Optional<Payment> findByPostId(Long postId);

    @Query("select p from Payment p where p.paymentStatus=PaymentStatus.DELETED")
    List<Payment> findDeletedAll();

    @Query("select p from Payment p join p.post.client c on c.id=:userId where p.paymentStatus=PaymentStatus.READY")
    List<Payment> findNotPaidByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("update Payment p set p.paymentStatus=PaymentStatus.IN_PROGRESS where p.id=:paymentId and p.paymentStatus=PaymentStatus.READY")
    int paymentInProgress(@Param("paymentId") Long paymentId);

    List<Payment> findByPostIdAndPaymentStatus(Long postId, PaymentStatus paymentStatus);

    @Query("select cast(p.approvedAt as LocalDate), sum(p.amount) paidAmount, count(p) counts " +
            "from Payment p " +
            "where p.approvedAt >= :startDate and p.approvedAt <= :endDate " +
            "and p.paymentStatus in (PaymentStatus.DONE, PaymentStatus.PARTIAL_CANCELED) " +
            "group by cast(p.approvedAt as LocalDate) " +
            "order by cast(p.approvedAt as LocalDate)")
    List<PaymentDailyAnalyticsDto> findDailyAnalytics(@Param("startDate") LocalDateTime startDate,
                                                      @Param("endDate") LocalDateTime endDate);

    @Query("select p " +
            "from Payment p " +
            "where p.approvedAt >= :startDate and p.approvedAt <= :endDate " +
            "and p.paymentStatus in (PaymentStatus.DONE, PaymentStatus.PARTIAL_CANCELED)")
    List<Payment> findAnalyticsByPeriod(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    @Query("select pay " +
            "from Payment pay " +
            "join fetch pay.post post " +
            "join fetch post.client " +
            "where pay.approvedAt >= :startDate and pay.approvedAt <= :endDate")
    Page<Payment> findPaymentsForAdmin(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate,
                                       Pageable pageable);
}
