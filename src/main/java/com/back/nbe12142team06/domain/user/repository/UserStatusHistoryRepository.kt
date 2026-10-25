package com.back.nbe12142team06.domain.user.repository

import com.back.nbe12142team06.domain.user.entity.User
import com.back.nbe12142team06.domain.user.entity.UserStatusHistory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UserStatusHistoryRepository : JpaRepository<UserStatusHistory, Long> {

    fun findAllByUserId(userId: Long): List<UserStatusHistory>

    // 회원의 가장 최근 상태 이력 (현재 정지 사유·만료 시각 확인용)
    fun findTopByUserOrderByIdDesc(user: User): Optional<UserStatusHistory>
}