// 기준선 측정 (수정 전/후 같은 조건으로 비교하려고 만든 스크립트)
//
// 준비:  SPRING_PROFILES_ACTIVE=dev,loadtest ./gradlew bootRun   (가상 사용자·공고·매칭 쌍 시드)
// 실행:  k6 run -e LABEL=before k6/baseline.js
//        결과는 k6/results/<LABEL>.json 에 저장된다. 수정 후에는 LABEL=after 로 다시 돌린다.
//
// 시나리오(각각 시간을 어긋나게 순서대로 돌려서 서로 영향을 주지 않는다):
//   posts_list     모집 중 공고 목록   — 가장 많이 호출되는 API
//   posts_search   공고 키워드 검색
//   location       의뢰인·동행 매니저가 각자 위치를 보내고 서로의 위치를 조회 (실제로는 5초마다)
//   progress       의뢰인 화면의 진행 단계 조회 (실제로는 15초마다)
//   login          로그인 (비밀번호 해시 CPU 비용)
// hikari_* 는 서버의 커넥션풀 상태를 1초마다 읽은 값이다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import exec from 'k6/execution';
import { BASE, PAIRS, loginAll, headersOf, pairOf } from './lib/common.js';

const DURATION = __ENV.DURATION || '30s';
const VUS = Number(__ENV.VUS || 50);
const LOC_SLEEP = Number(__ENV.LOC_SLEEP || 1); // 실제 화면은 5초. 1초면 5배의 부하를 준다.
const hikariActive = new Trend('hikari_active');
const hikariPending = new Trend('hikari_pending');
const processCpu = new Trend('process_cpu');
// 커넥션 점유 시간(hikaricp_connections_usage_seconds)은 누적값이라 구간 처음·끝의 차이로 평균을 구한다.
const usageSum = new Trend('hikari_usage_sum');
const usageCount = new Trend('hikari_usage_count');
const usageMax = new Trend('hikari_usage_max');

function stage(name, offsetSeconds, exec, vus = VUS) {
  return { executor: 'constant-vus', vus, duration: DURATION, startTime: `${offsetSeconds}s`, exec, tags: { api: name } };
}
const span = Number.parseInt(DURATION, 10) + 10;
const ALL_PHASES = ['posts_list', 'posts_search', 'location', 'progress', 'login'];
// ONLY=posts_list,location 처럼 지정하면 그 구간만 순서대로 돌린다. (고친 API 만 빨리 다시 재려고)
const PHASES = __ENV.ONLY ? ALL_PHASES.filter((p) => __ENV.ONLY.split(',').includes(p)) : ALL_PHASES;
const SCENARIO_DEFS = {
  posts_list: ['postsList', VUS],
  posts_search: ['postsSearch', VUS],
  location: ['location', Math.min(VUS, PAIRS)],
  progress: ['progress', Math.min(VUS, PAIRS)],
  login: ['login', Math.min(VUS, 20)],
};
const stages = {};
PHASES.forEach((name, i) => {
  stages[name] = stage(name, span * i, SCENARIO_DEFS[name][0], SCENARIO_DEFS[name][1]);
});

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  scenarios: {
    ...stages,
    sampler: { executor: 'constant-vus', vus: 1, duration: `${span * PHASES.length}s`, exec: 'sampler' },
  },
  // 임계값은 실패시키려는 게 아니라 api 별 지표가 요약에 항상 나오게 하는 용도다.
  thresholds: {
    'http_req_duration{api:posts_list}': ['p(95)<10000'],
    'http_req_duration{api:posts_search}': ['p(95)<10000'],
    'http_req_duration{api:location}': ['p(95)<10000'],
    'http_req_duration{api:progress}': ['p(95)<10000'],
    'http_req_duration{api:login}': ['p(95)<10000'],
    'http_req_failed{api:posts_list}': ['rate<1'],
    'http_req_failed{api:posts_search}': ['rate<1'],
    'http_req_failed{api:location}': ['rate<1'],
    'http_req_failed{api:progress}': ['rate<1'],
    'http_req_failed{api:login}': ['rate<1'],
    'http_reqs{api:posts_list}': ['count>=0'],
    'http_reqs{api:posts_search}': ['count>=0'],
    'http_reqs{api:location}': ['count>=0'],
    'http_reqs{api:progress}': ['count>=0'],
    'http_reqs{api:login}': ['count>=0'],
    'hikari_active{api:posts_list}': ['max>=0'],
    'hikari_pending{api:posts_list}': ['max>=0'],
    'hikari_active{api:posts_search}': ['max>=0'],
    'hikari_pending{api:posts_search}': ['max>=0'],
    'hikari_active{api:location}': ['max>=0'],
    'hikari_pending{api:location}': ['max>=0'],
    'hikari_active{api:progress}': ['max>=0'],
    'hikari_pending{api:progress}': ['max>=0'],
    'hikari_active{api:login}': ['max>=0'],
    'hikari_pending{api:login}': ['max>=0'],
  },
};

for (const api of ALL_PHASES) {
  for (const name of ['hikari_usage_sum', 'hikari_usage_count', 'hikari_usage_max']) {
    options.thresholds[`${name}{api:${api}}`] = ['max>=0'];
  }
}

export function setup() {
  return loginAll();
}

export function postsList(data) {
  const page = Math.floor(Math.random() * 20);
  const res = http.get(`${BASE}/posts?page=${page}&size=10`, headersOf(data, __VU));
  check(res, { 'posts list 200': (r) => r.status === 200 });
  sleep(1);
}

const KEYWORDS = ['서울대병원', '세브란스', '진료', '동행', '안과', '부하'];
export function postsSearch(data) {
  const keyword = encodeURIComponent(KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)]);
  const res = http.get(`${BASE}/posts?keyword=${keyword}&page=0&size=10`, headersOf(data, __VU));
  check(res, { 'posts search 200': (r) => r.status === 200 });
  sleep(1);
}

// 한 VU 가 한 쌍(의뢰인+동행 매니저). 둘 다 자기 위치를 보내고 서로의 위치를 조회한다.
export function location(data) {
  const pair = pairOf(data, __VU);
  const jitter = () => (Math.random() - 0.5) * 0.0004; // 약 ±20m
  const body = () => JSON.stringify({ lat: 37.5665 + jitter(), lng: 126.978 + jitter(), accuracy: 10 });
  const url = `${BASE}/applications/${pair.applicationId}/location`;
  const json = { 'Content-Type': 'application/json' };

  const r1 = http.post(url, body(), { headers: { ...json, ...pair.escort }, tags: { api: 'location' } });
  const r2 = http.post(url, body(), { headers: { ...json, ...pair.client }, tags: { api: 'location' } });
  const r3 = http.get(url, { headers: pair.escort, tags: { api: 'location' } });
  const r4 = http.get(url, { headers: pair.client, tags: { api: 'location' } });
  check(r1, { 'escort 위치 전송 200': (r) => r.status === 200 });
  check(r2, { 'client 위치 전송 200': (r) => r.status === 200 });
  check(r3, { 'escort 위치 조회 200': (r) => r.status === 200 });
  check(r4, { 'client 위치 조회 200': (r) => r.status === 200 });
  sleep(LOC_SLEEP);
}

export function progress(data) {
  const pair = pairOf(data, __VU);
  const res = http.get(`${BASE}/applications/${pair.applicationId}/progress`, { headers: pair.client, tags: { api: 'progress' } });
  check(res, { 'progress 200': (r) => r.status === 200 });
  sleep(1);
}

export function login() {
  const n = 1 + Math.floor(Math.random() * PAIRS);
  const res = http.post(
    `${BASE}/auth/login`,
    JSON.stringify({ username: `ltclient${String(n).padStart(3, '0')}`, password: 'password1!' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { api: 'login' } },
  );
  check(res, { 'login 200': (r) => r.status === 200 });
  sleep(1);
}

// 서버의 커넥션풀·CPU 를 1초마다 읽는다. (/actuator/prometheus)
export function sampler() {
  const res = http.get(`${BASE.replace('/api/v1', '')}/actuator/prometheus`, { tags: { api: 'sampler' } });
  const read = (name) => {
    const m = res.body.match(new RegExp(`^${name}\\{[^}]*\\} ([0-9.eE+-]+)`, 'm'));
    return m ? Number(m[1]) : null;
  };
  const active = read('hikaricp_connections_active');
  const pending = read('hikaricp_connections_pending');
  const uSum = read('hikaricp_connections_usage_seconds_sum');
  const uCount = read('hikaricp_connections_usage_seconds_count');
  const uMax = read('hikaricp_connections_usage_seconds_max');
  const cpu = res.body.match(/^process_cpu_usage ([0-9.eE+-]+)/m);
  // 지금이 어느 시나리오 구간인지 계산해서 그 구간 이름을 태그로 붙인다.
  const phase = PHASES[Math.min(Math.floor((Date.now() - exec.scenario.startTime) / 1000 / span), PHASES.length - 1)];
  if (active !== null) hikariActive.add(active, { api: phase });
  if (pending !== null) hikariPending.add(pending, { api: phase });
  if (uSum !== null && uCount !== null) {
    usageSum.add(uSum, { api: phase });
    usageCount.add(uCount, { api: phase });
    if (uMax !== null) usageMax.add(uMax, { api: phase });
  }
  if (cpu) processCpu.add(Number(cpu[1]));
  sleep(1);
}

// 결과를 api 별로 정리해 k6/results/<LABEL>.json 에 저장한다. (시각화 입력)
export function handleSummary(summary) {
  const label = __ENV.LABEL || 'run';
  const m = summary.metrics;
  const pick = (name, key) => (m[name] && m[name].values ? m[name].values[key] : null);
  const holdAvgMs = (api) => {
    const dSum = pick(`hikari_usage_sum{api:${api}}`, 'max') - pick(`hikari_usage_sum{api:${api}}`, 'min');
    const dCount = pick(`hikari_usage_count{api:${api}}`, 'max') - pick(`hikari_usage_count{api:${api}}`, 'min');
    return dCount > 0 ? (dSum / dCount) * 1000 : null;
  };
  const apis = {};
  for (const api of PHASES) {
    const dur = `http_req_duration{api:${api}}`;
    apis[api] = {
      avg: pick(dur, 'avg'),
      p95: pick(dur, 'p(95)'),
      p99: pick(dur, 'p(99)'),
      max: pick(dur, 'max'),
      count: pick(`http_reqs{api:${api}}`, 'count'),
      rps: (pick(`http_reqs{api:${api}}`, 'count') || 0) / Number.parseInt(DURATION, 10),
      errorRate: pick(`http_req_failed{api:${api}}`, 'rate'),
      hikariActiveMax: pick(`hikari_active{api:${api}}`, 'max'),
      hikariPendingMax: pick(`hikari_pending{api:${api}}`, 'max'),
      // 요청 하나가 커넥션을 잡고 있던 평균·최대 시간(ms). 구간 처음·끝 샘플의 누적값 차이로 계산한다.
      holdAvgMs: holdAvgMs(api),
      holdMaxMs: (pick(`hikari_usage_max{api:${api}}`, 'max') ?? 0) * 1000,
    };
  }
  const result = {
    label,
    at: new Date().toISOString(),
    config: { vus: VUS, duration: DURATION, locSleep: LOC_SLEEP },
    apis,
    hikari: {
      activeMax: pick('hikari_active', 'max'),
      activeAvg: pick('hikari_active', 'avg'),
      pendingMax: pick('hikari_pending', 'max'),
    },
    cpu: { avg: pick('process_cpu', 'avg'), max: pick('process_cpu', 'max') },
  };
  return { [`k6/results/${label}.json`]: JSON.stringify(result, null, 2), stdout: textSummary(result) };
}

function textSummary(r) {
  const f = (v) => (v === null || v === undefined ? '-' : v.toFixed(1));
  let out = `\n=== ${r.label} (vus=${r.config.vus}, ${r.config.duration}) ===\n`;
  out += 'api            avg(ms)  p95(ms)  p99(ms)   req/s   err%  풀active최대 풀대기최대 점유평균ms 점유최대ms\n';
  for (const [api, v] of Object.entries(r.apis)) {
    out += `${api.padEnd(14)} ${f(v.avg).padStart(7)} ${f(v.p95).padStart(8)} ${f(v.p99).padStart(8)} ${f(v.rps).padStart(7)} ${f((v.errorRate ?? 0) * 100).padStart(6)} ${f(v.hikariActiveMax).padStart(10)} ${f(v.hikariPendingMax).padStart(10)} ${f(v.holdAvgMs).padStart(10)} ${f(v.holdMaxMs).padStart(10)}\n`;
  }
  out += `hikari active max=${f(r.hikari.activeMax)} / pending max=${f(r.hikari.pendingMax)} / cpu max=${f((r.cpu.max ?? 0) * 100)}%\n`;
  return out;
}
