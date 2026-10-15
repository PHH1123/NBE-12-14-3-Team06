import { api, apiDelete, apiPatch, type SpringPage } from '@/lib/api';
import { toAppNotification } from './model/mapper';
import type { AppNotification, NotificationDto } from './types';

/** 한 번에 가져오는 알림 개수. 드롭다운이 다 보여 주는 양이라 더 받아 올 필요가 없습니다. */
const PAGE_SIZE = 20;

/**
 * 실시간 알림을 받는 SSE 주소 (EventSource 가 직접 붙습니다 — api() 를 쓰지 않습니다).
 * next.config.ts 의 rewrites 가 8080 으로 넘겨 주므로 쿠키도 그대로 실립니다.
 */
export const NOTIFICATION_SSE_PATH = '/api/v1/notification/connect';

/**
 * 내 알림 목록 — GET /api/v1/notification?page=&size=
 * 백엔드가 Page 로 감싸서 주고 id 내림차순(최신순)으로 정렬해 줍니다. 읽음 상태는 건드리지 않습니다.
 */
export async function fetchNotifications(): Promise<AppNotification[]> {
  const page = await api<SpringPage<NotificationDto>>(`/api/v1/notification?page=0&size=${PAGE_SIZE}`);
  return page.content.map(toAppNotification);
}

/** 알림 하나 읽음 처리 — PATCH /api/v1/notification/{id}/read */
export function markNotificationRead(id: number): Promise<unknown> {
  return apiPatch<unknown>(`/api/v1/notification/${id}/read`);
}

/**
 * 읽음 처리 — PATCH /api/v1/notification/read?page=&size=
 *
 * 백엔드는 "안 읽은 전부"가 아니라 "그 페이지"를 읽음으로 바꿉니다. 목록과 같은 범위를 넘겨서
 * 드롭다운에 보이는 알림이 전부 읽음이 되도록 맞춥니다(= "모두 읽음" 버튼).
 * 그래서 PAGE_SIZE 밖으로 밀려난 오래된 알림은 읽음 처리되지 않습니다.
 */
export function markNotificationsRead(): Promise<unknown> {
  return apiPatch<unknown>(`/api/v1/notification/read?page=0&size=${PAGE_SIZE}`);
}

/**
 * 알림 하나 삭제 — DELETE /api/v1/notification/{id}
 * 백엔드는 행을 지우지 않고 deletedAt 만 채우며(soft delete), 목록 조회에서 빠집니다.
 */
export function deleteNotification(id: number): Promise<unknown> {
  return apiDelete<unknown>(`/api/v1/notification/${id}`);
}
