package com.back.nbe12142team06.domain.user.enums

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

enum class Role(
    val description: String
) {
    ADMIN("관리자"),
    CLIENT("의뢰인"),
    ESCORT("동행인");

    fun toAuthorities(): MutableList<GrantedAuthority> {
        return mutableListOf(SimpleGrantedAuthority("ROLE_${name}"))
    }
}
