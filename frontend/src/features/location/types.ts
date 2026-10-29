/* ── 서버 DTO ─────────────────────────────────────────────── */

/**
 * 한 사람의 가장 최근 위치 1건
 * updatedAt 은 시간대가 붙은 ISO-8601("2026-10-10T10:15:30+09:00" 또는 "...Z") 이어야 시각이 어긋나지 않습니다.
 */
export type LocationPointDto = {
  lat: number;
  lng: number;
  /** 위치 측정 오차(미터). 모르면 null */
  accuracy: number | null;
  updatedAt: string;
};

/**
 * GET /api/v1/applications/{applicationId}/location 응답
 * 동행 중인 의뢰인과 동행 매니저의 최근 위치입니다. 아직 위치를 보내지 않은 쪽은 null 입니다.
 */
export type LiveLocationsDto = {
  escort: LocationPointDto | null;
  client: LocationPointDto | null;
  /** 두 위치가 모두 최근이고 반경(100m) 안이면 true. 서버가 계산하며 출발 버튼 활성화 기준입니다. */
  nearby: boolean;
  /** 두 사람 사이의 거리(미터). 한쪽이라도 없거나 오래됐으면 null */
  distanceMeters: number | null;
};

/** POST /api/v1/applications/{applicationId}/location 요청 본문 */
export type SendLocationRequest = {
  lat: number;
  lng: number;
  /** 위치 측정 오차(미터) */
  accuracy?: number;
};

/* ── 화면용 ───────────────────────────────────────────────── */

/** 위도·경도 한 점 */
export type LatLng = {
  lat: number;
  lng: number;
};

/** 지도에 표시할 한 사람의 위치 */
export type LocationView = {
  position: LatLng;
  /** "오후 3:25:10" — 지도 "최근 업데이트"에 그대로 씁니다. */
  updatedAtLabel: string;
};

/** 화면에서 쓰는 두 사람의 위치와 가까움 여부 */
export type LiveLocations = {
  escort?: LocationView;
  client?: LocationView;
  nearby: boolean;
  /** 두 사람 사이의 거리(미터). 모르면 undefined */
  distanceMeters?: number;
};

/** 지도에서 누구의 위치인지. 마커 색·이름표가 달라집니다. */
export type LocationRole = 'escort' | 'client';

/**
 * 이 기기의 위치 공유 상태
 * - idle: 공유 시간이 아님 / starting: 위치를 처음 받아오는 중 / sharing: 서버로 전송 중
 * - denied: 위치 권한 거부 / unsupported: 기기가 위치 기능을 지원하지 않음 / error: 전송·측정 실패
 */
export type ShareStatus = 'idle' | 'starting' | 'sharing' | 'denied' | 'unsupported' | 'error';
