package com.back.nbe12142team06.domain.user.scheduler

import com.back.nbe12142team06.domain.user.service.EscortService
import com.back.nbe12142team06.domain.user.service.UserService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class UserScheduler(
    private val userService: UserService,
    private val escortService: EscortService,
) {

    // 매 시각의 00분 , 30분으로 30분 주기로 실행
    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Seoul")
    fun releaseExpiredSuspensions() {
        userService.releaseExpiredSuspensions(LocalDateTime.now())
    }

    // 매일 새벽 3시에 실행 (등급 갱신 누락 보정용)
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    fun escortGradeCalculate() {
        escortService.escortGradeCheck()
    }
}