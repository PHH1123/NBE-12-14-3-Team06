/**
 * 결제 읽기 부하테스트 — 조회 3종
 *
 *   GET /api/v1/payments               내 결제 목록   (findAllByUserId)
 *   GET /api/v1/payments/{paymentId}   결제 상세      (findByIdFetchJoin + 소유자 검증)
 *   GET /api/v1/payments/posts/{postId} 공고별 READY  (findByPostIdAndUserIdAndReady)
 *
 * 상태를 바꾸지 않으므로 몇 번이고 다시 돌릴 수 있고, reset 도 필요 없습니다.
 * 승인·취소 부하의 기준선(baseline)으로 먼저 재 두는 게 좋습니다.
 *
 * ## 볼 거리
 * · 목록은 Payment 를 전부 돌려주고 PaymentResponse 가 post.id 를 건드립니다.
 *   default_batch_fetch_size=100 으로 완화돼 있지만, 결제가 많은 의뢰인에서
 *   응답 크기와 쿼리 수가 어떻게 변하는지 보세요. (data_received 지표)
 * · 상세는 fetch join 이 걸려 있어 쿼리 1번입니다. 세 경로 중 가장 빨라야 정상입니다.
 *
 *   k6 run loadtest/payment/read.js                       # load (100 rps, 10분)
 *   k6 run -e STAGE=stress loadtest/payment/read.js        # 한계점 탐색
 *   k6 run -e STAGE=soak loadtest/payment/read.js          # 1시간 내구성
 *   k6 run -e READ_CLIENT=client03 loadtest/payment/read.js # 시드 2만 건 계정으로 목록 부하
 */
import http from 'k6/http';
import { check, group } from 'k6';
import { BASE_URL, SUMMARY_TREND_STATS, pickProfile } from '../lib/config.js';
import { authHeaders } from '../lib/auth.js';
import { READ_CLIENT } from './lib/config.js';
import { collectPayments, requireRows } from './lib/payments.js';

/** stress / spike 의 각 단계 길이. 짧게 훑고 싶을 때 -e STAGE_DURATION=30s */
const STAGE_DURATION = __ENV.STAGE_DURATION || '2m';

const profiles = {
  smoke: { executor: 'constant-vus', vus: 1, duration: '30s' },

  load: {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.RATE || 100),
    timeUnit: '1s',
    duration: __ENV.DURATION || '10m',
    preAllocatedVUs: Number(__ENV.VUS || 50),
    maxVUs: Number(__ENV.MAX_VUS || 400),
  },

  stress: {
    executor: 'ramping-arrival-rate',
    timeUnit: '1s',
    startRate: 50,
    preAllocatedVUs: 80,
    maxVUs: 800,
    stages: [
      { target: 100, duration: STAGE_DURATION },
      { target: 200, duration: STAGE_DURATION },
      { target: 400, duration: STAGE_DURATION },
      { target: 800, duration: STAGE_DURATION },
      { target: 1200, duration: STAGE_DURATION },
    ],
  },

  // 커넥션 누수·GC 누적을 보는 장시간 테스트.
  // 토큰이 10분마다 만료되지만 authHeaders 가 알아서 재로그인합니다.
  soak: {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.RATE || 50),
    timeUnit: '1s',
    duration: __ENV.DURATION || '1h',
    preAllocatedVUs: 40,
    maxVUs: 200,
  },
};

export const options = {
  scenarios: pickProfile(profiles),
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: {
    'http_req_failed{endpoint:payment_list}': ['rate<0.01'],
    'http_req_failed{endpoint:payment_detail}': ['rate<0.01'],
    'http_req_failed{endpoint:payment_by_post}': ['rate<0.01'],
    // 목록은 건수에 비례해 커지므로 상세보다 느슨하게 잡습니다.
    'http_req_duration{endpoint:payment_list}': ['p(95)<800'],
    'http_req_duration{endpoint:payment_detail}': ['p(95)<200', 'p(99)<500'],
    'http_req_duration{endpoint:payment_by_post}': ['p(95)<300'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const set = collectPayments(READ_CLIENT);

  requireRows(set.all, `${READ_CLIENT} 의 결제`, 0);

  return { ids: set.all.ids, postIds: set.all.postIds };
}

export default function (data) {
  const pick = Math.floor(Math.random() * data.ids.length);

  group('목록', function () {
    const res = http.get(`${BASE_URL}/api/v1/payments`, {
      headers: authHeaders(READ_CLIENT),
      tags: { endpoint: 'payment_list' },
    });
    check(res, { '목록 200': (r) => r.status === 200 });
  });

  group('상세', function () {
    const res = http.get(`${BASE_URL}/api/v1/payments/${data.ids[pick]}`, {
      headers: authHeaders(READ_CLIENT),
      tags: { endpoint: 'payment_detail' },
    });
    check(res, { '상세 200': (r) => r.status === 200 });
  });

  group('공고별', function () {
    const res = http.get(`${BASE_URL}/api/v1/payments/posts/${data.postIds[pick]}`, {
      headers: authHeaders(READ_CLIENT),
      tags: { endpoint: 'payment_by_post' },
    });
    // READY 건이 없으면 data 가 null 이어도 200 입니다 (findByPostIdAndReady).
    check(res, { '공고별 200': (r) => r.status === 200 });
  });
}
