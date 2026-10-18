/**
 * 이 서버의 응답 규약(RsData)을 다루는 공통 헬퍼. 도메인과 무관합니다.
 */
import { fail } from 'k6';

/**
 * RsData 껍질을 벗겨 data 만 돌려줍니다.
 *
 * 이 서버는 ResponseAspect 가 RsData.statusCode 의 앞자리("400-44" -> 400)를
 * 그대로 HTTP 상태로 세팅하므로, HTTP 상태만 봐도 성공·실패 판정이 됩니다.
 * 덕분에 k6 의 http_req_failed 가 의미 있는 지표가 됩니다.
 */
export function unwrap(res, what) {
  if (res.status < 200 || res.status >= 300) {
    fail(`${what} 실패: HTTP ${res.status} ${res.body}`);
  }
  return res.json('data');
}

/** 응답 본문에서 RsData.statusCode 만 살짝 꺼내 봅니다. JSON 이 아니면 빈 문자열. */
export function peekStatusCode(res) {
  try {
    return res.json('statusCode') || '';
  } catch (e) {
    return '';
  }
}
