package com.back.nbe12142team06.domain.user.controller

import com.back.nbe12142team06.domain.user.dto.admin.*
import com.back.nbe12142team06.domain.user.dto.profile.EscortProfileResponse
import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.service.UserService
import com.back.nbe12142team06.global.response.RsData
import com.back.nbe12142team06.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "관리자 회원", description = "관리자 회원 조회, 수정, 탈퇴 및 프로필 조회 관련 API")
@RestController
@RequestMapping("/api/v1/admin")
class AdminUserController(
    private val userService: UserService,
) {

    @Operation(summary = "회원 상세 조회", description = "관리자가 특정 회원의 정보를 조회합니다. 탈퇴한 회원도 조회할 수 있습니다.")
    @GetMapping("/users/{userId}")
    fun getUser(@PathVariable userId: Long): RsData<AdminUserResponse> {
        val user = userService.findByIdIncludingDeleted(userId)

        return RsData("200-1", "회원 정보 조회가 완료되었습니다.", toAdminUserResponse(user))
    }

    @Operation(summary = "회원 목록 조회", description = "관리자가 탈퇴한 회원을 포함한 회원 목록을 페이지 단위로 조회합니다.")
    @GetMapping("/users")
    fun getUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): RsData<Page<AdminUserResponse>> {
        val userPage = userService.findAllUsersIncludingDeleted(page, size).map { AdminUserResponse(it) }

        return RsData("200-2", "회원 목록 조회가 완료되었습니다.", userPage)
    }

    @Operation(summary = "회원 정보 수정", description = "관리자가 특정 회원의 정보를 수정합니다. 탈퇴한 회원은 수정할 수 없으며 아이디와 비밀번호는 변경되지 않습니다.")
    @PatchMapping("/users/{userId}")
    fun updateUser(
        @PathVariable userId: Long,
        @Valid @RequestBody request: AdminUserProfileUpdateRequest,
    ): RsData<AdminUserResponse> {
        val user = userService.updateUserByAdmin(userId, request)

        return RsData("200-3", "회원 정보가 수정되었습니다.", toAdminUserResponse(user))
    }

    @Operation(summary = "회원 탈퇴 처리", description = "관리자가 특정 회원을 탈퇴 처리합니다. 관리자 본인 또는 이미 탈퇴한 회원은 탈퇴 처리할 수 없습니다.")
    @DeleteMapping("/users/{userId}")
    fun deleteUser(
        @AuthenticationPrincipal admin: SecurityUser,
        @PathVariable userId: Long,
    ): RsData<Void> {
        userService.deleteUser(admin.id, userId)

        return RsData("200-4", "회원 탈퇴가 완료되었습니다.")
    }

    @Operation(summary = "동행자 프로필 조회", description = "관리자가 특정 동행자의 프로필을 계좌 정보를 포함하여 조회합니다.")
    @GetMapping("/users/{userId}/profile/escort")
    fun getProfileEscortByAdmin(@PathVariable userId: Long): RsData<EscortProfileResponse> {
        val escortProfile = userService.getEscortProfile(userId)

        return RsData("200-9", "동행 매니저 프로필 조회를 완료했습니다.", EscortProfileResponse(escortProfile))
    }

    @Operation(summary = "회원 기간 정지", description = "관리자가 회원에게 기간 정지 패널티를 부여합니다.")
    @PostMapping("/users/{userId}/suspension")
    fun suspendUserByAdmin(
        @PathVariable userId: Long,
        @Valid @RequestBody request: AdminUserStatusRequest,
        @AuthenticationPrincipal admin: SecurityUser,
    ): RsData<AdminUserSuspendResponse> {
        val userStatusHistory = userService.suspendUser(admin.id, userId, request.reason!!)

        return RsData("200-10", "회원이 기간 정지되었습니다.", AdminUserSuspendResponse(userStatusHistory))
    }

    @Operation(summary = "회원 영구 정지", description = "관리자가 회원에게 영구 정지 패널티를 부여합니다.")
    @PostMapping("/users/{userId}/ban")
    fun banUserByAdmin(
        @PathVariable userId: Long,
        @Valid @RequestBody request: AdminUserStatusRequest,
        @AuthenticationPrincipal admin: SecurityUser,
    ): RsData<AdminUserBanResponse> {
        val userStatusHistory = userService.banUser(admin.id, userId, request.reason)

        return RsData("200-11", "회원이 영구 정지되었습니다.", AdminUserBanResponse(userStatusHistory))
    }

    @Operation(summary = "회원 정지 해제", description = "관리자가 회원의 정지를 해제합니다.")
    @PostMapping("/users/{userId}/release")
    fun releaseUserByAdmin(
        @PathVariable userId: Long,
        @Valid @RequestBody request: AdminUserStatusRequest,
        @AuthenticationPrincipal admin: SecurityUser,
    ): RsData<AdminUserReleaseResponse> {
        val userStatusHistory = userService.releaseUser(admin.id, userId, request.reason)

        return RsData("200-12", "회원의 정지가 해제되었습니다.", AdminUserReleaseResponse(userStatusHistory))
    }

    // 회원 정보 + 현재 정지 이력(활성 회원이면 null)
    private fun toAdminUserResponse(user: User) =
        AdminUserResponse(user, userService.findCurrentSanction(user))
}