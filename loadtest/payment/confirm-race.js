/**
 * 결제 승인 동시성(정합성) 테스트 — 같은 결제를 동시에 여러 번 승인해 봅니다.
 *
 * 성능이 아니라 **정합성**을 보는 테스트입니다. 결제 도메인에서 돈이 두 번 빠지는
 * 사고가 나는 지점이라, 부하테스트 세트에서 가장 먼저 돌려야 하는 항목입니다.
 *
 * ## 무엇을 검증하나
 * PaymentPersistenceService.confirmUpdateStatus 는
 *   UPDATE payment SET payment_status='IN_PROGRESS'
 *    WHERE id=? AND payment_status='READY'
 * 라는 조건부 UPDATE 로, 영향 행이 0이면 400-44 로 막습니다.
 * 즉 이 한 줄이 CAS(compare-and-set) 역할을 하는 유일한 중복 승인 방어선입니다.
 * 동시에 N 개를 때렸을 때 **정확히 1건만 200** 이고 나머지는 전부 400-44 여야 합니다.
 *
 * 동시 요청마다 paymentKey / orderId 를 다르게 보냅니다. 같은 값을 쓰면 방어선이
 * 뚫렸을 때 unique 제약 위반(500)으로 가려져 버려서, 버그를 못 잡습니다.
 *
 *   k6 run loadtest/payment/confirm-race.js
 *   k6 run -e CONCURRENCY=20 -e ROUNDS=50 loadtest/payment/confirm-race.js
 *
 * ROUNDS 건의 READY 결제를 소모합니다. 돌린 뒤 sql/reset.sql.
 */
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, SUMMARY_TREND_STATS } from '../lib/config.js';
import { jsonAuthHeaders } from '../lib/auth.js';
import { peekStatusCode } from '../lib/rsdata.js';
import { LT_CLIENT } from './lib/config.js';
import { collectPayments, requireRows } from './lib/payments.js';

/** 한 결제에 동시에 때릴 요청 수 */
const CONCURRENCY = Number(__ENV.CONCURRENCY || 10);
/** 몇 개의 결제로 반복할지 */
const ROUNDS = Number(__ENV.ROUNDS || 20);

export const options = {
  scenarios: {
    // VU 1개가 http.batch 로 동시에 N 개를 날립니다.
    // VU 를 여러 개 띄워 같은 결제를 노리게 하는 것보다 타이밍이 확실히 겹칩니다.
    main: { executor: 'per-vu-iterations', vus: 1, iterations: ROUNDS, maxDuration: '15m' },
  },
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: {
    // 한 라운드라도 2건 이상 승인되면 중복 결제 버그입니다. 타협 없이 100%.
    'checks{check:정확히 1건만 승인(200)}': ['rate==1.00'],
    'checks{check:나머지는 전부 400 으로 막힘}': ['rate==1.00'],
    'checks{check:최종 상태 DONE}': ['rate==1.00'],
    'checks{check:금액 그대로}': ['rate==1.00'],
    duplicate_approvals: ['count==0'],
  },
};

/** 한 결제에 2건 이상 승인이 통과한 횟수 — 0이 아니면 중복 결제 */
const duplicateApprovals = new Counter('duplicate_approvals');

export function setup() {
  const set = collectPayments(LT_CLIENT);

  requireRows(set.ready, `${LT_CLIENT} 의 READY 결제`, ROUNDS);

  return {
    ids: set.ready.ids,
    amounts: set.ready.amounts,
    runId: `RACE${Date.now()}`,
  };
}

export default function (data) {
  const index = exec.scenario.iterationInTest;
  if (index >= data.ids.length) return;

  const paymentId = data.ids[index];
  const amount = String(data.amounts[index]);
  const headers = jsonAuthHeaders(LT_CLIENT);
  const url = `${BASE_URL}/api/v1/payments/${paymentId}/confirm`;

  const requests = [];
  for (let i = 0; i < CONCURRENCY; i++) {
    requests.push([
      'POST',
      url,
      JSON.stringify({
        paymentKey: `INIT-PAYKEY-${data.runId}-${index}-${i}`,
        orderId: `ORDER-${data.runId}-${index}-${i}`,
        amount: amount,
      }),
      { headers: headers, tags: { endpoint: 'payment_confirm_race' } },
    ]);
  }

  const responses = http.batch(requests);

  let approved = 0;
  let blocked = 0;

  for (const res of responses) {
    if (res.status === 200) {
      approved++;
      continue;
    }

    // 막히는 경로는 두 가지입니다. 어느 쪽이든 "중복 승인을 거절했다"는 뜻입니다.
    //   400-44: 조건부 UPDATE 가 0행 (경쟁에서 진 경우. 대부분 이쪽)
    //   400-41: 조회 시점에 이미 DONE (승자가 먼저 끝낸 경우)
    const code = peekStatusCode(res);
    if (res.status === 400 && (code === '400-44' || code === '400-41')) {
      blocked++;
    }
  }

  if (approved > 1) {
    duplicateApprovals.add(1);
    console.error(
      `❌ paymentId=${paymentId} 에 승인이 ${approved}건 통과했습니다 (동시 ${CONCURRENCY}건). ` +
        `중복 결제 방어선이 뚫렸습니다.`,
    );
  }

  if (blocked !== CONCURRENCY - approved) {
    console.error(
      `❌ paymentId=${paymentId}: 승인 ${approved}건, 정상 거절 ${blocked}건 — ` +
        `설명되지 않는 응답이 ${CONCURRENCY - approved - blocked}건 있습니다 ` +
        `(500 이나 유니크 충돌일 수 있습니다).`,
    );
  }

  check(null, {
    '정확히 1건만 승인(200)': () => approved === 1,
    '나머지는 전부 400 으로 막힘': () => blocked === CONCURRENCY - 1,
  });

  // 승인 경쟁 와중에 금액이나 상태가 깨지지 않았는지 사후 확인
  const after = http.get(`${BASE_URL}/api/v1/payments/${paymentId}`, {
    headers: headers,
    tags: { endpoint: 'payment_detail' },
  });

  check(after, {
    '최종 상태 DONE': (r) => r.json('data.paymentStatus') === 'DONE',
    '금액 그대로': (r) => String(r.json('data.amount')) === amount,
  });
}

export function teardown() {
  console.log(
    `${ROUNDS}건의 READY 결제를 소모했습니다. loadtest/payment/sql/reset.sql 로 되돌리세요.`,
  );
}
