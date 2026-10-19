/**
 * 정산(settlement) 도메인 전용 설정.
 * 도메인 무관한 값(BASE_URL, PASSWORD, STAGE 등)은 loadtest/lib/config.js 에 있습니다.
 *
 * 모든 값은 k6 실행 시 -e KEY=VALUE 로 덮어쓸 수 있습니다.
 */

/**
 * 시드 정산의 주인(동행 매니저).
 * settlement/sql/seed.sql 의 @lt_escort_name 과 반드시 같아야 합니다.
 *
 * /api/v1/settlements/** 는 ESCORT 역할만 통과합니다 (SecurityConfig).
 * 또 AccountDto 조회가 EscortProfile 을 조인하므로 계좌 정보가 있는 계정이어야 합니다.
 */
export const LT_ESCORT = __ENV.LT_ESCORT || 'escort01';

/**
 * 소유권 검증용 "남의 계정". 같은 ESCORT 역할이어야 403 을 볼 수 있습니다
 * (역할이 다르면 Security 단계에서 먼저 막혀 403 의 의미가 달라집니다).
 */
export const OTHER_ESCORT = __ENV.OTHER_ESCORT || 'escort02';

/** 관리자 정산 API(/api/v1/admin/settlements)용 계정 */
export const ADMIN = __ENV.ADMIN || 'admin01';

/** seed.sql 이 공고 제목에 붙이는 접두어 */
export const SEED_POST_PREFIX = '[LT-SETL-';

/**
 * 목록 API 의 조회 구간.
 *
 * SettlementController.settlementList 는 startDate/endDate 를 안 주면
 * now-1month ~ now 로 잡고, 그 구간을 **공고의 escortStartAt** 에 겁니다.
 * 시드는 escortStartAt 을 "지난 1~25일"에 흩어 두므로 기본값으로도 잡히지만,
 * setup 에서 전건을 긁을 때는 넉넉하게 넓혀 둡니다.
 */
// ⚠️ 2026-10-19 부터 컨트롤러 파라미터가 LocalDateTime -> LocalDate 로 바뀌었습니다.
//    '2000-01-01T00:00:00' 형식을 보내면 400 입니다. 날짜만 보내세요.
export const LIST_FROM = __ENV.LIST_FROM || '2000-01-01';
export const LIST_TO = __ENV.LIST_TO || '2100-01-01';

/** setup 이 정산 목록을 긁을 때 한 페이지 크기 */
export const LIST_PAGE_SIZE = Number(__ENV.LIST_PAGE_SIZE || 2000);

/** 부하 중 목록 조회가 쓸 페이지 크기 (기본값과 같게) */
export const READ_PAGE_SIZE = Number(__ENV.READ_PAGE_SIZE || 10);
