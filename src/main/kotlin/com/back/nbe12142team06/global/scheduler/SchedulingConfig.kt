package com.back.nbe12142team06.global.scheduler

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@Profile("!test")
@EnableScheduling
class SchedulingConfig
