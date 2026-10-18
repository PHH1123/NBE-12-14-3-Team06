/**
 * 정산 도메인 전용 setup() 헬퍼.
 *
 * GET /api/v1/settlements 가 로그인한 동행 매니저의 정산을 Page 로 돌려주므로,
 * setup 에서 한 번 긁어 상태별로 나눠 둡니다. mysql 클라이언트가 따로 필요 없습니다.
 *
 * RsData 껍질 벗기기 같은 공통 동작은 loadtest/lib/rsdata.js 에 있습니다.
 */
import http from 'k6/http';
import { fail } from 'k6';
import { BASE_URL } from '../../lib/config.js';
import { authHeaders } from '../../lib/auth.js';
import { unwrap } from '../../lib/rsdata.js';
import { LIST_FROM, LIST_TO, LIST_PAGE_SIZE } from './config.js';

function emptyBucket() {
  return { ids: [], amounts: [] };
}

function push(bucket, settlement) {
  bucket.ids.push(settlement.id);
  bucket.amounts.push(settlement.payoutAmount);
}

/**
 * 정산 전건을 긁어 상태별로 나눕니다.
 *
 * setup 이 돌려준 값은 VU 마다 복사되므로, 객체 배열 대신 평행 배열로 담아 메모리를 아낍니다.
 * 시드가 1만 건이면 페이지 몇 번으로 끝나지만 한 요청이 수 초 걸릴 수 있어 timeout 을 넉넉히 줍니다.
 */
export function collectSettlements(username) {
  const pending = emptyBucket();   // PENDING / FAILED — 정산 요청 가능
  const completed = emptyBucket(); // COMPLETED — 요청하면 400-71
  const all = emptyBucket();

  let page = 0;

  // 페이지 크기를 크게 잡아 보통 몇 번이면 끝납니다. 무한루프만 막아 둡니다.
  for (let guard = 0; guard < 100; guard++) {
    const url =
      `${BASE_URL}/api/v1/settlements` +
      `?page=${page}&size=${LIST_PAGE_SIZE}&startDate=${LIST_FROM}&endDate=${LIST_TO}`;

    const res = http.get(url, {
      headers: authHeaders(username),
      timeout: '300s',
      tags: { endpoint: 'setup_collect' },
    });

    const body = unwrap(res, `${username} 정산 목록 조회`);
    const content = body.content || [];

    for (const settlement of content) {
      push(all, settlement);

      // FAILED 도 재요청이 가능합니다 (updateProcessing 의 WHERE 절이 PENDING/FAILED).
      if (settlement.status === 'PENDING' || settlement.status === 'FAILED') {
        push(pending, settlement);
      } else if (settlement.status === 'COMPLETED') {
        push(completed, settlement);
      }
    }

    if (body.last === true || content.length === 0) break;
    page++;
  }

  return { username, pending, completed, all };
}

/**
 * 한 행(정산)을 한 번만 쓰는 시나리오(정산 요청)에 필요한 행 수를 미리 알려 줍니다.
 * 모자라면 중간부터 400 이 쏟아지므로 실행 전에 경고를 띄웁니다.
 */
export function requireRows(bucket, label, needed) {
  if (bucket.ids.length === 0) {
    fail(`${label} 이 0건입니다. loadtest/settlement/sql/seed.sql 을 먼저 실행하세요.`);
  }

  console.log(`${label}: ${bucket.ids.length}건 확보`);

  if (needed && bucket.ids.length < needed) {
    console.warn(
      `⚠️  ${label} ${bucket.ids.length}건 < 이번 실행이 쓸 ${needed}건 — ` +
        `모자라는 만큼은 요청 없이 건너뜁니다(rows_exhausted 메트릭). ` +
        `seed.sql 의 @pending_count 를 늘리거나 RATE/DURATION 을 줄이세요. ` +
        `직전 실행 뒤 reset.sql 을 돌렸는지도 확인하세요.`,
    );
  }
}
