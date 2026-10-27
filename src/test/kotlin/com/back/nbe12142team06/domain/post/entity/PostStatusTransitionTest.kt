package com.back.nbe12142team06.domain.post.entity

import com.back.nbe12142team06.domain.post.entity.PostStatus.CANCELED
import com.back.nbe12142team06.domain.post.entity.PostStatus.COMPLETED
import com.back.nbe12142team06.domain.post.entity.PostStatus.EXPIRED
import com.back.nbe12142team06.domain.post.entity.PostStatus.IN_PROGRESS
import com.back.nbe12142team06.domain.post.entity.PostStatus.MATCHED
import com.back.nbe12142team06.domain.post.entity.PostStatus.OPEN
import com.back.nbe12142team06.support.TransitionTestBase

/**
 * PostStatus 상태 전이 규칙 테스트. 아래 표는 docs/state-diagrams.md 의 흐름도와 같아야 한다.
 * 규칙을 바꾸면 PostStatus.canTransitionTo, 이 표, 흐름도 문서를 함께 고친다.
 */
class PostStatusTransitionTest : TransitionTestBase<PostStatus>() {

    override fun stateType(): Class<PostStatus> = PostStatus::class.java

    override fun allowed(): Map<PostStatus, Set<PostStatus>> = mapOf(
        OPEN to setOf(MATCHED, EXPIRED, CANCELED),
        MATCHED to setOf(IN_PROGRESS, CANCELED, OPEN),
        IN_PROGRESS to setOf(COMPLETED),
        COMPLETED to emptySet(),
        CANCELED to emptySet(),
        EXPIRED to emptySet(),
    )

    override fun canTransition(from: PostStatus, to: PostStatus): Boolean = from.canTransitionTo(to)
}
