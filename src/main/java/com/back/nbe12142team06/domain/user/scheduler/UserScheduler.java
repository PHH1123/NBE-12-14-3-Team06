package com.back.nbe12142team06.domain.user.scheduler;

import com.back.nbe12142team06.domain.user.service.EscortService;
import com.back.nbe12142team06.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class UserScheduler {

    private final UserService userService;
    private final EscortService escortService;

    // 매 시각의 00분 , 30분으로 30분 주기로 실행
    @Scheduled(cron = "0 */30 * * * *", zone="Asia/Seoul")
    public void releaseExpiredSuspensions() {
        this.userService.releaseExpiredSuspensions(LocalDateTime.now());
    }

    // 매일 새벽 3시에 실행 (등급 갱신 누락 보정용)
    @Scheduled(cron = "0 0 3 * * *", zone="Asia/Seoul")
    public void escortGradeCalculate() {
        this.escortService.escortGradeCheck();
    }

}
