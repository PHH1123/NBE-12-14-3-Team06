package com.back.nbe12142team06.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.EnumSet
import java.util.stream.Stream

/**
 * 상태 enum 의 전이 규칙(canTransitionTo)을 흐름도 스펙과 대조하는 공통 테스트.
 * 상속 클래스에서 stateType, allowed, canTransition 을 구현한다.
 * 종료 상태도 allowed 표에 빈 Set 으로 명시해야 한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class TransitionTestBase<S : Enum<S>> {

    protected abstract fun stateType(): Class<S>

    protected abstract fun allowed(): Map<S, Set<S>>

    protected abstract fun canTransition(from: S, to: S): Boolean

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    fun 모든_전이_조합이_흐름도와_일치한다(from: S, to: S) {
        val expected = allowed().getOrDefault(from, emptySet()).contains(to)

        assertThat(canTransition(from, to))
            .describedAs("%s -> %s 는 %s 이어야 한다", from, to, if (expected) "허용" else "거부")
            .isEqualTo(expected)
    }

    @Test
    fun 흐름도_표에_모든_상태가_명시되어_있다() {
        assertThat(allowed().keys)
            .describedAs("allowed() 에는 종료 상태를 포함해 모든 상태를 키로 적어야 한다 (상태를 추가했다면 표도 갱신)")
            .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(stateType()))
    }

    fun allPairs(): Stream<Arguments> {
        val states = stateType().enumConstants
        return states.flatMap { from -> states.map { to -> Arguments.of(from, to) } }.stream()
    }
}
