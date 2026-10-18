/**
 * 도메인 무관 공통 설정. 모든 도메인이 같이 씁니다.
 * 도메인 전용 값(대상 계정, 시드 접두어 등)은 <도메인>/lib/config.js 에 둡니다.
 *
 * 모든 값은 k6 실행 시 -e KEY=VALUE 로 덮어쓸 수 있습니다.
 */

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/** BaseInitData 가 심는 공통 비밀번호 */
export const PASSWORD = __ENV.PASSWORD || 'password1!';

/**
 * 액세스 토큰을 다시 받기까지의 간격(ms).
 *
 * .env 의 JWT_EXPIRATION 이 600초(10분)입니다. setup() 에서 받은 토큰 하나로
 * 10분을 넘겨 돌리면 테스트 중간부터 전부 401 이 되므로, 그보다 짧게 잡아
 * VU 가 스스로 재로그인하게 합니다. (soak 테스트에 필수)
 */
export const TOKEN_TTL_MS = Number(__ENV.TOKEN_TTL_MS || 480000);

/** 실행 프로파일: 스크립트마다 지원하는 값이 다릅니다 (각 파일의 profiles 참고) */
export const STAGE = __ENV.STAGE || 'load';

export const JSON_HEADERS = { 'Content-Type': 'application/json' };

/** 요약에 p99 까지 보이게 */
export const SUMMARY_TREND_STATS = ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'];

/** '30s' / '10m' / '1h' 를 초로 바꿉니다. */
export function toSeconds(duration) {
  const matched = /^(\d+)(s|m|h)$/.exec(String(duration).trim());
  if (!matched) return 0;

  const value = Number(matched[1]);
  const unit = matched[2];

  if (unit === 'h') return value * 3600;
  if (unit === 'm') return value * 60;
  return value;
}

/**
 * 시나리오가 몇 번 반복될지 어림합니다.
 *
 * 결제 승인·취소는 한 번 쓰면 그 행을 소모하기 때문에, 시드 행 수가 반복 횟수보다
 * 적으면 중간부터 빈 반복이 됩니다. 실행 직후 경고를 띄우려고 미리 계산합니다.
 */
export function estimateIterations(scenario) {
  if (!scenario) return 0;

  if (scenario.executor === 'constant-arrival-rate') {
    return Math.ceil(scenario.rate * toSeconds(scenario.duration));
  }

  if (scenario.executor === 'ramping-arrival-rate') {
    let total = 0;
    let from = scenario.startRate || 0;

    for (const stage of scenario.stages || []) {
      total += ((from + stage.target) / 2) * toSeconds(stage.duration);
      from = stage.target;
    }

    return Math.ceil(total);
  }

  if (scenario.executor === 'shared-iterations') return scenario.iterations || 0;
  if (scenario.executor === 'per-vu-iterations') {
    return (scenario.iterations || 0) * (scenario.vus || 1);
  }

  return 0;
}

/** STAGE 환경변수로 시나리오 하나를 고릅니다. */
export function pickProfile(profiles) {
  const profile = profiles[STAGE];

  if (!profile) {
    throw new Error(
      `STAGE=${STAGE} 는 이 스크립트에 없습니다. 사용 가능: ${Object.keys(profiles).join(', ')}`,
    );
  }

  return { main: profile };
}
