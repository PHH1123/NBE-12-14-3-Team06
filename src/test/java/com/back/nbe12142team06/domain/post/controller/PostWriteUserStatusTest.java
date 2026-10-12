package com.back.nbe12142team06.domain.post.controller;

import com.back.nbe12142team06.domain.post.repository.PostRepository;
import com.back.nbe12142team06.domain.user.entity.User;
import com.back.nbe12142team06.domain.user.enums.Gender;
import com.back.nbe12142team06.domain.user.enums.Role;
import com.back.nbe12142team06.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공고 등록 시 계정 상태(UserStatus) 확인: 활성(ACTIVE) 계정만 등록할 수 있다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PostWriteUserStatusTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private User saveClient() {
        int seq = SEQ.incrementAndGet();
        String username = "writeStatusClient" + seq;
        return userRepository.save(new User(
                username, passwordEncoder.encode("1234"), username + "@test.com", "테스트",
                Role.CLIENT, Gender.MALE, LocalDate.of(1990, 1, 1), String.format("010-4441-%04d", seq), "서울"));
    }

    private Cookie login(User user) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"1234\"}".formatted(user.getUsername())))
                .andReturn().getResponse().getCookie("accessToken");
    }

    private String postJson() {
        LocalDateTime now = LocalDateTime.now();
        return """
                {
                    "title": "상태 확인 공고", "content": "내용", "region": "서울",
                    "hospitalName": "서울성모병원", "hospitalAddress": "서울 서초구 반포대로 222",
                    "hospitalLat": 37.5012743, "hospitalLng": 127.0051893,
                    "pickupAddress": "서울 서초구 잠원동 10-1", "pickupLat": 37.5160000, "pickupLng": 127.0200000,
                    "hourlyPay": 15000,
                    "recruitStartAt": "%s", "recruitEndAt": "%s", "escortStartAt": "%s", "escortEndAt": "%s",
                    "rideSelectToHospital": "TAXI", "rideSelectToHome": "TAXI",
                    "patientNote": "테스트", "reportRequired": true
                }
                """.formatted(now.plusDays(1), now.plusDays(2), now.plusDays(3), now.plusDays(3).plusHours(3));
    }

    @Test
    @DisplayName("[공고 등록] 활성(ACTIVE) 의뢰인은 등록할 수 있다")
    void 활성_계정은_등록된다() throws Exception {
        User client = saveClient();

        mvc.perform(post("/api/v1/posts").cookie(login(client)).contentType(MediaType.APPLICATION_JSON).content(postJson()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("[공고 등록] 기간 정지(SUSPENDED) 계정은 등록할 수 없다 - 403, 공고가 생기지 않는다")
    void 정지_계정은_등록_불가() throws Exception {
        User client = saveClient();
        Cookie cookie = login(client);
        client.suspend(1L, "테스트");   // 정지 이력(UserStatusHistory)은 저장하지 않아도 상태 변경만 필요하다
        userRepository.save(client);
        long before = postRepository.count();

        mvc.perform(post("/api/v1/posts").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(postJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value("403-11"));

        assertThat(postRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("[공고 등록] 영구 정지(BANNED) 계정은 등록할 수 없다 - 403")
    void 영구정지_계정은_등록_불가() throws Exception {
        User client = saveClient();
        Cookie cookie = login(client);
        client.ban(1L, "테스트");
        userRepository.save(client);

        mvc.perform(post("/api/v1/posts").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(postJson()))
                .andExpect(status().isForbidden());
    }
}
