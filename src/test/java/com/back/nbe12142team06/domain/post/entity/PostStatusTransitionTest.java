package com.back.nbe12142team06.domain.post.entity;

import com.back.nbe12142team06.support.TransitionTestBase;

import java.util.Map;
import java.util.Set;

import static com.back.nbe12142team06.domain.post.entity.PostStatus.*;

/**
 * PostStatus 상태 전이 규칙 테스트. 아래 표는 docs/state-diagrams.md 의 흐름도와 같아야 한다.
 * 규칙을 바꾸면 PostStatus.canTransitionTo, 이 표, 흐름도 문서를 함께 고친다.
 */
class PostStatusTransitionTest extends TransitionTestBase<PostStatus> {

    @Override
    protected Class<PostStatus> stateType() {
        return PostStatus.class;
    }

    @Override
    protected Map<PostStatus, Set<PostStatus>> allowed() {
        return Map.of(
            OPEN,        Set.of(MATCHED, EXPIRED, CANCELED),
            MATCHED,     Set.of(IN_PROGRESS, CANCELED, OPEN),
            IN_PROGRESS, Set.of(COMPLETED),
            COMPLETED,   Set.of(),
            CANCELED,    Set.of(),
            EXPIRED,     Set.of()
        );
    }

    @Override
    protected boolean canTransition(PostStatus from, PostStatus to) {
        return from.canTransitionTo(to);
    }
}
