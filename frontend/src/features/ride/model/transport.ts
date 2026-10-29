import type { RideDirection, RideDto, RideSelect } from '../types';

/** 공고 작성 폼의 이동수단 선택지(client/model/postForm.ts)와 같은 문구를 씁니다. */
const RIDE_SELECT_LABELS: Record<string, string> = { WALK: '도보', BUS: '대중교통', TAXI: '택시', OWN_CAR: '자가용' };

/**
 * 방향별로 고른 이동수단. 그 방향 이동 정보가 없으면 null 입니다.
 * 표시용 문구(formatTransport)와 달리 ENUM 값을 그대로 넘겨, 화면이 "택시인지"로 분기할 수 있게 합니다.
 */
export function selectedByDirection(rides: RideDto[]): Record<RideDirection, RideSelect | null> {
  const selected = (direction: RideDirection) => rides.find((item) => item.direction === direction)?.selected ?? null;
  return { TO_HOSPITAL: selected('TO_HOSPITAL'), TO_HOME: selected('TO_HOME') };
}

/** 갈 때(TO_HOSPITAL)·올 때(TO_HOME) 이동수단을 한 줄로 합칩니다. 그 방향 이동 정보가 없으면 "미정"으로 표시합니다. */
export function formatTransport(rides: RideDto[]): string {
  const selected = selectedByDirection(rides);
  const label = (direction: RideDirection) => {
    const value = selected[direction];
    return value ? (RIDE_SELECT_LABELS[value] ?? value) : '미정';
  };
  return `갈 때 ${label('TO_HOSPITAL')} · 올 때 ${label('TO_HOME')}`;
}
