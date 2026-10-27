import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
export const PAIRS = Number(__ENV.PAIRS || 100); // 시드의 loadtest.user-count 와 같게

const PASSWORD = 'password1!';
const pad = (n) => String(n).padStart(3, '0');

function login(username) {
  const res = http.post(`${BASE}/auth/login`, JSON.stringify({ username, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { api: 'setup' },
  });
  check(res, { [`${username} 로그인`]: (r) => r.status === 200 });
  const cookie = res.cookies.accessToken;
  if (!cookie || cookie.length === 0) fail(`${username} 로그인 실패: ${res.status} ${res.body}`);
  return { Cookie: `accessToken=${cookie[0].value}` };
}

// 시드된 모든 쌍(ltclientNNN + ltescortNNN)의 로그인 쿠키와 applicationId 를 모은다.
export function loginAll() {
  const pairs = [];
  for (let i = 1; i <= PAIRS; i++) {
    const client = login(`ltclient${pad(i)}`);
    const escort = login(`ltescort${pad(i)}`);
    const apps = http.get(`${BASE}/applications/me`, { headers: escort, tags: { api: 'setup' } }).json('data');
    const accepted = (apps || []).find((a) => a.applicationStatus === 'ACCEPTED');
    if (!accepted) fail(`ltescort${pad(i)} 에 승인된 지원이 없습니다. (loadtest 프로필로 서버를 띄웠는지 확인하세요)`);
    pairs.push({ client, escort, applicationId: accepted.applicationId });
  }
  return { pairs };
}

export function pairOf(data, vu) {
  return data.pairs[(vu - 1) % data.pairs.length];
}

// 공고 목록 같은 조회용: 의뢰인 쿠키 하나를 VU 마다 나눠 쓴다.
export function headersOf(data, vu) {
  return { headers: pairOf(data, vu).client };
}
