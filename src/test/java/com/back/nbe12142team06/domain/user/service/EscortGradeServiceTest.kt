package com.back.nbe12142team06.domain.user.service

import com.back.nbe12142team06.DatabaseCleaner
import com.back.nbe12142team06.domain.user.entity.EscortGradeHistory
import com.back.nbe12142team06.domain.user.entity.EscortProfile
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.EscortGrade
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import com.back.nbe12142team06.domain.user.repository.EscortGradeHistoryRepository
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("test")
class EscortGradeServiceTest {

    @Autowired
    private lateinit var escortService: EscortService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var escortProfileRepository: EscortProfileRepository

    @Autowired
    private lateinit var escortGradeHistoryRepository: EscortGradeHistoryRepository

    @Autowired
    private lateinit var databaseCleaner: DatabaseCleaner

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    // escortGradeCheck 는 DB 의 모든 동행인을 처리하므로, 다른 테스트가 남긴 데이터와 섞이지 않게 비운다.
    @BeforeEach
    fun setUp() {
        databaseCleaner.clean()
    }

    @AfterEach
    fun tearDown() {
        databaseCleaner.clean()
    }

    // 회원과 동행 프로필을 저장한다. 완료/노쇼 횟수와 "저장된 등급"을 원하는 값으로 만든다.
    private fun saveEscort(completedCount: Int, noShowCount: Int, storedGrade: EscortGrade): EscortProfile {
        val seq = SEQ.incrementAndGet()
        val user = userRepository.save(
            User(
                username = "gradeEscort$seq",
                password = "Password123!",
                email = "grade$seq@test.com",
                name = "테스트",
                role = Role.ESCORT,
                gender = Gender.MALE,
                birthDate = LocalDate.of(1990, 1, 1),
                phoneNum = "010-8881-%04d".format(seq),
                region = "서울",
            )
        )

        val escort = EscortProfile(user, "소개", "은행", "예금주", "계좌번호").withCounts(completedCount, noShowCount)
        escort.updateGrade(storedGrade)
        return escortProfileRepository.save(escort)
    }

    // 등급 계산만 확인할 때 쓰는 프로필 (DB 에 저장하지 않는다. 회원은 생성자 조건을 채우기 위한 값)
    private fun newProfile(): EscortProfile {
        val user = User(
            username = "calcEscort",
            password = "Password123!",
            email = "calc@test.com",
            name = "테스트",
            role = Role.ESCORT,
            gender = Gender.MALE,
            birthDate = LocalDate.of(1990, 1, 1),
            phoneNum = "010-0000-0000",
            region = "서울",
        )
        return EscortProfile(user, "소개", "은행", "예금주", "계좌번호")
    }

    // 완료/노쇼 횟수는 setter 가 없어서 리플렉션으로 직접 넣는다
    private fun EscortProfile.withCounts(completedCount: Int, noShowCount: Int = 0) = apply {
        ReflectionTestUtils.setField(this, "completedCount", completedCount)
        ReflectionTestUtils.setField(this, "noShowCount", noShowCount)
    }

    // 실제 서비스(PostService, ApplicationService)처럼 "트랜잭션 안에서 조회한 프로필"로 refreshGrade 를 호출한다.
    // 저장이 끝난 뒤의 객체를 그대로 넘기면 영속성 컨텍스트 밖의 객체라 변경 감지가 되지 않는다.
    private fun refresh(escort: EscortProfile): Boolean =
        transactionTemplate.execute {
            escortService.refreshGrade(escortProfileRepository.findByIdOrNull(escort.requiredId)!!)
        }

    private fun storedGradeOf(escort: EscortProfile): EscortGrade =
        escortProfileRepository.findByIdOrNull(escort.requiredId)!!.grade

    private fun historiesOf(escort: EscortProfile): List<EscortGradeHistory> =
        escortGradeHistoryRepository.findAllByEscortProfileUserId(escort.requiredId)

    /* ── 등급 계산 (DB 없이 엔티티만) ──────────────────────── */
    @Test
    @DisplayName("완료5회 +  노쇼0회 = 새싹")
    fun 완료5회노쇼0회_새싹SPROUT() {
        val escort = newProfile().withCounts(completedCount = 5)

        assertThat(escort.calculateGrade()).isEqualTo(EscortGrade.SPROUT)
    }

    @ParameterizedTest(name = "완료 {0}회, 노쇼 {1}회 → {2}")
    @CsvSource(
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
        "0,  3, SEED",
    )
    @DisplayName("완료/노쇼 횟수에 따라 등급이 계산된다")
    fun 횟수별_등급_계산(completedCount: Int, noShowCount: Int, expected: EscortGrade) {
        val escort = newProfile().withCounts(completedCount, noShowCount)

        assertThat(escort.calculateGrade()).isEqualTo(expected)
    }

    @Test
    @DisplayName("새로 만든 동행인의 저장된 등급은 씨앗이다")
    fun 새_동행인은_씨앗() {
        val escort = newProfile()

        assertThat(escort.grade).isEqualTo(EscortGrade.SEED)
    }

    /* ── 한 명 갱신 (refreshGrade) ─────────────────────────── */
    @Test
    @DisplayName("[refreshGrade] 계산 등급이 저장 등급과 다르면 등급을 바꾸고 이력을 남긴다")
    fun 등급이_달라지면_갱신하고_이력을_남긴다() {
        val escort = saveEscort(5, 0, EscortGrade.SEED) // 완료 5회인데 저장 등급은 아직 씨앗

        // (saveEscort 안의 save() 가 끝나면 escort 는 영속성 컨텍스트 밖의 객체가 된다)

        // [처음 버전 - 실패함] save() 가 끝난 뒤의 객체(영속성 컨텍스트 밖)를 그대로 서비스에 넘김
        //   → refreshGrade 안의 updateGrade() 가 변경 감지 대상이 아니라서 DB 에 반영되지 않았다.
        // val changed = escortService.refreshGrade(escort)

        // [고친 버전] 같은 트랜잭션 안에서 findById 로 새로 조회한 객체를 넘김 (refresh() 도우미, 위 참고)
        val changed = refresh(escort)

        assertThat(changed).isTrue()
        assertThat(storedGradeOf(escort)).isEqualTo(EscortGrade.SPROUT)

        val histories = historiesOf(escort)
        assertThat(histories).hasSize(1)
        assertThat(histories[0].previousGrade).isEqualTo(EscortGrade.SEED)
        assertThat(histories[0].newGrade).isEqualTo(EscortGrade.SPROUT)
    }

    @Test
    @DisplayName("[refreshGrade] 노쇼로 등급이 내려가는 경우에도 이력이 남는다")
    fun 노쇼로_강등되면_이력을_남긴다() {
        val escort = saveEscort(10, 2, EscortGrade.FLOWER) // 완료 10회지만 노쇼 2회 → 새싹이어야 함

        val changed = refresh(escort)

        assertThat(changed).isTrue()
        assertThat(storedGradeOf(escort)).isEqualTo(EscortGrade.SPROUT)

        val histories = historiesOf(escort)
        assertThat(histories).hasSize(1)
        assertThat(histories[0].previousGrade).isEqualTo(EscortGrade.FLOWER)
        assertThat(histories[0].newGrade).isEqualTo(EscortGrade.SPROUT)
    }

    @Test
    @DisplayName("[refreshGrade] 등급이 같으면 아무것도 하지 않고 이력도 남기지 않는다")
    fun 등급이_같으면_아무것도_안한다() {
        val escort = saveEscort(5, 0, EscortGrade.SPROUT) // 이미 맞는 등급

        val changed = refresh(escort)

        assertThat(changed).isFalse()
        assertThat(storedGradeOf(escort)).isEqualTo(EscortGrade.SPROUT)
        assertThat(historiesOf(escort)).isEmpty()
    }

    @Test
    @DisplayName("[refreshGrade] 같은 동행인에게 두 번 호출해도 이력은 한 번만 남는다")
    fun 두번_호출해도_이력은_한번() {
        val escort = saveEscort(10, 0, EscortGrade.SEED)

        val first = refresh(escort)
        val second = refresh(escort)

        assertThat(first).isTrue()
        assertThat(second).isFalse()
        assertThat(historiesOf(escort)).hasSize(1)
    }

    /* ── 스케줄러 (escortGradeCheck) ───────────────────────── */
    @Test
    @DisplayName("[스케줄러] 등급이 어긋난 동행인만 고치고, 바뀐 인원 수를 반환한다")
    fun 스케줄러는_어긋난_동행인만_고친다() {
        val needsPromote = saveEscort(5, 0, EscortGrade.SEED) // 승급 필요
        val needsDemote = saveEscort(10, 3, EscortGrade.FLOWER) // 강등 필요 (노쇼 3회 → 씨앗)
        val alreadyOk = saveEscort(30, 0, EscortGrade.EGGPLANT) // 이미 맞음

        val updated = escortService.escortGradeCheck()

        assertThat(updated).isEqualTo(2)
        assertThat(storedGradeOf(needsPromote)).isEqualTo(EscortGrade.SPROUT)
        assertThat(storedGradeOf(needsDemote)).isEqualTo(EscortGrade.SEED)
        assertThat(storedGradeOf(alreadyOk)).isEqualTo(EscortGrade.EGGPLANT)

        assertThat(historiesOf(needsPromote)).hasSize(1)
        assertThat(historiesOf(needsDemote)).hasSize(1)
        assertThat(historiesOf(alreadyOk)).isEmpty()
    }

    @Test
    @DisplayName("[스케줄러] 모두 맞으면 0명이고, 한 번 더 돌려도 변화가 없다")
    fun 스케줄러를_다시_돌려도_변화가_없다() {
        saveEscort(5, 0, EscortGrade.SEED)

        val first = escortService.escortGradeCheck()
        val second = escortService.escortGradeCheck()

        assertThat(first).isEqualTo(1)
        assertThat(second).isZero()
    }

    @Test
    @DisplayName("[스케줄러] 동행인이 없어도 오류 없이 0명을 반환한다")
    fun 동행인이_없어도_오류가_없다() {
        assertThat(escortService.escortGradeCheck()).isZero()
    }

    companion object {
        private val SEQ = AtomicInteger()
    }
}
