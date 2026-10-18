/**
 * 정산 도메인 스모크 테스트 — 1 VU 로 모든 경로를 한 바퀴만 돌립니다.
 *
 * 부하를 재는 게 목적이 아니고, 본 테스트를 돌리기 전에
 *   · 로그인 쿠키가 먹는지 (동행 매니저 / 관리자 둘 다)
 *   · seed.sql 이 제대로 들어갔는지 (PENDING / COMPLETED 정산이 있는지)
 *   · 정산 요청이 실제로 통과하는지, 중복 요청이 막히는지
 *   · 소유권 검증이 동작하는지
 *   · 관리자 경로가 열려 있는지
 * 를 확인하는 용도입니다. 여기서 실패하면 다른 스크립트는 전부 의미가 없습니다.
 *
 *   k6 run loadtest/settlement/smoke.js
 *
 * 정산 1건의 상태를 소모합니다. 쓰고 나면 sql/reset.sql 로 되돌리세요.
 * (버킷의 "마지막" 행을 쓰므로, 0번부터 쓰는 부하 스크립트와 겹치지 않습니다.)
 */
import http from 'k6/http';
import { check, group, fail } from 'k6';
import { BASE_URL, SUMMARY_TREND_STATS } from '../lib/config.js';
import { authHeaders } from '../lib/auth.js';
import { peekStatusCode } from '../lib/rsdata.js';
import { LT_ESCORT, OTHER_ESCORT, ADMIN, READ_PAGE_SIZE } from './lib/config.js';
import { collectSettlements } from './lib/settlements.js';

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
  const set = collectSettlements(LT_ESCORT);

  if (set.pending.ids.length === 0) {
    fail(
      `${LT_ESCORT} 의 PENDING 정산이 0건입니다. ` +
        `loadtest/settlement/sql/seed.sql 을 실행하거나 reset.sql 로 되돌리세요.`,
    );
  }
  if (set.completed.ids.length === 0) {
    fail(`${LT_ESCORT} 의 COMPLETED 정산이 0건입니다. seed.sql 을 실행하세요.`);
  }

  console.log(
    `${LT_ESCORT}: 전체 ${set.all.ids.length}건 / ` +
      `요청 가능(PENDING·FAILED) ${set.pending.ids.length}건 / COMPLETED ${set.completed.ids.length}건`,
  );

  const last = set.pending.ids.length - 1;

  return {
    requestable: set.pending.ids[last],
    completed: set.completed.ids[0],
    anyId: set.all.ids[0],
  };
}

export default function (data) {
  const { requestable, completed, anyId } = data;

  group('조회', function () {
    const listRes = http.get(`${BASE_URL}/api/v1/settlements?page=0&size=${READ_PAGE_SIZE}`, {
      headers: authHeaders(LT_ESCORT),
      tags: { endpoint: 'settlement_list' },
    });
    check(listRes, {
      '목록 200': (r) => r.status === 200,
      '목록 statusCode 200-72': (r) => peekStatusCode(r) === '200-72',
      '목록 content 있음': (r) => (r.json('data.content') || []).length > 0,
    });

    const detailRes = http.get(`${BASE_URL}/api/v1/settlements/${anyId}`, {
      headers: authHeaders(LT_ESCORT),
      tags: { endpoint: 'settlement_detail' },
    });
    check(detailRes, {
      '상세 200': (r) => r.status === 200,
      '상세 statusCode 200-73': (r) => peekStatusCode(r) === '200-73',
      '상세 id 일치': (r) => r.json('data.id') === anyId,
      // SettlementResponse 가 application.post 를 타고 들어가 PostDto 를 만듭니다.
      '상세 post 채워짐': (r) => !!r.json('data.post'),
    });
  });

  group('정산 요청', function () {
    const res = http.post(`${BASE_URL}/api/v1/settlements/${requestable}`, null, {
      headers: authHeaders(LT_ESCORT),
      tags: { endpoint: 'settlement_request' },
    });
    check(res, {
      '요청 200': (r) => r.status === 200,
      '요청 statusCode 200-71': (r) => peekStatusCode(r) === '200-71',
    });

    // 같은 건을 한 번 더 요청하면 400-71 로 막혀야 합니다
    // (SettlementRepository.updateProcessing 의 조건부 UPDATE 가 0행).
    const again = http.post(`${BASE_URL}/api/v1/settlements/${requestable}`, null, {
      headers: authHeaders(LT_ESCORT),
      tags: { endpoint: 'settlement_request_duplicate' },
    });
    check(again, {
      '중복 요청 차단': (r) => r.status === 400 || r.status === 404,
      '중복 요청 코드 400-71 / 404-71': (r) => {
        const code = peekStatusCode(r);
        return code === '400-71' || code === '404-71';
      },
    });

    // 이미 COMPLETED 인 건도 요청 대상이 아닙니다.
    // findByIdAndState 가 PENDING/FAILED/PROCESSING 만 집으므로 404-71 입니다.
    const done = http.post(`${BASE_URL}/api/v1/settlements/${completed}`, null, {
      headers: authHeaders(LT_ESCORT),
      tags: { endpoint: 'settlement_request_completed' },
    });
    check(done, {
      'COMPLETED 재요청 404': (r) => r.status === 404,
      'COMPLETED 재요청 404-71': (r) => peekStatusCode(r) === '404-71',
    });
  });

  group('소유권 검증', function () {
    // 상세는 정산 주인만 통과합니다 (SettlementService.findSettlement -> ForbiddenException(71)).
    const denied = http.get(`${BASE_URL}/api/v1/settlements/${anyId}`, {
      headers: authHeaders(OTHER_ESCORT),
      tags: { endpoint: 'settlement_detail_forbidden' },
    });
    check(denied, {
      '남의 상세 403': (r) => r.status === 403,
      '남의 상세 403-71': (r) => peekStatusCode(r) === '403-71',
    });

    // 요청도 남의 건이면 안 됩니다. findByIdAndState(userId, settlementId) 가
    // escort id 까지 조건에 넣어 조회 자체가 비므로 404-71 입니다.
    const deniedReq = http.post(`${BASE_URL}/api/v1/settlements/${completed}`, null, {
      headers: authHeaders(OTHER_ESCORT),
      tags: { endpoint: 'settlement_request_forbidden' },
    });
    check(deniedReq, {
      '남의 정산 요청 404': (r) => r.status === 404,
      '남의 정산 요청 404-71': (r) => peekStatusCode(r) === '404-71',
    });
  });

  group('관리자', function () {
    const listRes = http.get(`${BASE_URL}/api/v1/admin/settlements?page=0&size=20`, {
      headers: authHeaders(ADMIN),
      tags: { endpoint: 'admin_list' },
    });
    check(listRes, {
      '관리자 목록 200': (r) => r.status === 200,
      '관리자 목록 statusCode 200-162': (r) => peekStatusCode(r) === '200-162',
    });

    const analyticsRes = http.get(`${BASE_URL}/api/v1/admin/settlements/analytics`, {
      headers: authHeaders(ADMIN),
      tags: { endpoint: 'admin_analytics' },
    });
    check(analyticsRes, {
      '관리자 통계 200': (r) => r.status === 200,
      '관리자 통계 statusCode 200-161': (r) => peekStatusCode(r) === '200-161',
      '관리자 통계 건수 있음': (r) => r.json('data.settlementCount') > 0,
    });

    // 동행 매니저 계정으로 관리자 경로를 찌르면 막혀야 합니다
    // (SecurityConfig: /api/v1/admin/** -> hasRole("ADMIN")).
    const denied = http.get(`${BASE_URL}/api/v1/admin/settlements?page=0&size=20`, {
      headers: authHeaders(LT_ESCORT),
      tags: { endpoint: 'admin_list_forbidden' },
    });
    check(denied, { '비관리자 관리자목록 403': (r) => r.status === 403 });
  });
}

export function teardown() {
  console.log('스모크 통과. 쓴 행을 되돌리려면: loadtest/settlement/sql/reset.sql');
}
