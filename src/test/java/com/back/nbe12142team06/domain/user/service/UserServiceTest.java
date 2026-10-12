package com.back.nbe12142team06.domain.user.service;

import com.back.nbe12142team06.domain.user.dto.profile.EscortProfileRequest;
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpRequest;
import com.back.nbe12142team06.domain.user.entity.EscortProfile;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.entity.UserStatusHistory;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.enums.UserStatus;
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import com.back.nbe12142team06.domain.user.repository.UserStatusHistoryRepository;
import com.back.nbe12142team06.global.exception.DuplicatedException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class UserServiceTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager em;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserStatusHistoryRepository userStatusHistoryRepository;
    @Autowired
    private EscortProfileRepository escortProfileRepository;

    // 회원 생성 헬퍼
    private User createUser(String username, String email, String phoneNum) {
        User user = User.builder()
                .username(username)
                .password("password1!")
                .email(email)
                .name("테스트")
                .role(Role.CLIENT)
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1960, 1, 1))
                .phoneNum(phoneNum)
                .region("서울")
                .build();
        User savedUser = userRepository.save(user);
        userStatusHistoryRepository.save(UserStatusHistory.signup(savedUser));
        return savedUser;
    }

    // 정지 종료 시각 직접 수정
    private void updateEndAt(Long historyId, LocalDateTime endAt) {
        em.createNativeQuery("UPDATE user_status_history SET end_at = :endAt WHERE id = :id")
                .setParameter("endAt", endAt)
                .setParameter("id", historyId)
                .executeUpdate();
    }


    @Test
    @DisplayName("[UserService] 회원가입 - 단순 저장")
    void t1(){

        User saved = this.userService.signUp(
                new UserSignUpRequest(
                        "user1",
                        "1234",
                        "user@test.test",
                        "유저1",
                        Role.CLIENT,
                        Gender.MALE,
                        LocalDate.of(1990, 5, 6),
                        "010-1234-5678",
                        "서울"
                )
        );

        // 캐시 비우고 실제로 DB에서 조회
        em.flush();
        em.clear();

        User userCheck = this.userRepository.findByUsername("user1").orElseThrow();
        assertThat(userCheck.getUsername()).isEqualTo("user1");
        assertThat(userCheck.getPassword()).isNotNull();
        assertThat(userCheck.getEmail()).isEqualTo("user@test.test");
        assertThat(userCheck.getName()).isEqualTo("유저1");
        assertThat(userCheck.getRole()).isEqualTo(Role.CLIENT);
        assertThat(userCheck.getGender()).isEqualTo(Gender.MALE);
        assertThat(userCheck.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 6));
        assertThat(userCheck.getPhoneNum()).isEqualTo("010-1234-5678");
        assertThat(userCheck.getRegion()).isEqualTo("서울");
    }

    @Test
    @DisplayName("[UserService] 회원가입 - 이미 사용 중인 아이디로 가입 시 예외")
    void t2(){
        // username = user1
        User saved = this.userService.signUp(
                new UserSignUpRequest(
                        "user1",
                        "1234",
                        "user@test.test",
                        "유저1",
                        Role.CLIENT,
                        Gender.MALE,
                        LocalDate.of(1990, 5, 6),
                        "010-1234-5678",
                        "서울"
                )
        );

        // username = user1
        DuplicatedException e = catchThrowableOfType(() ->
                this.userService.signUp(
                        new UserSignUpRequest(
                                "user1",
                                "1234",
                                "userA@test.test",
                                "유저A",
                                Role.CLIENT,
                                Gender.MALE,
                                LocalDate.of(1990, 5, 6),
                                "010-2345-6789",
                                "경기"
                        )
                ),
                DuplicatedException.class
        );

        assertThat(e).isNotNull();
        assertThat(e.getMessage()).isEqualTo("이미 사용 중인 아이디입니다.");
        assertThat(e.getStatusCode()).isEqualTo("409-1");
    }

    @Test
    @DisplayName("[UserService] 회원가입 - 이미 사용 중인 이메일로 가입 시 예외")
    void t3(){
        // email = user@test.test
        User saved = this.userService.signUp(
                new UserSignUpRequest(
                        "user1",
                        "1234",
                        "user@test.test",
                        "유저1",
                        Role.CLIENT,
                        Gender.MALE,
                        LocalDate.of(1990, 5, 6),
                        "010-1234-5678",
                        "서울"
                )
        );

        // email = user@test.test
        DuplicatedException e = catchThrowableOfType(() ->
                this.userService.signUp(
                        new UserSignUpRequest(
                                "user2",
                                "1234",
                                "user@test.test",
                                "유저A",
                                Role.CLIENT,
                                Gender.MALE,
                                LocalDate.of(1990, 5, 6),
                                "010-2345-6789",
                                "경기"
                        )
                ),
                DuplicatedException.class
        );

        assertThat(e).isNotNull();
        assertThat(e.getMessage()).isEqualTo("이미 사용 중인 이메일입니다.");
        assertThat(e.getStatusCode()).isEqualTo("409-2");
    }

    @Test
    @DisplayName("[UserService] 회원가입 - 이미 사용 중인 전화번호로 가입 시 예외")
    void t4(){
        // phoneNum = "010-1234-5678"
        User saved = this.userService.signUp(
                new UserSignUpRequest(
                        "user1",
                        "1234",
                        "user@test.test",
                        "유저1",
                        Role.CLIENT,
                        Gender.MALE,
                        LocalDate.of(1990, 5, 6),
                        "010-1234-5678",
                        "서울"
                )
        );

        // phoneNum = "010-1234-5678"
        DuplicatedException e = catchThrowableOfType(() ->
                        this.userService.signUp(
                                new UserSignUpRequest(
                                        "user2",
                                        "1234",
                                        "user2@test.test",
                                        "유저A",
                                        Role.CLIENT,
                                        Gender.MALE,
                                        LocalDate.of(1990, 5, 6),
                                        "010-1234-5678",
                                        "경기"
                                )
                        ),
                DuplicatedException.class
        );

        assertThat(e).isNotNull();
        assertThat(e.getMessage()).isEqualTo("이미 사용 중인 전화번호입니다.");
        assertThat(e.getStatusCode()).isEqualTo("409-3");
    }


    @Test
    @DisplayName("[UserService] 회원가입 - 비밀번호 암호화")
    void t5(){
        User saved = this.userService.signUp(
                new UserSignUpRequest(
                        "user1",
                        "1234",
                        "user@test.test",
                        "유저1",
                        Role.CLIENT,
                        Gender.MALE,
                        LocalDate.of(1990, 5, 6),
                        "010-1234-5678",
                        "서울"
                )
        );

        em.flush();
        em.clear();

        User userCheck = this.userRepository.findByUsername("user1").orElseThrow();

        // 비밀번호가 평문인지 검증
        assertThat(userCheck.getPassword()).isNotEqualTo("1234");
        // 비밀번호가 지정한 방식으로 정확히 암호화되어 저장되어 있는지 검증
        assertThat(passwordEncoder.matches("1234", userCheck.getPassword())).isTrue();
    }

    @Test
    @DisplayName("[UserService] 동행 매니저 프로필 생성")
    void t6() {

        // 회원 생성
        User saved = this.userService.signUp(
                new UserSignUpRequest(
                        "user1",
                        "1234",
                        "user@test.test",
                        "유저1",
                        Role.ESCORT,
                        Gender.MALE,
                        LocalDate.of(1990, 5, 6),
                        "010-1234-5678",
                        "서울"
                )
        );

        this.userService.createEscortProfile(
                saved.getId(),
                new EscortProfileRequest(
                        "동행 매니저 입니다.",
                        "오픈은행",
                        "유저1",
                        "123-000000-123")
        );

        em.flush();
        em.clear();

        EscortProfile profileCheck = this.escortProfileRepository.findById(saved.getId()).get();

        assertThat(profileCheck.getBankName()).isEqualTo("오픈은행");
        assertThat(profileCheck.getAccountNumber()).isEqualTo("123-000000-123");
    }

    @Test
    @DisplayName("[UserService] 정지 기간이 지나면 ACTIVE로 변경")
    void t7() {
        User user = createUser("expired01", "expired01@test.com", "010-7000-0001");
        userStatusHistoryRepository.save(user.suspend(1L, "만료 테스트"));
        em.flush();
        em.clear();

        int released = userService.releaseExpiredSuspensions(LocalDateTime.now().plusDays(8));
        em.flush();
        em.clear();

        assertThat(released).isEqualTo(1);
        User found = userRepository.findById(user.getId()).orElseThrow();
        assertThat(found.getUserStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("[UserService] 정지 기간이 유효하면 SUSPEND 유지")
    void t8() {
        User user = createUser("suspended01", "suspended01@test.com", "010-7000-0002");
        userStatusHistoryRepository.save(user.suspend(1L, "미만료 테스트"));
        em.flush();
        em.clear();

        int released = userService.releaseExpiredSuspensions(LocalDateTime.now().plusDays(6));
        em.flush();
        em.clear();

        assertThat(released).isZero();
        User found = userRepository.findById(user.getId()).orElseThrow();
        assertThat(found.getUserStatus()).isEqualTo(UserStatus.SUSPENDED);
    }
}
