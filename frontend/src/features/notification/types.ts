/** 서버가 주는 알림 (백엔드 NotificationResponse) */
export type NotificationDto = {
  id: number;
  content: string;
  /** 알림을 눌렀을 때 갈 주소. 지금 백엔드는 대부분 null 로 보냅니다. */
  url: string | null;
  isRead: boolean;
  /** 받는 사람 — 내 알림만 오므로 화면에서는 쓰지 않습니다. */
  userId: number;
  name: string;
  /**
   * 생성 시각 — "2026-10-13T21:59:08.123456" (백엔드 LocalDateTime).
   * 선택 항목으로 둔 건 값이 비어 오면 "Invalid Date" 대신 시각 줄을 숨기기 위해서입니다.
   */
  createdAt?: string;
};

/** 화면이 쓰는 알림 */
export type AppNotification = {
  id: number;
  content: string;
  /** 눌렀을 때 이동할 경로. null 이면 링크 없이 글만 보여 줍니다. */
  href: string | null;
  read: boolean;
  /** 서버가 시각을 안 주면 null */
  createdAt: string | null;
};
