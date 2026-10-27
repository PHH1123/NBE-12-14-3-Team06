import { ApiError, api, apiPost } from '@/lib/api';
import type { LiveLocationsDto, SendLocationRequest } from './types';

/**
 * 내 현재 위치 전송 — POST /api/v1/applications/{applicationId}/location
 * 승인(ACCEPTED)된 지원의 동행 매니저와 그 공고의 의뢰인이, 동행이 진행 중(출발 후 ~ 귀가 완료 전)일 때만 보낼 수 있습니다.
 * 서버에는 사람마다 가장 최근 위치 1건만 남고, 보낼 때마다 덮어씁니다.
 */
export function sendLocation(applicationId: number, body: SendLocationRequest): Promise<void> {
  return apiPost<void>(`/api/v1/applications/${applicationId}/location`, body);
}

/**
 * 의뢰인과 동행 매니저의 가장 최근 위치와 가까움 여부 조회 — GET /api/v1/applications/{applicationId}/location
 * 동행 진행 중 해당 동행의 의뢰인과 동행 매니저 본인만 서로의 위치를 볼 수 있습니다. 관리자는 조회할 수 없습니다.
 * 귀가 완료 시 저장된 위치는 삭제되며, 공유 중인 동행이 아니면(404) null 을 돌려줍니다.
 */
export async function fetchLocation(applicationId: number): Promise<LiveLocationsDto | null> {
  try {
    const data = await api<LiveLocationsDto | null>(`/api/v1/applications/${applicationId}/location`);
    return data ?? null;
  } catch (error) {
    if (error instanceof ApiError && error.statusCode.startsWith('404')) return null;
    throw error;
  }
}
