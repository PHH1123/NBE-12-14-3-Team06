/**
 * 결제 승인 부하테스트 — POST /api/v1/payments/{paymentId}/confirm
 *
 * 결제 도메인에서 가장 무거운 경로입니다. 한 번의 요청이
 *   1) 소유자 검증 조회(findByIdFetchJoin)
 *   2) 금액 검증
 *   3) 조건부 UPDATE (READY -> IN_PROGRESS)  ← 중복 승인 방어선
 *   4) 외부 결제 API 호출
 *   5) 상태 반영 UPDATE (-> DONE)
 *   6) 알림 INSERT
 * 를 모두 탑니다.
 *
 * ## 외부 API 는 우회됩니다
 * paymentKey 가 "INIT-PAYKEY-" 로 시작하면 TossPaymentClient 가 실제 토스 호출 없이
 * 성공을 흉내냅니다. 그래서 반복 부하가 가능하지만, **그만큼 외부 API 지연이 0** 입니다.
 * 실서비스에 가까운 수치를 보려면 그 분기에 고정 지연(200~500ms)을 넣고 다시 재세요.
 *
 * ## 한 행은 한 번만 쓸 수 있습니다
 * 승인은 READY -> DONE 으로 상태를 소모합니다. 그래서 반복마다 서로 다른 paymentId 를
 * 쓰도록 exec.scenario.iterationInTest(전체에서 유일한 반복 번호)로 인덱싱합니다.
 * 시드 행이 모자라면 rows_exhausted 카운터가 올라갑니다.
 *
 *   k6 run loadtest/payment/confirm.js                               # load (50 rps, 10분)
 *   k6 run -e STAGE=spike loadtest/payment/confirm.js
 *   k6 run -e STAGE=load -e RATE=100 -e DURATION=3m loadtest/payment/confirm.js
 *
 * 돌린 뒤에는 반드시 sql/reset.sql (또는 teardown + seed) 로 되돌리세요.
 * 되돌리지 않고 다시 돌리면 쓸 READY 행이 없어 전부 400 이 됩니다.
 */
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {
  BASE_URL,
  STAGE,
  SUMMARY_TREND_STATS,
  estimateIterations,
  pickProfile,
} from '../lib/config.js';
import { jsonAuthHeaders } from '../lib/auth.js';
import { peekStatusCode } from '../lib/rsdata.js';
import { LT_CLIENT } from './lib/config.js';
import { collectPayments, requireRows } from './lib/payments.js';

/** stress / spike 의 각 단계 길이. 짧게 훑고 싶을 때 -e STAGE_DURATION=30s */
const STAGE_DURATION = __ENV.STAGE_DURATION || '2m';

const profiles = {
  smoke: { executor: 'constant-vus', vus: 1, duration: '30s' },

  // 평균 부하: 목표 RPS 를 일정하게 유지하며 SLO 를 보는 본 테스트
  load: {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.RATE || 50),
    timeUnit: '1s',
    duration: __ENV.DURATION || '10m',
    preAllocatedVUs: Number(__ENV.VUS || 60),
    maxVUs: Number(__ENV.MAX_VUS || 300),
  },

  // 한계점 탐색: 어디서 p95 가 무너지고 에러가 터지는지
  stress: {
    executor: 'ramping-arrival-rate',
    timeUnit: '1s',
    startRate: 20,
    preAllocatedVUs: 60,
    maxVUs: 600,
    stages: [
      { target: 50, duration: STAGE_DURATION },
      { target: 100, duration: STAGE_DURATION },
      { target: 200, duration: STAGE_DURATION },
      { target: 400, duration: STAGE_DURATION },
    ],
  },

  // 급증: 평소 부하에서 갑자기 치솟았을 때 커넥션 풀이 버티는지
  spike: {
    executor: 'ramping-arrival-rate',
    timeUnit: '1s',
    startRate: 20,
    preAllocatedVUs: 60,
    maxVUs: 600,
    stages: [
      { target: 20, duration: '1m' },
      { target: 300, duration: '30s' },
      { target: 300, duration: '1m' },
      { target: 20, duration: '30s' },
      { target: 20, duration: '1m' },
    ],
  },
};

export const options = {
  scenarios: pickProfile(profiles),
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: {
    // 승인 경로만 떼어 본 임계값. 로그인·setup 요청은 태그가 달라 섞이지 않습니다.
    'http_req_failed{endpoint:payment_confirm}': ['rate<0.01'],
    'http_req_duration{endpoint:payment_confirm}': ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99'],
    // 시드가 모자라 빈 반복이 생기면 결과를 신뢰할 수 없으므로 실패로 봅니다.
    rows_exhausted: ['count==0'],
  },
};

/** 시드 행이 모자라 요청 없이 끝난 반복 수 */
const rowsExhausted = new Counter('rows_exhausted');

export function setup() {
  const set = collectPayments(LT_CLIENT);
  const needed = estimateIterations(profiles[STAGE]);

  requireRows(set.ready, `${LT_CLIENT} 의 READY 결제`, needed);

  return {
    ids: set.ready.ids,
    amounts: set.ready.amounts,
    // order_id / payment_key 는 unique 컬럼입니다. 실행마다 값을 바꿔 두면
    // reset 을 잊고 다시 돌려도 유니크 충돌로 엉뚱한 500 이 나지 않습니다.
    runId: `LT${Date.now()}`,
  };
}

export default function (data) {
  const index = exec.scenario.iterationInTest;

  if (index >= data.ids.length) {
    rowsExhausted.add(1);
    return;
  }

  const paymentId = data.ids[index];

  const body = JSON.stringify({
    // "INIT-PAYKEY-" 접두어가 실제 토스 호출을 건너뛰게 합니다 (TossPaymentClient).
    paymentKey: `INIT-PAYKEY-${data.runId}-${index}`,
    orderId: `ORDER-${data.runId}-${index}`,
    // PaymentService.verifyAmount 가 저장된 금액과 문자열 비교를 하므로 정확히 맞춰야 합니다.
    amount: String(data.amounts[index]),
  });

  const res = http.post(`${BASE_URL}/api/v1/payments/${paymentId}/confirm`, body, {
    headers: jsonAuthHeaders(LT_CLIENT),
    tags: { endpoint: 'payment_confirm' },
  });

  check(res, {
    '승인 200': (r) => r.status === 200,
    '승인 statusCode 200-41': (r) => peekStatusCode(r) === '200-41',
  });
}

export function teardown() {
  console.log(
    'READY 행을 소모했습니다. 다시 돌리기 전에 loadtest/payment/sql/reset.sql 을 실행하세요.',
  );
}
