/**
 * 이동수단 도메인 전용 setup() 헬퍼.
 *
 * ride 도메인에는 "내 이동수단 목록" 같은 엔드포인트가 없습니다. 노출된 건 둘뿐입니다.
 *   GET /api/v1/rides/posts/{postId}  공고별 목록
 *   GET /api/v1/rides/{rideId}        상세 (공고 작성자만)
 *
 * 그래서 ride id 를 알아내려면 공고를 먼저 찾고 공고별 목록을 호출해야 합니다.
 *   ① GET /api/v1/posts/me      → 시드 공고 id 수집
 *   ② GET /api/v1/rides/posts/* → 그 공고들의 ride id 수집
 *
 * RsData 껍질 벗기기 같은 공통 동작은 loadtest/lib/rsdata.js 에 있습니다.
 */
import http from 'k6/http';
import { fail } from 'k6';
import { BASE_URL } from '../../lib/config.js';
import { authHeaders } from '../../lib/auth.js';
import { unwrap } from '../../lib/rsdata.js';
import { SEED_POST_PREFIX, COLLECT_POSTS, POSTS_PAGE_SIZE } from './config.js';

/**
 * 시드가 만든 공고의 id 를 모읍니다.
 *
 * /api/v1/posts/me 는 Page 라서 data.content 안에 들어 있습니다.
 * 공고가 없으면 statusCode 가 "200-14" 로 내려오지만 HTTP 는 200 이라
 * unwrap 이 통과합니다 — 빈 배열은 호출한 쪽에서 걸러야 합니다.
 */
export function collectSeedPosts(username) {
  const ids = [];
  let page = 0;

  // 페이지 크기를 크게 잡아 보통 1~2번이면 끝납니다. 무한루프만 막아 둡니다.
  for (let guard = 0; guard < 50; guard++) {
    const res = http.get(
      `${BASE_URL}/api/v1/posts/me?page=${page}&size=${POSTS_PAGE_SIZE}`,
      {
        headers: authHeaders(username),
        timeout: '300s',
        tags: { endpoint: 'setup_posts' },
      },
    );

    const body = unwrap(res, `${username} 공고 목록 조회`);
    const content = body.content || [];

    for (const post of content) {
      if (post.title && post.title.indexOf(SEED_POST_PREFIX) === 0) {
        ids.push(post.id);
      }
    }

    if (body.last === true || content.length === 0) break;
    page++;
  }

  return ids;
}

/**
 * 공고별 목록 API 를 돌며 ride id 를 모읍니다.
 *
 * 공고 1건당 ride 2건(TO_HOSPITAL / TO_HOME)이므로 postIds 의 2배가 나옵니다.
 * 이 호출 자체가 setup 비용이라 COLLECT_POSTS 로 상한을 둡니다.
 */
export function collectRides(username, postIds) {
  const rideIds = [];
  const limit = Math.min(postIds.length, COLLECT_POSTS);

  for (let i = 0; i < limit; i++) {
    const res = http.get(`${BASE_URL}/api/v1/rides/posts/${postIds[i]}`, {
      headers: authHeaders(username),
      tags: { endpoint: 'setup_rides' },
    });

    const rides = unwrap(res, `공고 ${postIds[i]} 의 이동수단 목록 조회`);
    for (const ride of rides) {
      rideIds.push(ride.id);
    }
  }

  return rideIds;
}

/** 시드가 없으면 부하가 의미 없으므로 바로 세웁니다. */
export function requireSeed(postIds, rideIds) {
  if (postIds.length === 0) {
    fail(
      `${SEED_POST_PREFIX} 공고가 0건입니다. loadtest/ride/sql/seed.sql 을 먼저 실행하세요.`,
    );
  }

  if (rideIds.length === 0) {
    fail(
      `공고는 ${postIds.length}건 찾았는데 이동수단이 0건입니다. ` +
        `ride 테이블이 비어 있습니다 — seed.sql 의 ride INSERT 가 돌았는지 확인하세요. ` +
        `(BaseInitData 가 만든 공고에는 ride 가 없습니다. PostService.createPost 를 안 타기 때문입니다.)`,
    );
  }

  console.log(`시드 공고 ${postIds.length}건 / 이동수단 ${rideIds.length}건 확보`);
}
