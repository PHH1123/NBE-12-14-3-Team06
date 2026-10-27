package com.back.nbe12142team06.global.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class SecurityUser(
    val id: Long,
    private val username: String,
    private val authorities: Collection<GrantedAuthority>,
) : UserDetails {

    override fun getUsername(): String = username

    override fun getAuthorities(): Collection<GrantedAuthority> = authorities

    // JWT 인증이라 비밀번호는 보관하지 않는다
    override fun getPassword(): String? = null
}