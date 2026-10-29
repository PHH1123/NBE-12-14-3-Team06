import type { AppNotification, NotificationDto } from '../types';

/** 서버 DTO → 화면용 알림 */
export function toAppNotification(dto: NotificationDto): AppNotification {
  return {
    id: dto.id,
    content: dto.content,
    // 빈 문자열도 "링크 없음"으로 봅니다.
    href: dto.url || null,
    read: dto.isRead,
    createdAt: dto.createdAt ?? null,
  };
}
