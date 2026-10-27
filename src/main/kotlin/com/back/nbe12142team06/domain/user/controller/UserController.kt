package com.back.nbe12142team06.domain.user.controller

import com.back.nbe12142team06.domain.auth.service.RefreshTokenService
import com.back.nbe12142team06.domain.user.dto.profile.*
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpRequest
import com.back.nbe12142team06.domain.user.dto.signup.common.UserSignUpResponse
import com.back.nbe12142team06.domain.user.dto.user.UserProfileUpdateRequest
import com.back.nbe12142team06.domain.user.dto.user.UserResponse
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.service.UserService
import com.back.nbe12142team06.global.response.RsData
import com.back.nbe12142team06.global.rq.Rq
import com.back.nbe12142team06.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "회원", description = "회원가입, 회원 정보 및 의뢰인·동행자 프로필 관련 API")
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
    private val refreshTokenService: RefreshTokenService,
    private val rq: Rq,
) {

    @Operation(summary = "아이디 중복 검사", description = "입력한 아이디의 사용 가능 여부를 확인합니다.")
    @GetMapping("/username")
    fun checkUsername(@RequestParam username: String): RsData<Boolean> {
        val isAvailable = userService.isUsernameAvailable(username)
        val msg = if (isAvailable) "사용 가능한 아이디입니다." else "이미 사용 중인 아이디입니다."

        return RsData("200-1", msg, isAvailable)
    }

    @Operation(summary = "이메일 중복 검사", description = "입력한 이메일의 사용 가능 여부를 확인합니다.")
    @GetMapping("/email")
    fun checkEmail(@RequestParam email: String): RsData<Boolean> {
        val isAvailable = userService.isEmailAvailable(email)
        val msg = if (isAvailable) "사용 가능한 이메일입니다." else "이미 사용 중인 이메일입니다."

        return RsData("200-2", msg, isAvailable)
    }

    @Operation(summary = "회원가입", description = "회원가입 후 Access Token과 Refresh Token을 발급하여 쿠키에 저장합니다.")
    @PostMapping
    fun signUp(@RequestBody @Valid request: UserSignUpRequest): RsData<UserSignUpResponse> {
        val createUser = userService.signUp(request)

        // access token, refresh token 발급 후 쿠키에 저장
        rq.setAccessTokenCookie(userService.genAccessToken(createUser))
        rq.setRefreshTokenCookie(refreshTokenService.generate(createUser))

        return RsData("201-1", "회원 가입이 완료되었습니다.", UserSignUpResponse(createUser))
    }

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 회원 정보를 조회합니다.")
    @GetMapping("/profile")
    fun profile(@AuthenticationPrincipal me: SecurityUser): RsData<UserResponse> {
        val user = userService.myProfile(me.id)

        return RsData("200-3", "내 정보 조회가 완료되었습니다.", toUserResponse(user))
    }

    @Operation(summary = "내 정보 수정", description = "현재 로그인한 사용자의 회원 정보를 수정합니다.")
    @PatchMapping("/profile")
    fun updateProfile(
        @AuthenticationPrincipal me: SecurityUser,
        @Valid @RequestBody request: UserProfileUpdateRequest,
    ): RsData<UserResponse> {
        val user = userService.updateMyProfile(me.id, request)

        return RsData("200-4", "회원 정보가 수정되었습니다.", toUserResponse(user))
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자의 프로필과 회원 정보를 삭제하고 Refresh Token을 폐기한 뒤 인증 토큰 쿠키를 제거합니다.")
    @DeleteMapping("/profile")
    fun deleteProfile(@AuthenticationPrincipal me: SecurityUser): RsData<Void> {
        userService.deleteMyProfile(me.id)
        rq.clearTokenCookies()

        return RsData("204-1", "회원 탈퇴가 완료되었습니다.")
    }

    @Operation(summary = "의뢰인 프로필 생성", description = "현재 로그인한 사용자의 의뢰인 프로필을 생성합니다.")
    @PostMapping("/profile/client")
    fun createProfileClient(
        @AuthenticationPrincipal me: SecurityUser,
        @RequestBody @Valid request: ClientProfileRequest,
    ): RsData<ClientProfileResponse> {
        val clientProfile = userService.createClientProfile(me.id, request)

        return RsData("201-2", "의뢰인 프로필이 생성되었습니다.", ClientProfileResponse(clientProfile))
    }

    @Operation(summary = "내 의뢰인 프로필 조회", description = "현재 로그인한 사용자의 의뢰인 프로필을 조회합니다.")
    @GetMapping("/profile/client")
    fun getProfileClient(@AuthenticationPrincipal me: SecurityUser): RsData<ClientProfileResponse> {
        val clientProfile = userService.getClientProfile(me.id)

        return RsData("200-5", "의뢰인 프로필 조회를 완료했습니다.", ClientProfileResponse(clientProfile))
    }

    @Operation(summary = "의뢰인 프로필 조회", description = "관리자 또는 해당 의뢰인과 매칭된 동행자가 의뢰인 프로필을 조회합니다.")
    @GetMapping("/{userId}/profile/client")
    fun getClientProfile(
        @AuthenticationPrincipal me: SecurityUser,
        @PathVariable userId: Long,
    ): RsData<ClientProfileResponse> {
        val clientProfile = userService.getClientProfile(me.id, userId)

        return RsData("200-6", "의뢰인 프로필 조회가 완료되었습니다.", ClientProfileResponse(clientProfile))
    }

    @Operation(summary = "의뢰인 프로필 수정", description = "현재 로그인한 사용자의 의뢰인 프로필을 수정합니다.")
    @PutMapping("/profile/client")
    fun updateProfileClient(
        @AuthenticationPrincipal me: SecurityUser,
        @RequestBody @Valid request: ClientProfileModifyRequest,
    ): RsData<ClientProfileModifyResponse> {
        val clientProfile = userService.updateClientProfile(me.id, request)

        return RsData("200-7", "의뢰인 프로필 수정을 완료했습니다.", ClientProfileModifyResponse(clientProfile))
    }

    @Operation(summary = "동행자 프로필 생성", description = "현재 로그인한 사용자의 동행자 프로필을 생성합니다.")
    @PostMapping("/profile/escort")
    fun createProfileEscort(
        @AuthenticationPrincipal me: SecurityUser,
        @RequestBody @Valid request: EscortProfileRequest,
    ): RsData<EscortProfileResponse> {
        val escort = userService.createEscortProfile(me.id, request)

        return RsData("201-3", "동행 매니저 프로필이 생성되었습니다.", EscortProfileResponse(escort))
    }

    @Operation(summary = "내 동행자 프로필 조회", description = "현재 로그인한 사용자의 동행자 프로필을 계좌 정보를 포함하여 조회합니다.")
    @GetMapping("/profile/escort")
    fun getProfileEscort(@AuthenticationPrincipal me: SecurityUser): RsData<EscortProfileResponse> {
        val escortProfile = userService.getEscortProfile(me.id)

        return RsData("200-8", "동행 매니저 프로필 조회를 완료했습니다.", EscortProfileResponse(escortProfile))
    }

    @Operation(summary = "동행자 프로필 조회", description = "특정 동행자의 프로필을 계좌 정보를 제외하고 조회합니다.")
    @GetMapping("/{userId}/profile/escort")
    fun getProfileEscortByClient(@PathVariable userId: Long): RsData<EscortProfileForClientResponse> {
        val escortProfile = userService.getEscortProfile(userId)

        return RsData("200-9", "동행 매니저 프로필 조회를 완료했습니다.", EscortProfileForClientResponse(escortProfile))
    }

    @Operation(summary = "동행자 프로필 수정", description = "현재 로그인한 사용자의 동행자 프로필을 수정합니다.")
    @PutMapping("/profile/escort")
    fun updateProfileEscort(
        @AuthenticationPrincipal me: SecurityUser,
        @RequestBody @Valid request: EscortProfileModifyRequest,
    ): RsData<EscortProfileModifyResponse> {
        val escortProfile = userService.updateEscortProfile(me.id, request)

        return RsData("200-10", "동행 매니저 프로필 수정을 완료했습니다.", EscortProfileModifyResponse(escortProfile))
    }

    // 회원 정보 + 현재 정지 이력(활성 회원이면 null)
    private fun toUserResponse(user: User) =
        UserResponse(user, userService.findCurrentSanction(user))
}