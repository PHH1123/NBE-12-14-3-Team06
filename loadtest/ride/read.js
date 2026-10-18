/**
 * 이동수단 조회 부하 테스트.
 *
 * 노출된 API 가 GET 둘뿐이라 이 도메인의 부하테스트는 전부 읽기입니다.
 *   GET /api/v1/rides/posts/{postId}  공고별 목록 (로그인만 하면 통과)
 *   GET /api/v1/rides/{rideId}        상세 (공고 작성자만)
 *
 * ## 무엇을 보려는 건가
 * 두 경로 모두 **Post 를 LAZY 로 끌어옵니다.**
 *   · 목록: RideResponse 가 ride.getPost() 의 좌표·주소를 읽습니다.
 *     리포지토리 쿼리가 `join Post p` 를 쓰지만 fetch join 이 아니라 **추가 SELECT 가 나갑니다.**
 *   · 상세: RideService.validUser 가 ride.getPost().getClient() 를 타고,
 *     이어서 RideResponse 가 같은 post 를 읽습니다.
 * 요청 1건이 쿼리 몇 개로 번역되는지는 Grafana 의 "요청 1건당 커넥션 획득 횟수"와
 * 서버 평균 응답시간으로 봅니다.
 *
 *   k6 run loadtest/ride/read.js                          # 100 iter/s, 10분
 *   k6 run -e STAGE=stress loadtest/ride/read.js           # 50 → 1200 iter/s 단계 증가
 *   k6 run -e STAGE=stress -e STAGE_DURATION=30s loadtest/ride/read.js
 *   k6 run -e STAGE=soak   loadtest/ride/read.js           # 50 iter/s, 1시간
 *
 * 상태를 바꾸지 않으므로 reset 없이 반복해서 돌릴 수 있습니다.
 */
import http from 'k6/http';
import exec from 'k6/execution';
import { check, group } from 'k6';
import { BASE_URL, SUMMARY_TREND_STATS, pickProfile } from '../lib/config.js';
import { authHeaders } from '../lib/auth.js';
import { LT_CLIENT } from './lib/config.js';
import { collectSeedPosts, collectRides, requireSeed } from './lib/rides.js';

/** stress / soak 의 각 단계 길이. 짧게 훑고 싶을 때 -e STAGE_DURATION=30s */
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
    // 조회 경로별로 임계값을 따로 둡니다. 로그인·setup 요청은 태그가 달라 섞이지 않습니다.
    'http_req_failed{endpoint:ride_list}': ['rate<0.01'],
    'http_req_failed{endpoint:ride_detail}': ['rate<0.01'],
    'http_req_duration{endpoint:ride_list}': ['p(95)<300'],
    'http_req_duration{endpoint:ride_detail}': ['p(95)<300', 'p(99)<800'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const postIds = collectSeedPosts(LT_CLIENT);
  const rideIds = collectRides(LT_CLIENT, postIds);

  requireSeed(postIds, rideIds);

  return { postIds, rideIds };
}

export default function (data) {
  const { postIds, rideIds } = data;
  // 반복마다 다른 행을 집어 1차 캐시·쿼리 캐시에 과하게 유리해지지 않게 합니다.
  const i = exec.scenario.iterationInTest;

  group('공고별 목록', function () {
    const postId = postIds[i % postIds.length];

    const res = http.get(`${BASE_URL}/api/v1/rides/posts/${postId}`, {
      headers: authHeaders(LT_CLIENT),
      tags: { endpoint: 'ride_list' },
    });

    check(res, {
      '목록 200': (r) => r.status === 200,
      '목록 2건': (r) => r.json('data').length === 2,
    });
  });

  group('상세', function () {
    const rideId = rideIds[i % rideIds.length];

    const res = http.get(`${BASE_URL}/api/v1/rides/${rideId}`, {
      headers: authHeaders(LT_CLIENT),
      tags: { endpoint: 'ride_detail' },
    });

    check(res, {
      '상세 200': (r) => r.status === 200,
      '상세 id 일치': (r) => r.json('data.id') === rideId,
    });
  });
}
