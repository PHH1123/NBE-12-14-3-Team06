package com.back.nbe12142team06.global.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@EnableAsync
@Configuration
class AsyncConfig : AsyncConfigurer {
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
            .apply {
                corePoolSize = 3
                maxPoolSize = 20
                queueCapacity = 20
                keepAliveSeconds = 30
                setThreadNamePrefix("async-notification-")

                // 스레드 풀 꽉찼을 때 톰캣의 스레드가 직접 처리
                setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())

                initialize()
            }
        return executor
    }
}
