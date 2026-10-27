package com.back.nbe12142team06.domain.education.controller

import com.back.nbe12142team06.domain.education.entity.EducationVideo
import com.back.nbe12142team06.domain.education.repository.EducationProgressRepository
import com.back.nbe12142team06.domain.education.repository.EducationVideoRepository
import com.back.nbe12142team06.domain.education.repository.WatchProgressLogRepository
import com.back.nbe12142team06.domain.user.repository.EscortProfileRepository
import com.back.nbe12142team06.domain.user.repository.UserRepository
import jakarta.persistence.EntityManager
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class EducationControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var em: EntityManager

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var escortProfileRepository: EscortProfileRepository

    @Autowired
    private lateinit var educationVideoRepository: EducationVideoRepository

    @Autowired
    private lateinit var educationProgressRepository: EducationProgressRepository

    @Autowired
    private lateinit var watchProgressLogRepository: WatchProgressLogRepository

    private var phoneSeq = 0

    // 교육 영상 생성 (60초, 필수) 후 영상 ID 반환
    private fun createVideoId(): Long =
        educationVideoRepository.save(
            EducationVideo("동행 서비스 기본 교육", "/videos/sample_video.mp4", 60, true)
        ).requiredId

    // 역할 지정 회원가입 후 accessToken 쿠키 반환
    private fun signUp(username: String, role: String): Cookie {
        // 전화번호 형식 검증(@Pattern)을 통과하면서 회원마다 겹치지 않는 번호
        val phoneNum = "010-8080-%04d".format(++phoneSeq)

        val accessToken = mvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "username": "$username",
                        "password": "testPassword",
                        "email": "$username@user.user",
                        "name": "김춘식",
                        "role": "$role",
                        "gender": "MALE",
                        "birthDate": "1990-05-20",
                        "phoneNum": "$phoneNum",
                        "region": "서울시"
                    }
                    """
                )
        )
            .andExpect(status().isCreated())
            .andReturn()
            .response
            .getCookie("accessToken")

        assertThat(accessToken).isNotNull()
        return accessToken!!
    }

    // 동행 매니저 프로필 생성 (교육 진행 상황도 함께 생성됨)
    private fun createEscortProfile(escortToken: Cookie) {
        mvc.perform(
            post("/api/v1/users/profile/escort")
                .cookie(escortToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "intro": "동행 매니저입니다.",
                        "bankName": "오픈은행",
                        "accountHolder": "김춘식",
                        "accountNumber": "123-0000000-123"
                    }
                    """
                )
        )
            .andExpect(status().isCreated())
    }

    // 동행 매니저 가입 → 프로필 생성까지 한 번에
    private fun escortReady(username: String): Cookie =
        signUp(username, "ESCORT").also { createEscortProfile(it) }

    private fun findUserId(username: String): Long =
        userRepository.findByUsername(username)!!.id

    // 시청 기록 요청 (token 이 null 이면 비로그인 요청)
    private fun watchLog(videoId: Long, body: String, token: Cookie?): ResultActions {
        val request = post("/api/v1/education-videos/{videoId}/watchlogs", videoId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
        token?.let { request.cookie(it) }

        return mvc.perform(request).andDo(print())
    }

    // 교육 영상 목록 / 단건 조회 요청
    private fun getVideos(token: Cookie): ResultActions =
        mvc.perform(get("/api/v1/education-videos").cookie(token)).andDo(print())

    private fun getVideo(videoId: Long, token: Cookie): ResultActions =
        mvc.perform(get("/api/v1/education-videos/{videoId}", videoId).cookie(token)).andDo(print())

    private fun position(positionSec: Number) = """{ "positionSec": $positionSec }"""

    @Test
    @DisplayName("[EducationController] 시청 기록 - 정상 기록 시 200-101 반환")
    fun t1() {
        val videoId = createVideoId()
        val escortToken = escortReady("escort1")

        watchLog(videoId, position(0), escortToken)
            .andExpect(handler().handlerType(EducationController::class.java))
            .andExpect(handler().methodName("createLog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-101"))
            .andExpect(jsonPath("$.msg").value("시청 기록이 저장되었습니다."))
            .andExpect(jsonPath("$.data.maxWatchedSec").value(0.0))
            .andExpect(jsonPath("$.data.completed").value(false))
            .andExpect(jsonPath("$.data.verified").value(false))

        em.flush()
        em.clear()

        val logs = watchProgressLogRepository.findAll()
        assertThat(logs).hasSize(1)
        assertThat(logs[0].accepted).isTrue()
    }

    @Test
    @DisplayName("[EducationController] 시청 기록 - 앞으로 건너뛴 위치는 인정되지 않고 로그만 남음")
    fun t2() {
        val videoId = createVideoId()
        val escortToken = escortReady("escort1")

        watchLog(videoId, position(0), escortToken)
            .andExpect(status().isOk())

        // 직후 30초 위치 보고 → 경과 시간이 거의 없어 허용치 초과
        watchLog(videoId, position(30), escortToken)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-101"))
            .andExpect(jsonPath("$.data.maxWatchedSec").value(0.0))
            .andExpect(jsonPath("$.data.completed").value(false))

        em.flush()
        em.clear()

        val logs = watchProgressLogRepository.findAll()
        assertThat(logs).hasSize(2)
        assertThat(logs[1].accepted).isFalse()
    }

    @Test
    @DisplayName("[EducationController] 시청 기록 - 필수 영상을 끝까지 보면 완료 및 교육 이수 처리")
    fun t3() {
        val videoId = createVideoId()
        val escortToken = escortReady("escort1")
        val escortId = findUserId("escort1")

        // 60초 전부터 10초 간격으로 50초까지 정상 시청한 상태를 미리 만듦
        val progress = educationProgressRepository
            .findByEscortProfileUserIdAndEducationVideoId(escortId, videoId)!!

        val base = LocalDateTime.now().minusSeconds(60)
        for (sec in 0..50 step 10) {
            progress.record(sec.toDouble(), base.plusSeconds(sec.toLong()))
        }

        // 마지막 기록 약 10초 뒤, 영상 종료 위치 보고
        watchLog(videoId, position(60.1), escortToken)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-101"))
            .andExpect(jsonPath("$.data.maxWatchedSec").value(60.1))
            .andExpect(jsonPath("$.data.completed").value(true))
            .andExpect(jsonPath("$.data.verified").value(true))

        em.flush()
        em.clear()

        val updated = escortProfileRepository.findByIdOrNull(escortId)!!
        assertThat(updated.verified).isTrue()
        assertThat(updated.verifiedAt).isNotNull()
    }

    @Test
    @DisplayName("[EducationController] 시청 기록 - 이미 교육을 이수한 동행 매니저는 기록이 쌓이지 않음")
    fun t4() {
        val videoId = createVideoId()
        val escortToken = escortReady("escort1")
        val escortId = findUserId("escort1")

        escortProfileRepository.verify(escortId, LocalDateTime.now())

        watchLog(videoId, position(0), escortToken)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-101"))
            .andExpect(jsonPath("$.data.verified").value(true))

        em.flush()
        em.clear()

        assertThat(watchProgressLogRepository.count()).isZero()
    }

    @Test
    @DisplayName("[EducationController] 시청 기록 - 재생 위치 누락 시 400-01 반환")
    fun t5() {
        val videoId = createVideoId()
        val escortToken = escortReady("escort1")

        watchLog(videoId, "{}", escortToken)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-01"))
            .andExpect(jsonPath("$.msg").value("positionSec: 재생 위치는 필수입니다."))
    }

    @Test
    @DisplayName("[EducationController] 시청 기록 - 음수 재생 위치 요청 시 400-01 반환")
    fun t6() {
        val videoId = createVideoId()
        val escortToken = escortReady("escort1")

        watchLog(videoId, position(-1), escortToken)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400-01"))
            .andExpect(jsonPath("$.msg").value("positionSec: 재생 위치는 0 이상이어야 합니다."))
    }

    @Test
    @DisplayName("[EducationController] 시청 기록 - 존재하지 않는 영상 요청 시 404-101 반환")
    fun t7() {
        createVideoId()
        val escortToken = escortReady("escort1")

        watchLog(NOT_EXIST_VIDEO_ID, position(0), escortToken)
            .andExpect(handler().handlerType(EducationController::class.java))
            .andExpect(handler().methodName("createLog"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-101"))
            .andExpect(jsonPath("$.msg").value("존재하지않는 교육 영상입니다."))
    }

    @Test
    @DisplayName("[EducationController] 시청 기록 - 의뢰인 요청 시 403-1 반환")
    fun t8() {
        val videoId = createVideoId()
        val clientToken = signUp("client1", "CLIENT")

        watchLog(videoId, position(0), clientToken)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403-1"))
            .andExpect(jsonPath("$.msg").value("권한이 없습니다."))
    }

    @Test
    @DisplayName("[EducationController] 시청 기록 - 로그인 없이 요청 시 401-113 반환")
    fun t9() {
        val videoId = createVideoId()

        watchLog(videoId, position(0), null)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value("401-113"))
            .andExpect(jsonPath("$.msg").value("로그인 후 이용해주세요."))
    }

    @Test
    @DisplayName("[EducationController] 교육 영상 목록 조회 - 정상 조회 시 200-102 반환")
    fun t10() {
        val videoId = createVideoId()
        val escortToken = escortReady("escort1")

        em.flush()
        em.clear()

        getVideos(escortToken)
            .andExpect(handler().handlerType(EducationController::class.java))
            .andExpect(handler().methodName("getVideos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-102"))
            .andExpect(jsonPath("$.msg").value("교육 영상 목록을 조회했습니다."))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].videoId").value(videoId))
            .andExpect(jsonPath("$.data[0].title").value("동행 서비스 기본 교육"))
            .andExpect(jsonPath("$.data[0].url").value("/videos/sample_video.mp4"))
            .andExpect(jsonPath("$.data[0].durationSec").value(60))
            .andExpect(jsonPath("$.data[0].required").value(true))
            .andExpect(jsonPath("$.data[0].maxWatchedSec").value(0.0))
            .andExpect(jsonPath("$.data[0].completed").value(false))
    }

    @Test
    @DisplayName("[EducationController] 교육 영상 목록 조회 - 프로필 없는 동행 매니저 조회 시 404-3 반환")
    fun t11() {
        createVideoId()
        val escortToken = signUp("escort1", "ESCORT") // 프로필 생성 안 함

        getVideos(escortToken)
            .andExpect(handler().handlerType(EducationController::class.java))
            .andExpect(handler().methodName("getVideos"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-3"))
            .andExpect(jsonPath("$.msg").value("동행 매니저 프로필이 존재하지 않습니다."))
    }

    @Test
    @DisplayName("[EducationController] 교육 영상 단건 조회 - 정상 조회 시 200-103 반환")
    fun t12() {
        val videoId = createVideoId()
        val escortToken = escortReady("escort1")

        em.flush()
        em.clear()

        getVideo(videoId, escortToken)
            .andExpect(handler().handlerType(EducationController::class.java))
            .andExpect(handler().methodName("getVideo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value("200-103"))
            .andExpect(jsonPath("$.msg").value("교육 영상을 조회했습니다."))
            .andExpect(jsonPath("$.data.videoId").value(videoId))
            .andExpect(jsonPath("$.data.maxWatchedSec").value(0.0))
            .andExpect(jsonPath("$.data.completed").value(false))
    }

    @Test
    @DisplayName("[EducationController] 교육 영상 단건 조회 - 존재하지 않는 영상 조회 시 404-103 반환")
    fun t13() {
        createVideoId()
        val escortToken = escortReady("escort1")

        getVideo(NOT_EXIST_VIDEO_ID, escortToken)
            .andExpect(handler().handlerType(EducationController::class.java))
            .andExpect(handler().methodName("getVideo"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404-103"))
            .andExpect(jsonPath("$.msg").value("존재하지않는 교육 영상입니다."))
    }

    companion object {
        private const val NOT_EXIST_VIDEO_ID = 99999L
    }
}
