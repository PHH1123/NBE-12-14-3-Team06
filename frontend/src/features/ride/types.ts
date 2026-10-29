/** 이동 방향. 갈 때(병원으로)·올 때(집으로) */
export type RideDirection = 'TO_HOSPITAL' | 'TO_HOME';

/** 이동수단 선택 상태 */
export type RideStatus = 'ACCEPTED' | 'IN_PROGRESS' | 'COMPLETED';

/** 이동수단. 공고 작성 시 필수 항목이라 이동 정보에는 항상 담겨 옵니다. */
export type RideSelect = 'WALK' | 'BUS' | 'TAXI' | 'OWN_CAR';

/** 백엔드 이동 응답 (RideResponse) — GET /api/v1/rides/posts/{postId} 목록의 한 항목 */
export type RideDto = {
  id: number;
  direction: RideDirection;
  status: RideStatus;
  selected: RideSelect;
  originLat: number;
  originLng: number;
  originAddr: string;
  destLat: number;
  destLng: number;
  destAddr: string;
};
