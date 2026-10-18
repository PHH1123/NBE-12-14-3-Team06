/**
 * 결제 도메인 전용 setup() 헬퍼.
 * RsData 껍질 벗기기 같은 공통 동작은 loadtest/lib/rsdata.js 에 있습니다.
 */
import http from 'k6/http';
import { fail } from 'k6';
import { BASE_URL } from '../../lib/config.js';
import { authHeaders } from '../../lib/auth.js';
import { unwrap } from '../../lib/rsdata.js';
import { SEED_DONE_ORDER_PREFIX } from './config.js';

function push(bucket, payment) {
  bucket.ids.push(payment.id);
  bucket.amounts.push(payment.amount);
  bucket.postIds.push(payment.postId);
  bucket.orderIds.push(payment.orderId);
}

function emptyBucket() {
  return { ids: [], amounts: [], postIds: [], orderIds: [] };
}

/**
 * 시드가 만든 DONE 결제만 남깁니다.
 *
 * GET /api/v1/payments 는 BaseInitData 가 심은 결제까지 같이 돌려줍니다. 그 공고들은
 * MATCHED/COMPLETED 라서 취소하면 서버가 400-46 으로 올바르게 거절하고, 간혹 취소에
 * 성공하면 teardown.sql 이 지울 수 없는 잔여 행(재결제용 READY)을 남깁니다.
 * 시드 결제는 order_id 가 'LT-ORDER-D-' 로 시작하므로 그것만 골라 씁니다.
 */
export function onlySeedRows(bucket) {
  const filtered = emptyBucket();

  for (let i = 0; i < bucket.ids.length; i++) {
    const orderId = bucket.orderIds[i];
    if (orderId && orderId.indexOf(SEED_DONE_ORDER_PREFIX) === 0) {
      filtered.ids.push(bucket.ids[i]);
      filtered.amounts.push(bucket.amounts[i]);
      filtered.postIds.push(bucket.postIds[i]);
      filtered.orderIds.push(orderId);
    }
  }

  return filtered;
}

/**
 * setup() 에서 쓰는 데이터 수집기.
 *
 * GET /api/v1/payments 가 로그인한 의뢰인의 결제를 전부 돌려주므로, 여기서 한 번만
 * 긁어 상태별로 나눠 둡니다. CSV 파일이나 mysql 클라이언트가 따로 필요 없습니다.
 *
 * setup 이 돌려준 값은 VU 마다 복사되므로, 객체 배열 대신 평행 배열로 담아
 * 메모리를 아낍니다. (2만 건이면 객체 배열은 VU 당 수 MB)
 *
 * 시드가 2만 건이면 이 요청 하나가 수 초 걸립니다 — timeout 을 넉넉히 줍니다.
 */
export function collectPayments(username, options) {
  const timeout = (options && options.timeout) || '300s';

  const res = http.get(`${BASE_URL}/api/v1/payments`, {
    headers: authHeaders(username),
    timeout: timeout,
    tags: { endpoint: 'setup_collect' },
  });

  const payments = unwrap(res, `${username} 결제 목록 조회`);

  const ready = emptyBucket();
  const done = emptyBucket();
  const all = emptyBucket();

  for (const payment of payments) {
    push(all, payment);

    if (payment.paymentStatus === 'READY') {
      push(ready, payment);
    } else if (payment.paymentStatus === 'DONE') {
      push(done, payment);
    }
  }

  return { username, ready, done, all };
}

/**
 * 한 행(결제)을 한 번만 쓰는 시나리오(승인·취소)에 필요한 행 수를 미리 알려 줍니다.
 * 모자라면 중간부터 요청을 건너뛰게 되므로 실행 전에 경고를 띄웁니다.
 */
export function requireRows(bucket, label, needed) {
  if (bucket.ids.length === 0) {
    fail(`${label} 이 0건입니다. loadtest/payment/sql/seed.sql 을 먼저 실행하세요.`);
  }

  console.log(`${label}: ${bucket.ids.length}건 확보`);

  if (needed && bucket.ids.length < needed) {
    console.warn(
      `⚠️  ${label} ${bucket.ids.length}건 < 이번 실행이 쓸 ${needed}건 — ` +
        `모자라는 만큼은 요청 없이 건너뜁니다(rows_exhausted 메트릭). ` +
        `seed.sql 의 @ready_count 를 늘리거나 RATE/DURATION 을 줄이세요.`,
    );
  }
}
