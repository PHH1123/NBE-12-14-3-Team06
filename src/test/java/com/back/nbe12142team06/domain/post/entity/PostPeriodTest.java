package com.back.nbe12142team06.domain.post.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostPeriodTest {

    private final LocalDateTime start =
            LocalDateTime.of(2026, 10, 21, 9, 0);

    private final LocalDateTime end =
            LocalDateTime.of(2026, 10, 21, 18, 0);

    private final Post post = Post.builder()
            .recruitStartAt(start)
            .recruitEndAt(end)
            .build();

    @Test
    @DisplayName("모집 시작 직전에는 지원할 수 없다")
    void beforeStart() {
        assertFalse(post.isWithinPeriod(start.minusNanos(1)));
    }

    @Test
    @DisplayName("모집 시작 시각에는 지원할 수 있다")
    void atStart() {
        assertTrue(post.isWithinPeriod(start));
    }

    @Test
    @DisplayName("모집 기간 중에는 지원할 수 있다")
    void duringPeriod() {
        assertTrue(post.isWithinPeriod(start.plusHours(1)));
    }

    @Test
    @DisplayName("모집 마감 시각에는 지원할 수 있다")
    void atEnd() {
        assertTrue(post.isWithinPeriod(end));
    }

    @Test
    @DisplayName("모집 마감 직후에는 지원할 수 없다")
    void afterEnd() {
        assertFalse(post.isWithinPeriod(end.plusNanos(1)));
    }
}
