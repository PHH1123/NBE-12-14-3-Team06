package com.back.nbe12142team06.domain.settlement.service;

import com.back.nbe12142team06.domain.application.entity.Application;
import com.back.nbe12142team06.domain.application.enums.ApplicationStatus;
import com.back.nbe12142team06.domain.application.repository.ApplicationRepository;
import com.back.nbe12142team06.domain.post.entity.Post;
import com.back.nbe12142team06.domain.post.entity.PostStatus;
import com.back.nbe12142team06.domain.post.repository.PostRepository;
import com.back.nbe12142team06.domain.settlement.client.SettlementClient;
import com.back.nbe12142team06.domain.settlement.client.SettlementClientResponse;
import com.back.nbe12142team06.domain.settlement.dto.SettlementAdminResponse;
import com.back.nbe12142team06.domain.settlement.dto.SettlementAnalyticsResponse;
import com.back.nbe12142team06.domain.settlement.entity.Settlement;
import com.back.nbe12142team06.domain.settlement.entity.SettlementStatus;
import com.back.nbe12142team06.domain.settlement.repository.SettlementRepository;
import com.back.nbe12142team06.domain.user.entity.EscortProfile;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SettlementServiceAdminTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private SettlementService settlementService;
    @Autowired
    private SettlementRepository settlementRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EscortProfileRepository escortProfileRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @MockitoBean
    private SettlementClient settlementClient;

    private Settlement createSettlement(Long id, int payoutAmount, int platformFee, int penaltyAmount,
                                        SettlementStatus status, LocalDate settledDate,
                                        Application application, User escort) {
        return Settlement.builder()
                .id(id)
                .payoutAmount(payoutAmount)
                .platformFee(platformFee)
                .penaltyAmount(penaltyAmount)
                .settlementStatus(status)
                .settledDate(settledDate)
                .application(application)
                .escort(escort)
                .build();
    }

    private Settlement createSettlement(int payoutAmount, int platformFee,
                                        int penaltyAmount, SettlementStatus status) {
        return createSettlement(null, payoutAmount, platformFee, penaltyAmount,
                status, LocalDate.now(), null, null);
    }

    private Settlement createSettlement(int payoutAmount, SettlementStatus status, User escort) {
        return createSettlement(null, payoutAmount, (int) (payoutAmount / 9.0), 0,
                status, LocalDate.now(), null, escort);
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

        return Post.builder()
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
                .postStatus(PostStatus.COMPLETED)
                .client(client)
                .build();
    }

    private Application createApplication(User escort, Post post) {
        return Application.builder()
                .id(null)
                .post(post)
                .escort(escort)
                .status(ApplicationStatus.ACCEPTED)
                .acceptedPostId(post.getId())
                .activePostId(post.getId())
                .build();
    }

    private User createClient() {
        return User.builder()
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
    }

    private User createEscort() {
        return User.builder()
                .username("aescort01")
                .password("password1!")
                .email("aescort01@email.com")
                .name("동행매니저")
                .role(Role.ESCORT)
                .gender(Gender.MALE)
                .birthDate(LocalDate.of(2000, 1, 1))
                .phoneNum("010-5678-5679")
                .region("서울")
                .build();
    }

    private EscortProfile createEscortProfile(User escort) {
        return new EscortProfile(
                escort,
                "동행 매니저입니다.",
                "오픈은행",
                "동행매니저",
                "123-000000-123");
    }

    @Test
    @DisplayName("[SettlementService] 관리자 기간별 정산 금액 및 건수 조회")
    void getSettlementByPeriod() {
        // 정산 완료 - 지급 135_000 / 수수료 15_000 / 기부 0
        Settlement settlement1 = createSettlement(135_000, 15_000, 0, SettlementStatus.COMPLETED);
        // 정산 완료 - 지급 121_500 / 수수료 15_000 / 기부 13_500
        Settlement settlement2 = createSettlement(121_500, 15_000, 13_500, SettlementStatus.COMPLETED);
        // 정산 전 - 미정산 135_000
        Settlement settlement3 = createSettlement(135_000, 15_000, 0, SettlementStatus.PENDING);
        // 정산 실패 - 미정산 90_000
        Settlement settlement4 = createSettlement(90_000, 10_000, 0, SettlementStatus.FAILED);
        // 정산 중 - 오류 45_000
        Settlement settlement5 = createSettlement(45_000, 5_000, 0, SettlementStatus.PROCESSING);

        settlementRepository.save(settlement1);
        settlementRepository.save(settlement2);
        settlementRepository.save(settlement3);
        settlementRepository.save(settlement4);
        settlementRepository.save(settlement5);

        List<Settlement> settlements =
                settlementService.getAnalytics(LocalDate.now().minusDays(7), LocalDate.now().plusDays(1));
        SettlementAnalyticsResponse result = new SettlementAnalyticsResponse(settlements);

        assertThat(result.totalSettlementAmount()).isEqualTo(526_500L);
        assertThat(result.settlementCount()).isEqualTo(5L);
        assertThat(result.totalSettledAmount()).isEqualTo(256_500L);
        assertThat(result.settledCount()).isEqualTo(2L);
        assertThat(result.totalUnSettledAmount()).isEqualTo(225_000L);
        assertThat(result.unSettledCount()).isEqualTo(2L);
        assertThat(result.totalPlatformFee()).isEqualTo(30_000L);
        assertThat(result.platformFeeCount()).isEqualTo(2L);
        assertThat(result.totalDonationAmount()).isEqualTo(13_500L);
        assertThat(result.donationCount()).isEqualTo(1L);
        assertThat(result.totalErrorSettlementAmount()).isEqualTo(45_000L);
        assertThat(result.errorSettlementCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("[SettlementService] 관리자 기간별 정산 목록 조회")
    void settlementList() {
        User escort = createEscort();
        escort = userRepository.save(escort);

        Settlement settlement1 = createSettlement(135_000, SettlementStatus.COMPLETED, escort);
        Settlement settlement2 = createSettlement(121_500, SettlementStatus.PENDING, escort);
        Settlement settlement3 = createSettlement(90_000, SettlementStatus.FAILED, escort);
        Settlement settlement4 = createSettlement(45_000, SettlementStatus.PROCESSING, escort);

        settlement1 = settlementRepository.save(settlement1);
        settlementRepository.save(settlement2);
        settlementRepository.save(settlement3);
        settlement4 = settlementRepository.save(settlement4);

        Pageable pageable = PageRequest.of(0, 20, Sort.Direction.DESC, "id");

        Page<Settlement> settlementPage =
                settlementService.getSettlementForAdmin(LocalDate.now().minusDays(7), LocalDate.now().plusDays(1), pageable);

        List<SettlementAdminResponse> result = settlementPage.map(SettlementAdminResponse::new).getContent();

        assertThat(result.size()).isEqualTo(4);
        assertThat(result.getFirst().id()).isEqualTo(settlement4.getId());
        assertThat(result.getFirst().payoutAmount()).isEqualTo(45_000);
        assertThat(result.getFirst().status()).isEqualTo(SettlementStatus.PROCESSING);
        assertThat(result.getFirst().name()).isEqualTo("동행매니저");
        assertThat(result.getLast().id()).isEqualTo(settlement1.getId());
        assertThat(result.getLast().payoutAmount()).isEqualTo(135_000);
        assertThat(result.getLast().status()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(result.getLast().userId()).isEqualTo(escort.getId());
    }

    // deprecated - PROCESSING 상태도 정산 되도록 하면 관리자의 부주의 이중 정산의 위험성이 있어 삭제 예정
//    @Test
//    @DisplayName("[SettlementService] 관리자 정산 요청 - PROCESSING 상태여도 정산된다")
//    void requestForAdmin() {
//        User client = createClient();
//        User escort = createEscort();
//        userRepository.save(client);
//        escort = userRepository.save(escort);
//        escortProfileRepository.save(createEscortProfile(escort));
//
//        Post post = postRepository.save(createPost(client, null, 10));
//        Application application = applicationRepository.save(createApplication(escort, post));
//
//        Settlement settlement = settlementRepository.save(
//                createSettlement(null, 135_000, 15_000, 0,
//                        SettlementStatus.PROCESSING, LocalDate.now(), application, escort));
//
//        doReturn(new SettlementClientResponse("123-000000-123", "동행매니저", 135_000))
//                .when(settlementClient).settlementRequest(any());
//
//        settlementService.requestForAdmin(settlement.getId());
//
//        em.flush();
//        em.clear();
//
//        Settlement result = settlementRepository.findById(settlement.getId()).get();
//
//        assertThat(result.getSettlementStatus()).isEqualTo(SettlementStatus.COMPLETED);
//    }
}
