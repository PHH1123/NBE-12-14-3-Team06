/**
 * 이동수단(ride) 도메인 전용 설정.
 * 도메인 무관한 값(BASE_URL, PASSWORD, STAGE 등)은 loadtest/lib/config.js 에 있습니다.
 *
 * 모든 값은 k6 실행 시 -e KEY=VALUE 로 덮어쓸 수 있습니다.
 */

/**
 * 시드 공고의 주인. ride/sql/seed.sql 의 @lt_client 와 반드시 같아야 합니다.
 * 상세 조회(GET /rides/{rideId})가 공고 작성자만 통과시키므로(RideService.validUser)
 * 부하는 이 계정으로 겁니다.
 */
export const LT_CLIENT = __ENV.LT_CLIENT || 'client03';

/**
 * 소유권 검증용 "남의 계정".
 * smoke 에서 LT_CLIENT 의 ride 를 이 계정으로 찔러 403 이 나오는지 봅니다.
 */
export const OTHER_CLIENT = __ENV.OTHER_CLIENT || 'client01';

/** ride/sql/seed.sql 이 공고 제목에 붙이는 접두어 */
export const SEED_POST_PREFIX = '[LT-RIDE]';

/**
 * setup() 이 ride id 를 모으려고 훑을 공고 수.
 *
 * ride 도메인에는 "내 이동수단 목록" 엔드포인트가 없어서, 공고를 먼저 찾고
 * 공고마다 GET /rides/posts/{postId} 를 호출해야 ride id 를 알 수 있습니다.
 * 그 호출이 곧 setup 비용이라 무한정 늘릴 수 없습니다. 공고 1건당 ride 2건이므로
 * 기본값 300 이면 ride 600건을 확보합니다.
 */
export const COLLECT_POSTS = Number(__ENV.COLLECT_POSTS || 300);

/** /api/v1/posts/me 한 페이지 크기. 시드 공고를 한 번에 긁어오려고 크게 잡습니다. */
export const POSTS_PAGE_SIZE = Number(__ENV.POSTS_PAGE_SIZE || 2000);
