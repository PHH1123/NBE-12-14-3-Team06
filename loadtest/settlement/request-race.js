/**
 * 정산 요청 동시성(정합성) 테스트 — 같은 정산을 동시에 여러 번 요청해 봅니다.
 *
 * 성능이 아니라 **정합성**을 보는 테스트입니다. 정산은 돈이 나가는 쪽이라
 * 두 번 지급되면 바로 사고입니다.
 *
 * ## 두 경로의 방어선이 다릅니다
 *
 * ① 동행 매니저 경로 (MODE=user, 기본)
 *      POST /api/v1/settlements/{id}  ->  SettlementRepository.updateProcessing
 *        UPDATE settlement SET status='PROCESSING'
 *         WHERE id=? AND (status='PENDING' OR status='FAILED')
 *      PROCESSING 이 WHERE 절에 **없으므로** CAS 가 성립합니다.
 *      동시 N 건이면 **정확히 1건만 200**, 나머지는 400-71 이어야 합니다.
 *
 * ② 관리자 경로 (MODE=admin)
 *      POST /api/v1/admin/settlements/{id}  ->  updateProcessingForAdmin
 *        UPDATE settlement SET status='PROCESSING'
 *         WHERE id=? AND status IN ('PENDING','FAILED','PROCESSING')
 *      PROCESSING 이 WHERE 절에 **있습니다.** 컨트롤러 설명에도
 *      "정산 상태가 PROCESSING이어도 정산이 요청됩니다" 라고 적혀 있어 의도된 동작이지만,
 *      동시 요청에서는 여러 건이 동시에 통과할 수 있습니다.
 *      이 스크립트는 **몇 건이 통과하는지 측정만 하고 실패로 처리하지 않습니다.**
 *
 *   k6 run loadtest/settlement/request-race.js
 *   k6 run -e CONCURRENCY=20 -e ROUNDS=50 loadtest/settlement/request-race.js
 *   k6 run -e MODE=admin -e CONCURRENCY=20 -e ROUNDS=50 loadtest/settlement/request-race.js
 *
 * ROUNDS 건의 PENDING 정산을 소모합니다. 돌린 뒤 sql/reset.sql.
 */
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, SUMMARY_TREND_STATS } from '../lib/config.js';
import { authHeaders } from '../lib/auth.js';
import { peekStatusCode } from '../lib/rsdata.js';
import { LT_ESCORT, ADMIN } from './lib/config.js';
import { collectSettlements, requireRows } from './lib/settlements.js';

/** 한 정산에 동시에 때릴 요청 수 */
const CONCURRENCY = Number(__ENV.CONCURRENCY || 10);
/** 몇 개의 정산으로 반복할지 */
const ROUNDS = Number(__ENV.ROUNDS || 20);
/** 'user' = 동행 매니저 경로(CAS 있음), 'admin' = 관리자 경로(CAS 없음) */
const MODE = __ENV.MODE || 'user';

if (MODE !== 'user' && MODE !== 'admin') {
  throw new Error(`MODE=${MODE} 는 없습니다. user 또는 admin 을 쓰세요.`);
}

const ACTOR = MODE === 'admin' ? ADMIN : LT_ESCORT;
const PATH = MODE === 'admin' ? '/api/v1/admin/settlements' : '/api/v1/settlements';

const userThresholds = {
  // 한 라운드라도 2건 이상 통과하면 중복 정산입니다. 타협 없이 100%.
  'checks{check:정확히 1건만 성공(200)}': ['rate==1.00'],
  'checks{check:나머지는 전부 400 으로 막힘}': ['rate==1.00'],
  duplicate_settlements: ['count==0'],
};

export const options = {
  scenarios: {
    // VU 1개가 http.batch 로 동시에 N 개를 날립니다.
    // VU 를 여러 개 띄워 같은 정산을 노리게 하는 것보다 타이밍이 확실히 겹칩니다.
    main: { executor: 'per-vu-iterations', vus: 1, iterations: ROUNDS, maxDuration: '15m' },
  },
  summaryTrendStats: SUMMARY_TREND_STATS,
  thresholds: Object.assign(
    {
      // 두 경로 공통: 최종 상태는 반드시 COMPLETED 여야 합니다.
      'checks{check:최종 상태 COMPLETED}': ['rate==1.00'],
    },
    // 관리자 경로는 설계상 중복 통과가 가능하므로 임계값을 걸지 않고 측정만 합니다.
    MODE === 'user' ? userThresholds : {},
  ),
};

/** 한 정산에 2건 이상 성공이 통과한 라운드 수 — 0이 아니면 중복 정산 */
const duplicateSettlements = new Counter('duplicate_settlements');
/** 라운드당 성공 건수 분포 (관리자 경로에서 몇 건이 통과하는지 보려고) */
const approvedPerRound = new Trend('approved_per_round');

export function setup() {
  const set = collectSettlements(LT_ESCORT);

  requireRows(set.pending, `${LT_ESCORT} 의 요청 가능(PENDING·FAILED) 정산`, ROUNDS);

  console.log(`MODE=${MODE} — ${ACTOR} 계정으로 ${PATH}/{id} 에 동시 ${CONCURRENCY}건씩 ${ROUNDS}라운드`);

  return { ids: set.pending.ids };
}

export default function (data) {
  const index = exec.scenario.iterationInTest;
  if (index >= data.ids.length) return;

  const settlementId = data.ids[index];
  const headers = authHeaders(ACTOR);
  const url = `${BASE_URL}${PATH}/${settlementId}`;

  const requests = [];
  for (let i = 0; i < CONCURRENCY; i++) {
    requests.push(['POST', url, null, { headers: headers, tags: { endpoint: 'settlement_request_race' } }]);
  }

  const responses = http.batch(requests);

  let approved = 0;
  let blocked = 0;

  for (const res of responses) {
    if (res.status === 200) {
      approved++;
      continue;
    }

    // 막히는 경로는 두 가지입니다. 어느 쪽이든 "중복 요청을 거절했다"는 뜻입니다.
    //   400-71: 조건부 UPDATE 가 0행 (경쟁에서 진 경우. 대부분 이쪽)
    //   404-71: 조회 시점에 이미 COMPLETED (승자가 먼저 끝낸 경우)
    const code = peekStatusCode(res);
    if ((res.status === 400 && code === '400-71') || (res.status === 404 && code === '404-71')) {
      blocked++;
    }
  }

  approvedPerRound.add(approved);

  if (approved > 1) {
    duplicateSettlements.add(1);
    console.error(
      `❌ settlementId=${settlementId} 에 정산이 ${approved}건 통과했습니다 (동시 ${CONCURRENCY}건, MODE=${MODE}). ` +
        `중복 정산 방어선이 뚫렸습니다.`,
    );
  }

  if (blocked !== CONCURRENCY - approved) {
    console.error(
      `❌ settlementId=${settlementId}: 성공 ${approved}건, 정상 거절 ${blocked}건 — ` +
        `설명되지 않는 응답이 ${CONCURRENCY - approved - blocked}건 있습니다 (500 일 수 있습니다).`,
    );
  }

  if (MODE === 'user') {
    check(null, {
      '정확히 1건만 성공(200)': () => approved === 1,
      '나머지는 전부 400 으로 막힘': () => blocked === CONCURRENCY - 1,
    });
  }

  // 경쟁 와중에 상태가 깨지지 않았는지 사후 확인 (상세는 정산 주인만 볼 수 있습니다)
  const after = http.get(`${BASE_URL}/api/v1/settlements/${settlementId}`, {
    headers: authHeaders(LT_ESCORT),
    tags: { endpoint: 'settlement_detail' },
  });

  check(after, {
    '최종 상태 COMPLETED': (r) => r.json('data.status') === 'COMPLETED',
  });
}

export function teardown() {
  console.log(
    `MODE=${MODE} — ${ROUNDS}건의 정산을 소모했습니다. loadtest/settlement/sql/reset.sql 로 되돌리세요.`,
  );
}
