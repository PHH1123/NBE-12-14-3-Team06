package com.back.nbe12142team06.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상태 enum 의 전이 규칙(canTransitionTo)을 "흐름도(스펙)" 와 대조하는 공통 테스트.
 *
 * <p>사용법: 이 클래스를 상속하고 3개만 구현한다.
 * <ul>
 *   <li>{@link #stateType()} : 검사할 enum 클래스</li>
 *   <li>{@link #allowed()}   : docs/state-diagrams.md 의 화살표를 그대로 옮긴 표 (스펙). 종료 상태도 빈 Set 으로 적는다.</li>
 *   <li>{@link #canTransition(Enum, Enum)} : 실제 코드의 규칙 호출 (from.canTransitionTo(to))</li>
 * </ul>
 *
 * <p>검증하는 것
 * <ol>
 *   <li>모든 (from, to) 조합이 스펙 표와 일치한다 → 허용 전이는 통과하고, 그 외(불가능한 전이)는 모두 거부된다.</li>
 *   <li>스펙 표가 enum 의 모든 상태를 명시한다 → 상태를 추가하면 이 테스트가 깨져서 표를 갱신하게 된다.</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)   // @MethodSource 를 static 아닌 메서드로 쓰기 위해
public abstract class TransitionTestBase<S extends Enum<S>> {

    /** 검사할 상태 enum */
    protected abstract Class<S> stateType();

    /** 허용되는 전이 표 (흐름도의 화살표). 종료 상태(어디로도 못 감)도 빈 Set 으로 반드시 적는다. */
    protected abstract Map<S, Set<S>> allowed();

    /** 실제 코드가 판단하는 전이 가능 여부 */
    protected abstract boolean canTransition(S from, S to);

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void 모든_전이_조합이_흐름도와_일치한다(S from, S to) {
        boolean expected = allowed().getOrDefault(from, Set.of()).contains(to);

        assertThat(canTransition(from, to))
                .as("%s -> %s 는 %s 이어야 한다", from, to, expected ? "허용" : "거부")
                .isEqualTo(expected);
    }

    @Test
    void 흐름도_표에_모든_상태가_명시되어_있다() {
        assertThat(allowed().keySet())
                .as("allowed() 에는 종료 상태를 포함해 모든 상태를 키로 적어야 한다 (상태를 추가했다면 표도 갱신)")
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(stateType()));
    }

    Stream<Arguments> allPairs() {
        S[] states = stateType().getEnumConstants();
        return Arrays.stream(states)
                .flatMap(from -> Arrays.stream(states).map(to -> Arguments.of(from, to)));
    }
}
