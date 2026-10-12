package com.back.nbe12142team06.domain.user.scheduler;

import com.back.nbe12142team06.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class UserScheduler {

    private final UserService userService;

    // 매 시각의 00분 , 30분으로 30분 주기로 실행
    @Scheduled(cron = "0 */30 * * * *", zone="Asia/Seoul")
    public void releaseExpiredSuspensions() {
        this.userService.releaseExpiredSuspensions(LocalDateTime.now());
    }
}
