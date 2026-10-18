/**
 * 정산 요청 부하 테스트.
 *
 *   POST /api/v1/settlements/{settlementId}
 *
 * ## 무엇을 보려는 건가
 * SettlementService.request 는 **@Transactional 이 아닙니다.**
 * 안에서 SettlementPersistenceService 의 메서드들이 각자 트랜잭션을 엽니다.
 *   ① findAccountDto      계좌 조회        (readOnly 트랜잭션)
 *   ② processingSettlement 조건부 UPDATE    (쓰기 트랜잭션)
 *   ─ 외부 정산 API 호출 (MockSettlementClient — 트랜잭션 밖, 올바름)
 *   ③ updateSettlement    COMPLETED 반영   (쓰기 트랜잭션)
 *   ④ findById            알림용 재조회     (readOnly 트랜잭션)
 *   ⑤ noticeService.send  알림 저장        (쓰기 트랜잭션)
 * 요청 1건이 커넥션을 몇 번 잡는지를 Grafana "요청 1건당 커넥션 획득 횟수"로 봅니다.
 *
 *   k6 run loadtest/settlement/request.js                      # 20 rps, 5분
 *   k6 run -e STAGE=spike loadtest/settlement/request.js       # 20 → 300 rps 급증
 *   k6 run -e STAGE=load -e RATE=50 -e DURATION=2m loadtest/settlement/request.js
 *
 * ⚠️ PENDING 정산을 한 건씩 소모합니다. 돌린 뒤 반드시 sql/reset.sql.
 *    되돌리지 않고 다시 돌리면 쓸 행이 없어 전부 404-71 이 됩니다.
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
import { authHeaders } from '../lib/auth.js';
import { peekStatusCode } from '../lib/rsdata.js';
import { LT_ESCORT } from './lib/config.js';
import { collectSettlements, requireRows } from './lib/settlements.js';

/** stress / spike 의 각 단계 길이. 짧게 훑고 싶을 때 -e STAGE_DURATION=30s */
const STAGE_DURATION = __ENV.STAGE_DURATION || '2m';

const profiles = {
  smoke: { executor: 'constant-vus', vus: 1, duration: '30s' },

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
    // 요청 경로만 떼어 본 임계값. 로그인·setup 요청은 태그가 달라 섞이지 않습니다.
    'http_req_failed{endpoint:settlement_request}': ['rate<0.01'],
    'http_req_duration{endpoint:settlement_request}': ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99'],
    rows_exhausted: ['count==0'],
  },
};

/** 쓸 PENDING 행이 떨어져 건너뛴 반복 수 — 0이 아니면 시드가 모자란 것입니다 */
const rowsExhausted = new Counter('rows_exhausted');

export function setup() {
  const set = collectSettlements(LT_ESCORT);
  const needed = estimateIterations(options.scenarios.main);

  requireRows(set.pending, `${LT_ESCORT} 의 요청 가능(PENDING·FAILED) 정산`, needed);

  return { ids: set.pending.ids };
}

export default function (data) {
  const index = exec.scenario.iterationInTest;

  // 한 행은 한 번만 쓸 수 있으므로 반복마다 다음 행으로 넘어갑니다.
  if (index >= data.ids.length) {
    rowsExhausted.add(1);
    return;
  }

  const settlementId = data.ids[index];

  const res = http.post(`${BASE_URL}/api/v1/settlements/${settlementId}`, null, {
    headers: authHeaders(LT_ESCORT),
    tags: { endpoint: 'settlement_request' },
  });

  check(res, {
    '요청 200': (r) => r.status === 200,
    '요청 statusCode 200-71': (r) => peekStatusCode(r) === '200-71',
  });
}

export function teardown() {
  console.log(
    `PENDING 정산을 소모했습니다. 다시 돌리기 전에 loadtest/settlement/sql/reset.sql 을 실행하세요. (STAGE=${STAGE})`,
  );
}
