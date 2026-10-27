package com.back.nbe12142team06.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock {
        return Clock.systemDefaultZone()
    }
}
