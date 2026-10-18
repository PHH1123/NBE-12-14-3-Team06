/**
 * 결제 도메인 스모크 테스트 — 1 VU 로 모든 경로를 한 바퀴만 돌립니다.
 *
 * 부하를 재는 게 목적이 아니고, 본 테스트를 돌리기 전에
 *   · 로그인 쿠키가 먹는지
 *   · seed.sql 이 제대로 들어갔는지 (READY / DONE 결제가 있는지)
 *   · 승인·취소가 실제로 통과하는지 (INIT-PAYKEY- 우회가 먹는지)
 * 를 확인하는 용도입니다. 여기서 실패하면 다른 스크립트는 전부 의미가 없습니다.
 *
 *   k6 run loadtest/payment/smoke.js
 *
 * 승인·취소는 각 1건씩 상태를 소모합니다. 쓰고 나면 sql/reset.sql 로 되돌리세요.
 * (버킷의 "마지막" 행을 쓰므로, 0번부터 쓰는 부하 스크립트와 겹치지 않습니다.)
 */
import http from 'k6/http';
import { check, group, fail } from 'k6';
import { BASE_URL, SUMMARY_TREND_STATS } from '../lib/config.js';
import { authHeaders, jsonAuthHeaders } from '../lib/auth.js';
import { peekStatusCode, unwrap } from '../lib/rsdata.js';
import { LT_CLIENT, READ_CLIENT } from './lib/config.js';
import { collectPayments, onlySeedRows } from './lib/payments.js';

export const options = {
  scenarios: {
    main: { executor: 'shared-iterations', vus: 1, iterations: 1, maxDuration: '10m' },
  },
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: {
    // 스모크는 한 건이라도 깨지면 실패여야 합니다.
    checks: ['rate==1.00'],
  },
};

export function setup() {
  const readSet = collectPayments(READ_CLIENT);
  const writeSet = collectPayments(LT_CLIENT);
  // 취소는 시드가 만든 DONE 결제만 씁니다 (BaseInitData 행을 취소하면 잔여 행이 남습니다).
  writeSet.done = onlySeedRows(writeSet.done);

  if (readSet.all.ids.length === 0) {
    fail(`${READ_CLIENT} 의 결제가 0건입니다. BaseInitData 가 돌았는지 확인하세요.`);
  }
  if (writeSet.ready.ids.length === 0) {
    fail(`${LT_CLIENT} 의 READY 결제가 0건입니다. loadtest/payment/sql/seed.sql 을 실행하세요.`);
  }
  if (writeSet.done.ids.length === 0) {
    fail(`${LT_CLIENT} 의 시드 DONE 결제가 0건입니다. loadtest/payment/sql/seed.sql 을 실행하세요.`);
  }

  console.log(
    `읽기용 ${READ_CLIENT}: 전체 ${readSet.all.ids.length}건 / ` +
      `쓰기용 ${LT_CLIENT}: READY ${writeSet.ready.ids.length}건, DONE ${writeSet.done.ids.length}건`,
  );

  return { readSet, writeSet, runId: `SMOKE-${Date.now()}` };
}

export default function (data) {
  const { readSet, writeSet, runId } = data;

  group('읽기 경로', function () {
    const listRes = http.get(`${BASE_URL}/api/v1/payments`, {
      headers: authHeaders(READ_CLIENT),
      tags: { endpoint: 'payment_list' },
    });
    check(listRes, {
      '목록 200': (r) => r.status === 200,
      '목록 statusCode 200-43': (r) => peekStatusCode(r) === '200-43',
    });

    const paymentId = readSet.all.ids[0];
    const detailRes = http.get(`${BASE_URL}/api/v1/payments/${paymentId}`, {
      headers: authHeaders(READ_CLIENT),
      tags: { endpoint: 'payment_detail' },
    });
    check(detailRes, {
      '상세 200': (r) => r.status === 200,
      '상세 id 일치': (r) => r.json('data.id') === paymentId,
    });

    const postId = readSet.all.postIds[0];
    const byPostRes = http.get(`${BASE_URL}/api/v1/payments/posts/${postId}`, {
      headers: authHeaders(READ_CLIENT),
      tags: { endpoint: 'payment_by_post' },
    });
    // READY 가 없으면 data 가 null 로 와도 정상입니다 (PaymentService.findByPostIdAndReady).
    check(byPostRes, { '공고별 200': (r) => r.status === 200 });
  });

  group('결제 승인', function () {
    const last = writeSet.ready.ids.length - 1;
    const paymentId = writeSet.ready.ids[last];
    const amount = String(writeSet.ready.amounts[last]);

    const body = JSON.stringify({
      // INIT-PAYKEY- 접두어라 TossPaymentClient 가 실제 토스 API 를 건너뜁니다.
      paymentKey: `INIT-PAYKEY-${runId}`,
      orderId: `ORDER-${runId}`,
      amount: amount,
    });

    const res = http.post(`${BASE_URL}/api/v1/payments/${paymentId}/confirm`, body, {
      headers: jsonAuthHeaders(LT_CLIENT),
      tags: { endpoint: 'payment_confirm' },
    });
    check(res, {
      '승인 200': (r) => r.status === 200,
      '승인 statusCode 200-41': (r) => peekStatusCode(r) === '200-41',
    });

    // 승인 뒤 상태가 DONE 인지 확인
    const after = http.get(`${BASE_URL}/api/v1/payments/${paymentId}`, {
      headers: authHeaders(LT_CLIENT),
      tags: { endpoint: 'payment_detail' },
    });
    check(after, { '승인 후 DONE': (r) => r.json('data.paymentStatus') === 'DONE' });

    // 같은 건을 한 번 더 승인하면 400-41 로 막혀야 합니다.
    const again = http.post(`${BASE_URL}/api/v1/payments/${paymentId}/confirm`, body, {
      headers: jsonAuthHeaders(LT_CLIENT),
      tags: { endpoint: 'payment_confirm_duplicate' },
    });
    check(again, {
      '중복 승인 400': (r) => r.status === 400,
      '중복 승인 statusCode 400-41': (r) => peekStatusCode(r) === '400-41',
    });
  });

  group('결제 취소', function () {
    const last = writeSet.done.ids.length - 1;
    const paymentId = writeSet.done.ids[last];

    const res = http.del(
      `${BASE_URL}/api/v1/payments/${paymentId}`,
      JSON.stringify({ cancelReason: '부하테스트 스모크' }),
      { headers: jsonAuthHeaders(LT_CLIENT), tags: { endpoint: 'payment_cancel' } },
    );
    // 컨트롤러가 "204-41" 을 돌려주고 ResponseAspect 가 204 로 세팅합니다. 200 이 아닙니다.
    check(res, { '취소 204': (r) => r.status === 204 });
  });
}

export function teardown() {
  console.log('스모크 통과. 쓴 행을 되돌리려면: loadtest/payment/sql/reset.sql');
}
