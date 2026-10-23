package com.back.nbe12142team06.domain.post.scheduler

import com.back.nbe12142team06.domain.post.service.PostService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PostScheduler(
    private val postService: PostService,
) {

    @Scheduled(fixedRate = 60000) // 1분마다 실행
    fun expireOverduePosts() {
        postService.expireOverduePosts()
    }
}
