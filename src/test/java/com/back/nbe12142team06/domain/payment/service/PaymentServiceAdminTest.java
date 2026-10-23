package com.back.nbe12142team06.domain.payment.service;

import com.back.nbe12142team06.domain.payment.dto.PaymentAdminResponse;
import com.back.nbe12142team06.domain.payment.dto.PaymentAnalyticsResponse;
import com.back.nbe12142team06.domain.payment.entity.Payment;
import com.back.nbe12142team06.domain.payment.entity.PaymentStatus;
import com.back.nbe12142team06.domain.payment.repository.PaymentRepository;
import com.back.nbe12142team06.domain.payment.repository.dto.PaymentDailyAnalyticsDto;
import com.back.nbe12142team06.domain.post.entity.Post;
import com.back.nbe12142team06.domain.post.repository.PostRepository;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PaymentServiceAdminTest {

    @Autowired
    EntityManager em;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserRepository userRepository;

    private Payment createPayment(Post post, PaymentStatus status, LocalDateTime approvedAt, BigDecimal hours, BigDecimal mulBalanceAmount, Long id) {
        int hourlyPaySnapshot = 15_000;
        BigDecimal amount = hours.multiply(BigDecimal.valueOf(hourlyPaySnapshot));
        Payment payment = new Payment(
                id, amount, hourlyPaySnapshot, hours, post,
                "CARD", status, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), approvedAt,
                mulBalanceAmount.multiply(amount)
        );
        return payment;
    }

    private Payment createPayment(Post post, PaymentStatus status) {
        return createPayment(post, status, LocalDateTime.now().minusDays(2), BigDecimal.TEN, BigDecimal.ONE, 1L);
    }

    private Payment createPayment(Post post, PaymentStatus status, LocalDateTime approvedAt, BigDecimal hours, Long id) {
        return createPayment(post, status, approvedAt, hours, BigDecimal.ONE, id);
    }

    private Post createPost(User client, Long id, int hour) {
        String title = "정형외과 동행 구합니다";
        String content = "무릎 수술 후 검진 예약이 있어 동행인이 필요합니다.";
        String postRegion = "서울";
        String hospitalName = "서울성모병원";
        String hospitalAddress = "서울 서초구 반포대로 222";
        BigDecimal hospitalLat = BigDecimal.valueOf(37.5012743);
        BigDecimal hospitalLng = BigDecimal.valueOf(127.0051893);
        String pickupAddress = "서울 서초구 잠원동 10-1";
        BigDecimal pickupLat = BigDecimal.valueOf(37.5160000);
        BigDecimal pickupLng = BigDecimal.valueOf(127.0200000);
        int hourlyPay = 15_000;
        LocalDateTime recruitStartAt = LocalDateTime.now().plusDays(1);
        LocalDateTime recruitEndAt = LocalDateTime.now().plusDays(6);
        LocalDateTime escortStartAt = LocalDateTime.now().plusDays(7);
        LocalDateTime escortEndAt = LocalDateTime.now().plusDays(7).plusHours(hour);

        Post post = Post.builder()
                .id(id)
                .title(title)
                .content(content)
                .region(postRegion)
                .hospitalName(hospitalName)
                .hospitalAddress(hospitalAddress)
                .hospitalLat(hospitalLat)
                .hospitalLng(hospitalLng)
                .pickupAddress(pickupAddress)
                .pickupLat(pickupLat)
                .pickupLng(pickupLng)
                .hourlyPay(hourlyPay)
                .recruitStartAt(recruitStartAt)
                .recruitEndAt(recruitEndAt)
                .escortStartAt(escortStartAt)
                .escortEndAt(escortEndAt)
                .client(client)
                .build();

        return post;
    }

    private Post createPost(User client, Long id) {
        return createPost(client, id, 10);
    }

    private Post createPost(User client) {
        return createPost(client, 1L, 10);
    }

    private User createClient(boolean addId) {
        User client = User.builder()
                .username("aclient01")
                .password("password1!")
                .email("aclient01@email.com")
                .name("의뢰인")
                .role(Role.CLIENT)
                .gender(Gender.MALE)
                .birthDate(LocalDate.of(1960, 11, 11))
                .phoneNum("010-1234-1239")
                .region("서울")
                .build();
        if (addId) {
            try {
                Field clientIdField = client.getClass().getDeclaredField("id");
                clientIdField.setAccessible(true);
                clientIdField.set(client, 1L);
            } catch (Exception e) {
            }
        }
        return client;
    }

    private User createClient() {
        return createClient(true);
    }

    private User createEscort() {
        User escort = User.builder()
                .username("escort01")
                .password("password1!")
                .email("escort01@email.com")
                .name("동행매니저")
                .role(Role.ESCORT)
                .gender(Gender.MALE)
                .birthDate(LocalDate.of(2000, 1, 1))
                .phoneNum("010-5678-5678")
                .region("서울")
                .build();
        try {
            Field escortIdField = escort.getClass().getDeclaredField("id");
            escortIdField.setAccessible(true);
            escortIdField.set(escort, 2L);
        } catch (Exception e) {
        }
        return escort;
    }

    @Test
    @DisplayName("[PaymentService] 관리자 일별 결제 정보 조회")
    void dailyPayment() {
        User client = createClient(false);
        userRepository.save(client);
        Post post = createPost(client, null);
        postRepository.save(post);

        Payment payment1 = createPayment(post, PaymentStatus.DONE,
                LocalDateTime.now().minusDays(8), BigDecimal.TEN, null);
        Payment payment2 = createPayment(post, PaymentStatus.DONE,
                LocalDateTime.now().minusDays(7), BigDecimal.ONE, null);
        Payment payment3 = createPayment(post, PaymentStatus.DONE,
                LocalDateTime.now().minusDays(6), BigDecimal.TWO, null);
        Payment payment4 = createPayment(post, PaymentStatus.PARTIAL_CANCELED,
                LocalDateTime.now().minusDays(6), BigDecimal.valueOf(5L), null);
        Payment payment5 = createPayment(post, PaymentStatus.DONE,
                LocalDateTime.now().minusDays(5), BigDecimal.valueOf(3L), null);

        paymentRepository.save(payment1);
        paymentRepository.save(payment2);
        paymentRepository.save(payment3);
        paymentRepository.save(payment4);
        paymentRepository.save(payment5);


        List<PaymentDailyAnalyticsDto> result =
                paymentService.getDailyPaymentAnalytics(LocalDate.now().minusDays(7), LocalDate.now());

        assertThat(result.get(0).paidAmount()).isEqualByComparingTo(BigDecimal.valueOf(15_000L));
        assertThat(result.get(1).paidAmount()).isEqualByComparingTo(BigDecimal.valueOf(105_000L));
        assertThat(result.get(2).paidAmount()).isEqualByComparingTo(BigDecimal.valueOf(45_000L));
    }

    @Test
    @DisplayName("[PaymentService] 관리자 기간별 결제 및 환불 금액 조회")
    void paymentByPeriod() {
        User client = createClient(false);
        userRepository.save(client);
        Post post = createPost(client, null);
        postRepository.save(post);

        // 150_000
        Payment payment1 = createPayment(post, PaymentStatus.DONE,
                LocalDateTime.now().minusDays(8), BigDecimal.TEN, null);
        // 15_000
        Payment payment2 = createPayment(post, PaymentStatus.DONE,
                LocalDateTime.now().minusDays(7), BigDecimal.ONE, null);
        // 30_000
        Payment payment3 = createPayment(post, PaymentStatus.DONE,
                LocalDateTime.now().minusDays(6), BigDecimal.TWO, null);
        // 75_000 - 37_500 = 37_500
        Payment payment4 = createPayment(post, PaymentStatus.PARTIAL_CANCELED,
                LocalDateTime.now().minusDays(6), BigDecimal.valueOf(5L), BigDecimal.valueOf(0.5), null);
        // 45_000 - 9_000 = 36_000
        Payment payment5 = createPayment(post, PaymentStatus.PARTIAL_CANCELED,
                LocalDateTime.now().minusDays(5), BigDecimal.valueOf(3L), BigDecimal.valueOf(0.8), null);

        paymentRepository.save(payment1);
        paymentRepository.save(payment2);
        paymentRepository.save(payment3);
        paymentRepository.save(payment4);
        paymentRepository.save(payment5);

        List<Payment> payments =
                paymentService.getPaymentAnalyticsByPeriod(LocalDate.now().minusDays(7), LocalDate.now());
        PaymentAnalyticsResponse result = new PaymentAnalyticsResponse(payments);

        assertThat(result.paidAmount()).isEqualTo(118_500L);
        assertThat(result.canceledAmount()).isEqualTo(46_500L);
        assertThat(result.counts()).isEqualTo(4L);
    }

    @Test
    @DisplayName("[PaymentService] 관리자 기간별 결제 목록 조회")
    void paymentList() {
        User client = createClient(false);
        userRepository.save(client);

        Post post1 = createPost(client, null, 8);
        Post post2 = createPost(client, null, 1);
        Post post3 = createPost(client, null, 2);
        Post post4 = createPost(client, null, 5);
        Post post5 = createPost(client, null, 3);

        postRepository.save(post1);
        post2 = postRepository.save(post2);
        postRepository.save(post3);
        postRepository.save(post4);
        post5 = postRepository.save(post5);

        Payment payment1 = createPayment(post1, PaymentStatus.DONE,
                LocalDateTime.now().minusDays(8), BigDecimal.TEN, null);
        Payment payment2 = createPayment(post2, PaymentStatus.DONE,
                LocalDateTime.now().minusDays(7), BigDecimal.ONE, null);
        Payment payment3 = createPayment(post3, PaymentStatus.DONE,
                LocalDateTime.now().minusDays(6), BigDecimal.TWO, null);
        Payment payment4 = createPayment(post4, PaymentStatus.PARTIAL_CANCELED,
                LocalDateTime.now().minusDays(6), BigDecimal.valueOf(5L), BigDecimal.valueOf(0.5), null);
        Payment payment5 = createPayment(post5, PaymentStatus.PARTIAL_CANCELED,
                LocalDateTime.now().minusDays(5), BigDecimal.valueOf(3L), BigDecimal.valueOf(0.8), null);

        paymentRepository.save(payment1);
        paymentRepository.save(payment2);
        paymentRepository.save(payment3);
        paymentRepository.save(payment4);
        paymentRepository.save(payment5);

        Pageable pageable = PageRequest.of(0, 20, Sort.Direction.DESC, "id");

        Page<Payment> paymentPage =
                paymentService.getPaymentsForAdmin(LocalDate.now().minusDays(7), LocalDate.now(), pageable);

        List<PaymentAdminResponse> result = paymentPage.map(PaymentAdminResponse::new).getContent();

        assertThat(result.size()).isEqualTo(4);
        assertThat(result.getFirst().amount()).isEqualTo(45_000);
        assertThat(result.getFirst().postId()).isEqualTo(post5.getId());
        assertThat(result.getLast().amount()).isEqualTo(15_000);
        assertThat(result.getLast().postId()).isEqualTo(post2.getId());
    }

    @Test
    @DisplayName("[PaymentService] 관리자 결제 상태 변경")
    void updateStatus() {
        User client = createClient(false);
        client = userRepository.save(client);

        Post post1 = createPost(client, null, 8);

        postRepository.save(post1);

        Payment payment = createPayment(post1, PaymentStatus.DONE,
                LocalDateTime.now().minusDays(8), BigDecimal.TEN, null);

        payment = paymentRepository.save(payment);

        paymentService.updateStatusForAdmin(payment.getId(), PaymentStatus.CANCELED);

        em.flush();
        em.clear();

        payment = paymentService.findById(client.getId(), payment.getId());

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
    }
}
