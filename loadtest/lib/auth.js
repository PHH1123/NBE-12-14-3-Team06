import http from 'k6/http';
import { fail } from 'k6';
import { BASE_URL, PASSWORD, TOKEN_TTL_MS, JSON_HEADERS } from './config.js';

/**
 * 로그인해서 accessToken 값을 꺼냅니다.
 *
 * 이 서버는 Authorization 헤더를 보지 않고 accessToken 쿠키만 읽습니다
 * (JwtAuthenticationFilter.resolveAccessToken -> Rq.getAccessToken).
 * k6 의 쿠키 항아리는 VU 마다 따로라서 setup() 에서 받은 쿠키를 VU 가 물려받지 못합니다.
 * 그래서 토큰 값만 꺼내 Cookie 헤더로 직접 싣습니다.
 */
export function login(username) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username, password: PASSWORD }),
    { headers: JSON_HEADERS, tags: { endpoint: 'auth_login' } },
  );

  if (res.status !== 200) {
    fail(`로그인 실패 (${username}): HTTP ${res.status} ${res.body}`);
  }

  const cookie = res.cookies.accessToken;
  if (!cookie || cookie.length === 0) {
    fail(`로그인 응답에 accessToken 쿠키가 없습니다 (${username})`);
  }

  return cookie[0].value;
}

// 모듈 스코프 변수는 VU 마다 하나씩 생기므로 VU 로컬 캐시로 쓸 수 있습니다.
const tokens = {};

/**
 * 요청에 붙일 Cookie 헤더.
 * 토큰이 TOKEN_TTL_MS 보다 오래됐으면 조용히 다시 로그인합니다.
 * 로그인 요청은 endpoint:auth_login 태그가 붙어 있어 도메인 임계값 계산에서 빠집니다.
 */
export function authHeaders(username) {
  const now = Date.now();
  const cached = tokens[username];

  if (!cached || now - cached.issuedAt > TOKEN_TTL_MS) {
    tokens[username] = { token: login(username), issuedAt: now };
  }

  return { Cookie: `accessToken=${tokens[username].token}` };
}

/** JSON 본문 + 인증까지 한 번에 */
export function jsonAuthHeaders(username) {
  return Object.assign({}, JSON_HEADERS, authHeaders(username));
}
