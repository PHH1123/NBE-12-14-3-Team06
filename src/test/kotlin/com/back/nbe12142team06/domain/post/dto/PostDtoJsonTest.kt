package com.back.nbe12142team06.domain.post.dto

import com.back.nbe12142team06.domain.post.entity.Post
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.enums.Gender
import com.back.nbe12142team06.domain.user.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

// 프론트(features/post/types.ts)가 읽는 JSON 키 이름을 지킨다.
// 서버가 실제로 쓰는 ObjectMapper 로 직렬화해서, 키 이름이 바뀌면(예: isOwner -> owner) 여기서 걸린다.
@SpringBootTest
@ActiveProfiles("test")
class PostDtoJsonTest {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun newDto(): PostDto {
        val client = User(
            "jsonClient", "pw", "jsonClient@test.com", "홍길동", Role.CLIENT,
            Gender.MALE, LocalDate.of(1990, 1, 1), "010-0000-0000", "서울",
        )
        val now = LocalDateTime.of(2026, 10, 23, 9, 0)
        val post = Post(
            id = Math.random().toLong(),
            client = client,
            title = "제목", content = "내용", region = "서울",
            hospitalName = "병원", hospitalAddress = "서울시",
            hospitalLat = BigDecimal.valueOf(37.5), hospitalLng = BigDecimal.valueOf(127.0),
            pickupAddress = "집", pickupLat = BigDecimal.valueOf(37.5), pickupLng = BigDecimal.valueOf(127.0),
            hourlyPay = 15000,
            recruitStartAt = now, recruitEndAt = now.plusDays(1),
            escortStartAt = now.plusDays(2), escortEndAt = now.plusDays(2).plusHours(3),
            patientNote = "특이사항", reportRequired = true,
        )
        return PostDto(post, true, true)
    }

    @Test
    @DisplayName("공고 응답 JSON 은 프론트가 읽는 키 이름을 그대로 쓴다")
    fun 공고_응답_JSON_키() {
        val json = objectMapper.readTree(objectMapper.writeValueAsString(newDto()))

        assertThat(json.has("isOwner")).`as`("isOwner 키").isTrue()
        assertThat(json.get("isOwner").asBoolean()).isTrue()
        assertThat(json.has("owner")).`as`("owner 로 바뀌면 안 된다").isFalse()
        assertThat(json.has("reportRequired")).isTrue()
        assertThat(json.get("reportRequired").asBoolean()).isTrue()
        assertThat(json.has("canViewSensitive")).isTrue()
        assertThat(json.has("client_id")).`as`("client_id 키").isTrue()
        assertThat(json.has("clientUserId")).isTrue()
        assertThat(json.get("postStatus").asString()).isEqualTo("모집 중")
        assertThat(json.get("hourlyPay").asInt()).isEqualTo(15000)
        assertThat(json.get("escortHours").decimalValue()).isEqualByComparingTo("3.0")
        assertThat(json.get("patientNote").asString()).isEqualTo("특이사항")
    }

    @Test
    @DisplayName("민감정보 권한이 없으면 환자 특이사항은 null 로 내려가고 이름은 가려진다")
    fun 민감정보_없으면_가려진다() {
        val full = newDto()
        val hidden = PostDto(
            Post(
                id = full.id,
                client = User(
                    "u", "pw", "u@test.com", "홍길동", Role.CLIENT, Gender.MALE,
                    LocalDate.of(1990, 1, 1), "010-0000-0001", "서울",
                ),
                title = "t", content = "c", region = "r", hospitalName = "h", hospitalAddress = "a",
                hospitalLat = BigDecimal.ONE, hospitalLng = BigDecimal.ONE,
                pickupAddress = "p", pickupLat = BigDecimal.ONE, pickupLng = BigDecimal.ONE,
                hourlyPay = 1000,
                recruitStartAt = LocalDateTime.now(), recruitEndAt = LocalDateTime.now(),
                escortStartAt = LocalDateTime.now(), escortEndAt = LocalDateTime.now(),
                patientNote = "비밀",
            ),
            false,
        )

        assertThat(full.client_id).isEqualTo("홍*동")
        assertThat(hidden.patientNote).isNull()
        assertThat(hidden.canViewSensitive).isFalse()
        assertThat(hidden.isOwner).isFalse()
    }
}
