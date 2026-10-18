/**
 * 정산 조회 부하 테스트 (동행 매니저 경로).
 *
 *   GET /api/v1/settlements        목록 (Page, 기본 10건, escortStartAt 으로 필터·정렬)
 *   GET /api/v1/settlements/{id}   상세 (정산 주인만)
 *
 * ## 무엇을 보려는 건가
 * 두 경로의 쿼리 모양이 다릅니다.
 *   · 목록(findAllByUserIdAndDate): User / Application / Post 를 **join fetch** 합니다.
 *     대신 정렬 키가 `application.post.escortStartAt` 이라 조인한 결과를 정렬해야 합니다.
 *   · 상세(findSettlement): `findById` 라 **fetch join 이 없습니다.** 이어서
 *     SettlementResponse 가 `settlement.getApplication().getPost()` 를 타며 LAZY 로딩이 터집니다.
 * 상세가 목록보다 느리면 이 차이 때문입니다.
 *
 *   k6 run loadtest/settlement/read.js                          # 100 iter/s, 10분
 *   k6 run -e STAGE=stress loadtest/settlement/read.js           # 50 → 1200 iter/s 단계 증가
 *   k6 run -e STAGE=stress -e STAGE_DURATION=30s loadtest/settlement/read.js
 *   k6 run -e STAGE=soak   loadtest/settlement/read.js           # 50 iter/s, 1시간
 *
 * 상태를 바꾸지 않으므로 reset 없이 반복해서 돌릴 수 있습니다.
 */
import http from 'k6/http';
import exec from 'k6/execution';
import { check, group } from 'k6';
import { BASE_URL, SUMMARY_TREND_STATS, pickProfile } from '../lib/config.js';
import { authHeaders } from '../lib/auth.js';
import { LT_ESCORT, READ_PAGE_SIZE } from './lib/config.js';
import { collectSettlements, requireRows } from './lib/settlements.js';

/** stress / soak 의 각 단계 길이. 짧게 훑고 싶을 때 -e STAGE_DURATION=30s */
const STAGE_DURATION = __ENV.STAGE_DURATION || '2m';

/** 목록 조회가 훑을 페이지 범위. 매번 0페이지만 치면 비현실적으로 유리합니다. */
const MAX_PAGE = Number(__ENV.MAX_PAGE || 50);

/**
 * 한 경로만 떼어 재고 싶을 때: -e ONLY=list 또는 -e ONLY=detail
 *
 * 둘을 같이 돌리면 느린 쪽이 커넥션 풀을 메워 빠른 쪽까지 끌어내립니다.
 * 어느 쪽이 원인인지 가르려면 따로 재야 합니다.
 */
const ONLY = __ENV.ONLY || 'both';

if (['both', 'list', 'detail'].indexOf(ONLY) < 0) {
  throw new Error(`ONLY=${ONLY} 는 없습니다. both / list / detail 중에 쓰세요.`);
}

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

  soak: {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.RATE || 50),
    timeUnit: '1s',
    duration: __ENV.DURATION || '1h',
    preAllocatedVUs: Number(__ENV.VUS || 40),
    maxVUs: Number(__ENV.MAX_VUS || 300),
  },
};

export const options = {
  scenarios: pickProfile(profiles),
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: {
    'http_req_failed{endpoint:settlement_list}': ['rate<0.01'],
    'http_req_failed{endpoint:settlement_detail}': ['rate<0.01'],
    'http_req_duration{endpoint:settlement_list}': ['p(95)<500'],
    'http_req_duration{endpoint:settlement_detail}': ['p(95)<300', 'p(99)<800'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const set = collectSettlements(LT_ESCORT);

  requireRows(set.all, `${LT_ESCORT} 의 정산`);

  return { ids: set.all.ids };
}

export default function (data) {
  const { ids } = data;
  // 반복마다 다른 행·다른 페이지를 집어 1차 캐시에 과하게 유리해지지 않게 합니다.
  const i = exec.scenario.iterationInTest;

  if (ONLY !== 'detail') group('목록', function () {
    const page = i % MAX_PAGE;

    const res = http.get(`${BASE_URL}/api/v1/settlements?page=${page}&size=${READ_PAGE_SIZE}`, {
      headers: authHeaders(LT_ESCORT),
      tags: { endpoint: 'settlement_list' },
    });

    check(res, { '목록 200': (r) => r.status === 200 });
  });

  if (ONLY !== 'list') group('상세', function () {
    const settlementId = ids[i % ids.length];

    const res = http.get(`${BASE_URL}/api/v1/settlements/${settlementId}`, {
      headers: authHeaders(LT_ESCORT),
      tags: { endpoint: 'settlement_detail' },
    });

    check(res, {
      '상세 200': (r) => r.status === 200,
      '상세 id 일치': (r) => r.json('data.id') === settlementId,
    });
  });
}
