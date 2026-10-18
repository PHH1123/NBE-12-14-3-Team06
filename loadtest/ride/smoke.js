/**
 * 이동수단 도메인 스모크 테스트 — 1 VU 로 모든 경로를 한 바퀴만 돌립니다.
 *
 * 부하를 재는 게 목적이 아니고, 본 테스트를 돌리기 전에
 *   · 로그인 쿠키가 먹는지
 *   · seed.sql 이 제대로 들어갔는지 (공고 + ride 가 있는지)
 *   · 두 조회 경로가 실제로 통과하는지
 *   · 소유권 검증이 동작하는지
 * 를 확인하는 용도입니다. 여기서 실패하면 다른 스크립트는 전부 의미가 없습니다.
 *
 *   k6 run loadtest/ride/smoke.js
 *
 * 읽기 전용이라 상태를 소모하지 않습니다. 돌린 뒤 되돌릴 것이 없습니다.
 */
import http from 'k6/http';
import { check, group } from 'k6';
import { BASE_URL, SUMMARY_TREND_STATS } from '../lib/config.js';
import { authHeaders } from '../lib/auth.js';
import { peekStatusCode, unwrap } from '../lib/rsdata.js';
import { LT_CLIENT, OTHER_CLIENT } from './lib/config.js';
import { collectSeedPosts, collectRides, requireSeed } from './lib/rides.js';

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
  const postIds = collectSeedPosts(LT_CLIENT);
  // 스모크는 한 바퀴만 돌면 되므로 공고 몇 건만 훑습니다.
  const rideIds = collectRides(LT_CLIENT, postIds.slice(0, 3));

  requireSeed(postIds, rideIds);

  return { postId: postIds[0], rideId: rideIds[0] };
}

export default function (data) {
  const { postId, rideId } = data;

  group('공고별 이동수단 목록', function () {
    const res = http.get(`${BASE_URL}/api/v1/rides/posts/${postId}`, {
      headers: authHeaders(LT_CLIENT),
      tags: { endpoint: 'ride_list' },
    });

    check(res, {
      '목록 200': (r) => r.status === 200,
      '목록 statusCode 200-81': (r) => peekStatusCode(r) === '200-81',
      // RideService.createRide 가 공고당 TO_HOSPITAL / TO_HOME 2건을 만듭니다.
      '목록 2건': (r) => r.json('data').length === 2,
      '방향 두 종류': (r) => {
        const directions = r.json('data').map((ride) => ride.direction);
        return directions.indexOf('TO_HOSPITAL') >= 0 && directions.indexOf('TO_HOME') >= 0;
      },
    });
  });

  group('이동수단 상세', function () {
    const res = http.get(`${BASE_URL}/api/v1/rides/${rideId}`, {
      headers: authHeaders(LT_CLIENT),
      tags: { endpoint: 'ride_detail' },
    });

    check(res, {
      '상세 200': (r) => r.status === 200,
      '상세 statusCode 200-82': (r) => peekStatusCode(r) === '200-82',
      '상세 id 일치': (r) => r.json('data.id') === rideId,
      // RideResponse 가 post 의 좌표를 direction 에 따라 뒤집어 담습니다.
      // post 가 LAZY 라 여기서 추가 쿼리가 나갑니다 — null 이면 매핑이 깨진 것입니다.
      '출발지 주소 있음': (r) => !!r.json('data.originAddr'),
      '도착지 주소 있음': (r) => !!r.json('data.destAddr'),
    });
  });

  group('소유권 검증', function () {
    // 상세는 공고 작성자만 통과합니다 (RideService.validUser -> ForbiddenException(81)).
    const denied = http.get(`${BASE_URL}/api/v1/rides/${rideId}`, {
      headers: authHeaders(OTHER_CLIENT),
      tags: { endpoint: 'ride_detail_forbidden' },
    });

    check(denied, {
      '남의 상세 403': (r) => r.status === 403,
      '남의 상세 statusCode 403-81': (r) => peekStatusCode(r) === '403-81',
    });

    // ⚠️ 목록(GET /rides/posts/{postId})에는 소유권 검증이 없습니다.
    // SecurityConfig 에서 /api/** 가 authenticated() 로만 걸려 있어 로그인만 하면 통과합니다.
    // 아래 체크는 "막힌다"가 아니라 **지금 동작을 기록해 두는 것**입니다.
    // 서버에 소유권 검증이 추가되면 이 체크가 깨지면서 알려 줍니다.
    const otherList = http.get(`${BASE_URL}/api/v1/rides/posts/${postId}`, {
      headers: authHeaders(OTHER_CLIENT),
      tags: { endpoint: 'ride_list_other_user' },
    });

    check(otherList, {
      '남의 공고 목록은 현재 200 (소유권 검증 없음)': (r) => r.status === 200,
    });
  });
}

export function teardown() {
  console.log('스모크 통과. 읽기 전용이라 되돌릴 것이 없습니다.');
}
