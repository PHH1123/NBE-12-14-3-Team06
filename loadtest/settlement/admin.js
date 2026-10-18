/**
 * 관리자 정산 조회 부하 테스트.
 *
 *   GET /api/v1/admin/settlements            목록 (Page)
 *   GET /api/v1/admin/settlements/analytics  기간별 통계
 *
 * ## 무엇을 보려는 건가
 * **통계 경로가 페이지네이션 없이 전건을 JVM 으로 끌어옵니다.**
 *
 *   SettlementPersistenceService.getAnalytics
 *     -> SettlementRepository.findAnalytics  (List<Settlement>, 페이징 없음)
 *     -> new SettlementAnalyticsResponse(list)  (자바 for 문으로 합산)
 *
 * 기본 조회 구간이 "최근 7일"이고 시드 정산의 createdAt 이 전부 지금이라,
 * 통계 요청 1건이 **시드 전건을 메모리로 로드**합니다. 집계를 SQL 로 내리지 않은 비용이
 * 부하에서 어떻게 드러나는지 보는 게 이 스크립트의 목적입니다.
 *
 * 목록은 Page 라 비교군이 됩니다 — 같은 테이블, 같은 구간인데 한쪽만 페이징합니다.
 *
 *   k6 run loadtest/settlement/admin.js                        # 20 iter/s, 5분
 *   k6 run -e STAGE=load -e RATE=50 -e DURATION=2m loadtest/settlement/admin.js
 *   k6 run -e STAGE=stress -e STAGE_DURATION=30s loadtest/settlement/admin.js
 *   k6 run -e ANALYTICS=0 loadtest/settlement/admin.js         # 목록만 (통계 제외)
 *
 * 조회 전용이라 상태를 바꾸지 않습니다. reset 없이 반복 실행해도 됩니다.
 */
import http from 'k6/http';
import exec from 'k6/execution';
import { check, group } from 'k6';
import { BASE_URL, SUMMARY_TREND_STATS, pickProfile } from '../lib/config.js';
import { authHeaders } from '../lib/auth.js';
import { ADMIN } from './lib/config.js';

/** stress 의 각 단계 길이 */
const STAGE_DURATION = __ENV.STAGE_DURATION || '2m';
/** 통계 경로를 같이 때릴지. 목록만 재고 싶으면 -e ANALYTICS=0 */
const WITH_ANALYTICS = __ENV.ANALYTICS !== '0';
/** 목록이 훑을 페이지 범위 */
const MAX_PAGE = Number(__ENV.MAX_PAGE || 50);
/** 목록 페이지 크기 (컨트롤러 기본값과 같게) */
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 20);

const profiles = {
  smoke: { executor: 'constant-vus', vus: 1, duration: '30s' },

  // 관리자 화면은 동시 사용자가 적으므로 낮은 RPS 로 시작합니다.
  load: {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.RATE || 20),
    timeUnit: '1s',
    duration: __ENV.DURATION || '5m',
    preAllocatedVUs: Number(__ENV.VUS || 40),
    maxVUs: Number(__ENV.MAX_VUS || 300),
  },

  stress: {
    executor: 'ramping-arrival-rate',
    timeUnit: '1s',
    startRate: 10,
    preAllocatedVUs: 60,
    maxVUs: 600,
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
    'http_req_failed{endpoint:admin_list}': ['rate<0.01'],
    'http_req_duration{endpoint:admin_list}': ['p(95)<500'],
    // 통계는 전건 로드라 느릴 것으로 보고 임계값을 느슨하게 잡습니다.
    // 깨지면 "느리다"가 아니라 "예상보다도 느리다"는 뜻입니다.
    'http_req_failed{endpoint:admin_analytics}': ['rate<0.01'],
    'http_req_duration{endpoint:admin_analytics}': ['p(95)<3000'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const i = exec.scenario.iterationInTest;

  group('관리자 목록', function () {
    const page = i % MAX_PAGE;

    const res = http.get(
      `${BASE_URL}/api/v1/admin/settlements?page=${page}&size=${PAGE_SIZE}`,
      { headers: authHeaders(ADMIN), tags: { endpoint: 'admin_list' } },
    );

    check(res, { '관리자 목록 200': (r) => r.status === 200 });
  });

  if (!WITH_ANALYTICS) return;

  group('관리자 통계', function () {
    const res = http.get(`${BASE_URL}/api/v1/admin/settlements/analytics`, {
      headers: authHeaders(ADMIN),
      tags: { endpoint: 'admin_analytics' },
      timeout: '120s',
    });

    check(res, {
      '관리자 통계 200': (r) => r.status === 200,
      '관리자 통계 건수 있음': (r) => r.json('data.settlementCount') > 0,
    });
  });
}
