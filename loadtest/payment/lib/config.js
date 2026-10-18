/**
 * 결제 도메인 전용 설정.
 * 도메인 무관한 값(BASE_URL, PASSWORD, STAGE 등)은 loadtest/lib/config.js 에 있습니다.
 *
 * 모든 값은 k6 실행 시 -e KEY=VALUE 로 덮어쓸 수 있습니다.
 */

/**
 * 결제 쓰기(승인·취소) 대상 공고를 몰아 둔 의뢰인.
 * payment/sql/seed.sql 의 @lt_client 와 반드시 같아야 합니다.
 */
export const LT_CLIENT = __ENV.LT_CLIENT || 'client03';

/**
 * 읽기 부하에 쓰는 의뢰인.
 * GET /api/v1/payments 는 의뢰인의 결제를 전부 돌려주므로(PaymentRepository.findAllByUserId),
 * 시드 결제가 2만 건 붙은 LT_CLIENT 로 읽기를 재면 응답 크기가 수치를 다 먹습니다.
 * 그래서 읽기는 BaseInitData 시드만 들고 있는 가벼운 계정으로 분리합니다.
 */
export const READ_CLIENT = __ENV.READ_CLIENT || 'client01';

/** payment/sql/seed.sql 이 DONE 결제에 심는 order_id 접두어 */
export const SEED_DONE_ORDER_PREFIX = 'LT-ORDER-D-';
