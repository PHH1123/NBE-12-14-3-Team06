package com.back.nbe12142team06.domain.user.service;

import com.back.nbe12142team06.DatabaseCleaner;
import com.back.nbe12142team06.domain.user.entity.EscortGradeHistory;
import com.back.nbe12142team06.domain.user.entity.EscortProfile;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.enums.EscortGrade;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.repository.EscortGradeHistoryRepository;
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class EscortGradeServiceTest {

    @Autowired
    private EscortService escortService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EscortProfileRepository escortProfileRepository;
    @Autowired
    private EscortGradeHistoryRepository escortGradeHistoryRepository;
    @Autowired
    private DatabaseCleaner databaseCleaner;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final AtomicInteger SEQ = new AtomicInteger();

    // escortGradeCheck 는 DB 의 모든 동행인을 처리하므로, 다른 테스트가 남긴 데이터와 섞이지 않게 비운다.
    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    // 회원과 동행 프로필을 저장한다. 완료/노쇼 횟수와 "저장된 등급"을 원하는 값으로 만든다.
    private EscortProfile saveEscort(int completedCount, int noShowCount, EscortGrade storedGrade) {
        int seq = SEQ.incrementAndGet();
        User user = userRepository.save(new User(
                "gradeEscort" + seq, "Password123!", "grade" + seq + "@test.com", "테스트",
                Role.ESCORT, Gender.MALE, LocalDate.of(1990, 1, 1),
                String.format("010-8881-%04d", seq), "서울"));

        EscortProfile escort = new EscortProfile(user, "소개", "은행", "예금주", "계좌번호");
        ReflectionTestUtils.setField(escort, "completedCount", completedCount);
        ReflectionTestUtils.setField(escort, "noShowCount", noShowCount);
        escort.updateGrade(storedGrade);
        return escortProfileRepository.save(escort);
    }
    // 등급 계산만 확인할 때 쓰는 프로필 (DB 에 저장하지 않는다. 회원은 생성자 조건을 채우기 위한 값)
    private EscortProfile newProfile() {
        User user = new User(
                "calcEscort", "Password123!", "calc@test.com", "테스트",
                Role.ESCORT, Gender.MALE, LocalDate.of(1990, 1, 1),
                "010-0000-0000", "서울");
        return new EscortProfile(user, "소개", "은행", "예금주", "계좌번호");
    }

    // 실제 서비스(PostService, ApplicationService)처럼 "트랜잭션 안에서 조회한 프로필"로 refreshGrade 를 호출한다.
    // 저장이 끝난 뒤의 객체를 그대로 넘기면 영속성 컨텍스트 밖의 객체라 변경 감지가 되지 않는다.
    private boolean refresh(EscortProfile escort) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                escortService.refreshGrade(escortProfileRepository.findById(escort.getUserId()).orElseThrow())));
    }

    private EscortGrade storedGradeOf(EscortProfile escort) {
        return escortProfileRepository.findById(escort.getUserId()).orElseThrow().getGrade();
    }

    private List<EscortGradeHistory> historiesOf(EscortProfile escort) {
        return escortGradeHistoryRepository.findAllByEscortProfileUserId(escort.getUserId());
    }

    /* ── 등급 계산 (DB 없이 엔티티만) ──────────────────────── */

    @Test
    @DisplayName("완료5회 +  노쇼0회 = 새싹")
    void 완료5회노쇼0회_새싹SPROUT(){
        EscortProfile escort = newProfile();
        ReflectionTestUtils.setField(escort, "completedCount", 5);

        EscortGrade grade = escort.calculateGrade();
        // 사용자와 동행 프로필을 저장한다 (기존 테스트의 준비 코드를 복사)
        // 횟수를 올려서 계산 등급이 달라지게 만든다
        // escortService.refreshGrade(프로필)을 호출한다
        // 반환값(true/false)과 이력 개수(findAllByEscortProfileUserId(id).size())를 확인한다
        assertThat(grade).isEqualTo(EscortGrade.SPROUT);

    }

    @ParameterizedTest(name = "완료 {0}회, 노쇼 {1}회 → {2}")
    @CsvSource({
            // 완료 횟수별 기본 등급 (경계값)
            "0,  0, SEED",
            "4,  0, SEED",
            "5,  0, SPROUT",
            "9,  0, SPROUT",
            "10, 0, FLOWER",
            "29, 0, FLOWER",
            "30, 0, EGGPLANT",
            // 노쇼 1회는 강등 없음
            "30, 1, EGGPLANT",
            // 노쇼 2회: 1단계 강등
            "30, 2, FLOWER",
            "10, 2, SPROUT",
            "5,  2, SEED",
            // 노쇼 3회 이상: 2단계 강등
            "30, 3, SPROUT",
            "10, 3, SEED",
            "30, 5, SPROUT",
            // 맨 아래 등급 밑으로는 내려가지 않는다
            "0,  3, SEED"
    })
    @DisplayName("완료/노쇼 횟수에 따라 등급이 계산된다")
    void 횟수별_등급_계산(int completedCount, int noShowCount, EscortGrade expected) {
        EscortProfile escort = newProfile();
        ReflectionTestUtils.setField(escort, "completedCount", completedCount);
        ReflectionTestUtils.setField(escort, "noShowCount", noShowCount);

        assertThat(escort.calculateGrade()).isEqualTo(expected);
    }

    @Test
    @DisplayName("새로 만든 동행인의 저장된 등급은 씨앗이다")
    void 새_동행인은_씨앗() {
        EscortProfile escort = newProfile();

        assertThat(escort.getGrade()).isEqualTo(EscortGrade.SEED);
    }

    /* ── 한 명 갱신 (refreshGrade) ─────────────────────────── */

    @Test
    @DisplayName("[refreshGrade] 계산 등급이 저장 등급과 다르면 등급을 바꾸고 이력을 남긴다")
    void 등급이_달라지면_갱신하고_이력을_남긴다() {
        EscortProfile escort = saveEscort(5, 0, EscortGrade.SEED);   // 완료 5회인데 저장 등급은 아직 씨앗
                                                                      // (saveEscort 안의 save() 가 끝나면 escort 는 영속성 컨텍스트 밖의 객체가 된다)

        // [처음 버전 - 실패함] save() 가 끝난 뒤의 객체(영속성 컨텍스트 밖)를 그대로 서비스에 넘김
        //   → refreshGrade 안의 updateGrade() 가 변경 감지 대상이 아니라서 DB 에 반영되지 않았다.
        // boolean changed = escortService.refreshGrade(escort);

        // [고친 버전] 같은 트랜잭션 안에서 findById 로 새로 조회한 객체를 넘김 (refresh() 도우미, 아래 참고)
        boolean changed = refresh(escort);

        assertThat(changed).isTrue();
        assertThat(storedGradeOf(escort)).isEqualTo(EscortGrade.SPROUT);

        List<EscortGradeHistory> histories = historiesOf(escort);
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getPreviousGrade()).isEqualTo(EscortGrade.SEED);
        assertThat(histories.get(0).getNewGrade()).isEqualTo(EscortGrade.SPROUT);
    }

    @Test
    @DisplayName("[refreshGrade] 노쇼로 등급이 내려가는 경우에도 이력이 남는다")
    void 노쇼로_강등되면_이력을_남긴다() {
        EscortProfile escort = saveEscort(10, 2, EscortGrade.FLOWER);   // 완료 10회지만 노쇼 2회 → 새싹이어야 함

        boolean changed = refresh(escort);

        assertThat(changed).isTrue();
        assertThat(storedGradeOf(escort)).isEqualTo(EscortGrade.SPROUT);

        List<EscortGradeHistory> histories = historiesOf(escort);
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getPreviousGrade()).isEqualTo(EscortGrade.FLOWER);
        assertThat(histories.get(0).getNewGrade()).isEqualTo(EscortGrade.SPROUT);
    }

    @Test
    @DisplayName("[refreshGrade] 등급이 같으면 아무것도 하지 않고 이력도 남기지 않는다")
    void 등급이_같으면_아무것도_안한다() {
        EscortProfile escort = saveEscort(5, 0, EscortGrade.SPROUT);   // 이미 맞는 등급

        boolean changed = refresh(escort);

        assertThat(changed).isFalse();
        assertThat(storedGradeOf(escort)).isEqualTo(EscortGrade.SPROUT);
        assertThat(historiesOf(escort)).isEmpty();
    }

    @Test
    @DisplayName("[refreshGrade] 같은 동행인에게 두 번 호출해도 이력은 한 번만 남는다")
    void 두번_호출해도_이력은_한번() {
        EscortProfile escort = saveEscort(10, 0, EscortGrade.SEED);

        boolean first = refresh(escort);
        boolean second = refresh(escort);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(historiesOf(escort)).hasSize(1);
    }

    /* ── 스케줄러 (escortGradeCheck) ───────────────────────── */

    @Test
    @DisplayName("[스케줄러] 등급이 어긋난 동행인만 고치고, 바뀐 인원 수를 반환한다")
    void 스케줄러는_어긋난_동행인만_고친다() {
        EscortProfile needsPromote = saveEscort(5, 0, EscortGrade.SEED);        // 승급 필요
        EscortProfile needsDemote = saveEscort(10, 3, EscortGrade.FLOWER);      // 강등 필요 (노쇼 3회 → 씨앗)
        EscortProfile alreadyOk = saveEscort(30, 0, EscortGrade.EGGPLANT);      // 이미 맞음

        int updated = escortService.escortGradeCheck();

        assertThat(updated).isEqualTo(2);
        assertThat(storedGradeOf(needsPromote)).isEqualTo(EscortGrade.SPROUT);
        assertThat(storedGradeOf(needsDemote)).isEqualTo(EscortGrade.SEED);
        assertThat(storedGradeOf(alreadyOk)).isEqualTo(EscortGrade.EGGPLANT);

        assertThat(historiesOf(needsPromote)).hasSize(1);
        assertThat(historiesOf(needsDemote)).hasSize(1);
        assertThat(historiesOf(alreadyOk)).isEmpty();
    }

    @Test
    @DisplayName("[스케줄러] 모두 맞으면 0명이고, 한 번 더 돌려도 변화가 없다")
    void 스케줄러를_다시_돌려도_변화가_없다() {
        saveEscort(5, 0, EscortGrade.SEED);

        int first = escortService.escortGradeCheck();
        int second = escortService.escortGradeCheck();

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    @DisplayName("[스케줄러] 동행인이 없어도 오류 없이 0명을 반환한다")
    void 동행인이_없어도_오류가_없다() {
        assertThat(escortService.escortGradeCheck()).isZero();
    }
}
