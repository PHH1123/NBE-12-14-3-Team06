/**
 * 결제 취소 부하테스트 — DELETE /api/v1/payments/{paymentId}
 *
 * 승인보다 한 요청이 하는 일이 더 많습니다.
 *   1) 소유자 검증 + 공고 상태 검증(OPEN/EXPIRED 만 취소 가능)
 *   2) 외부 결제 취소 API 호출
 *   3) 기존 결제 CANCELED + 새 READY 결제 INSERT (재결제용)
 *   4) **그 공고의 PENDING 지원을 전부 REJECTED 로 변경** (PaymentPersistenceService.rejectApplication)
 *   5) 알림 INSERT
 *
 * ## 핵심은 4번입니다
 * rejectApplication 은 PENDING 지원을 전부 가져와 하나씩 더티 체킹으로 바꿉니다.
 * 즉 **지원자 수에 비례해 UPDATE 가 늘어납니다.** 지원자 0명인 공고로 재면
 * 이 비용이 아예 안 잡히므로, sql/seed-applications.sql 로 지원자를 심고
 * 0명 / 50명 / 200명 으로 나눠 재서 기울기를 보는 게 이 스크립트의 목적입니다.
 *
 *   # 1) 지원자 0명 기준선
 *   k6 run loadtest/payment/cancel.js
 *
 *   # 2) sql/seed-applications.sql (@applicants=50) 실행 후 같은 조건으로 재측정
 *   k6 run loadtest/payment/cancel.js
 *
 *   # 3) @applicants=200 으로 올려 또 재측정 -> p95 가 선형으로 늘어나는지 확인
 *
 * DONE 결제를 한 건씩 소모합니다. 돌린 뒤 sql/reset.sql.
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
import { collectPayments, onlySeedRows, requireRows } from './lib/payments.js';

/** stress / spike 의 각 단계 길이. 짧게 훑고 싶을 때 -e STAGE_DURATION=30s */
const STAGE_DURATION = __ENV.STAGE_DURATION || '2m';

const profiles = {
  smoke: { executor: 'constant-vus', vus: 1, duration: '30s' },

  // 취소는 쓰기가 많아 승인보다 낮은 RPS 로 시작합니다.
  load: {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.RATE || 20),
    timeUnit: '1s',
    duration: __ENV.DURATION || '5m',
    preAllocatedVUs: Number(__ENV.VUS || 40),
    maxVUs: Number(__ENV.MAX_VUS || 200),
  },

  stress: {
    executor: 'ramping-arrival-rate',
    timeUnit: '1s',
    startRate: 10,
    preAllocatedVUs: 40,
    maxVUs: 400,
    stages: [
      { target: 20, duration: STAGE_DURATION },
      { target: 50, duration: STAGE_DURATION },
      { target: 100, duration: STAGE_DURATION },
      { target: 200, duration: STAGE_DURATION },
    ],
  },
};

export const options = {
  scenarios: pickProfile(profiles),
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: {
    'http_req_failed{endpoint:payment_cancel}': ['rate<0.01'],
    'http_req_duration{endpoint:payment_cancel}': ['p(95)<800', 'p(99)<1500'],
    checks: ['rate>0.99'],
    rows_exhausted: ['count==0'],
  },
};

const rowsExhausted = new Counter('rows_exhausted');

/**
 * 구조적으로 취소할 수 없는 행 수.
 *
 * collectPayments 는 LT_CLIENT 의 DONE 결제를 전부 담는데, 거기에는 BaseInitData 가
 * 심은 시드 결제도 섞입니다. 그 공고들은 MATCHED/COMPLETED 등이라 서버가 400-46
 * ("이미 종료된 동행이거나 매칭되었기 때문에...")으로 올바르게 거절합니다.
 * 서버 실패가 아니므로 체크를 깨뜨리지 않고 따로 셉니다.
 */
const rowsNotCancelable = new Counter('rows_not_cancelable');

export function setup() {
  const set = collectPayments(LT_CLIENT);
  const needed = estimateIterations(profiles[STAGE]);

  // BaseInitData 결제를 섞으면 취소 불가 응답과 teardown 이 못 지우는 잔여 행이 생깁니다.
  const seedDone = onlySeedRows(set.done);

  requireRows(seedDone, `${LT_CLIENT} 의 시드 DONE 결제`, needed);

  return { ids: seedDone.ids };
}

export default function (data) {
  const index = exec.scenario.iterationInTest;

  if (index >= data.ids.length) {
    rowsExhausted.add(1);
    return;
  }

  const res = http.del(
    `${BASE_URL}/api/v1/payments/${data.ids[index]}`,
    JSON.stringify({ cancelReason: '부하테스트 취소' }),
    { headers: jsonAuthHeaders(LT_CLIENT), tags: { endpoint: 'payment_cancel' } },
  );

  // 컨트롤러가 RsData("204-41") 을 돌려주고 ResponseAspect 가 HTTP 204 로 세팅합니다.
  // 200 을 기대하면 전부 실패로 잡히니 주의하세요.
  // 취소 불가 상태의 공고는 측정 대상이 아니므로 체크에서 제외합니다.
  if (res.status === 400 && peekStatusCode(res) === '400-46') {
    rowsNotCancelable.add(1);
    return;
  }

  const ok = check(res, { '취소 204': (r) => r.status === 204 });

  // 그 밖의 실패는 어떤 응답이었는지 남겨 둡니다.
  if (!ok) {
    console.error(
      `취소 실패 paymentId=${data.ids[index]} HTTP=${res.status} body=${String(res.body).slice(0, 200)}`,
    );
  }
}

export function teardown() {
  console.log(
    'DONE 행을 소모하고 재결제용 READY 행을 새로 만들었습니다. loadtest/payment/sql/reset.sql 로 되돌리세요.',
  );
}
