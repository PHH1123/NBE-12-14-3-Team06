package com.back.nbe12142team06.domain.auth.controller

import com.back.nbe12142team06.domain.auth.service.RefreshTokenService
import com.back.nbe12142team06.domain.user.dto.login.UserLoginRequest
import com.back.nbe12142team06.domain.user.dto.login.UserLoginResponse
import com.back.nbe12142team06.domain.user.service.UserService
import com.back.nbe12142team06.global.response.RsData
import com.back.nbe12142team06.global.rq.Rq
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@Tag(name = "인증", description = "로그인, 로그아웃 및 토큰 재발급 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val rq: Rq,
    private val refreshTokenService: RefreshTokenService,
    private val userService: UserService,
) {

    @Operation(summary = "Access Token 재발급", description = "Refresh Token을 이용해 새로운 Access Token을 발급합니다.")
    @PostMapping("/refresh")
    fun refresh(): RsData<Void> {
        val newAccessToken = refreshTokenService.refresh(rq.refreshToken)
        rq.setAccessTokenCookie(newAccessToken)

        return RsData("200-91", "토큰 재발급되었습니다.")
    }

    @Operation(summary = "로그인", description = "사용자 정보를 확인한 후 Access Token과 Refresh Token을 발급합니다.")
    @PostMapping("/login")
    fun login(@RequestBody @Valid request: UserLoginRequest): RsData<UserLoginResponse> {
        val user = userService.login(request)

        rq.setAccessTokenCookie(userService.genAccessToken(user))
        rq.setRefreshTokenCookie(refreshTokenService.generate(user))

        return RsData("200-92", "${user.name}님 반갑습니다.", UserLoginResponse(user))
    }

    @Operation(summary = "로그아웃", description = "Refresh Token을 폐기하고 인증 토큰 쿠키를 삭제합니다.")
    @DeleteMapping("/logout")
    fun logout(): RsData<Void> {
        refreshTokenService.revoke(rq.refreshToken)
        rq.clearTokenCookies()

        return RsData("200-93", "로그아웃 되었습니다.")
    }
}