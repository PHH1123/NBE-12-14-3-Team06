'use client';

import { useCallback, useEffect, useState } from 'react';
import type { RsData } from '@/lib/api';
import {
  NOTIFICATION_SSE_PATH,
  deleteNotification,
  fetchNotifications,
  markNotificationRead,
  markNotificationsRead,
} from '../api';
import { toAppNotification } from '../model/mapper';
import type { AppNotification, NotificationDto } from '../types';

type Result = {
  /** 최신순 알림 목록 */
  items: AppNotification[];
  /** 안 읽은 알림 개수 (종 옆 빨간 배지) */
  unreadCount: number;
  /** 목록을 처음 불러오는 중 */
  loading: boolean;
  /** 알림 하나를 읽음으로 바꿉니다. */
  markRead: (id: number) => void;
  /** 드롭다운에 보이는 알림을 모두 읽음으로 바꿉니다. 서버에도 반영됩니다. */
  markAllRead: () => void;
  /** 알림 하나를 지웁니다. 서버가 거절하면 목록에 되돌려 놓습니다. */
  remove: (id: number) => void;
};

/**
 * SSE 로 온 한 줄을 알림으로 바꿉니다.
 *
 * 백엔드는 연결 직후 "connect" 라는 문자열을 한 번 보내고,
 * 그 뒤로는 RsData<NotificationResponse> 를 보냅니다(NotificationEventListener).
 * 알림이 아닌 줄(연결 확인 등)은 null 로 걸러 냅니다.
 */
function parseNotification(raw: string): AppNotification | null {
  try {
    const body = JSON.parse(raw) as Partial<RsData<NotificationDto>>;
    return body.data?.id === undefined ? null : toAppNotification(body.data);
  } catch {
    return null; // JSON 이 아니면 알림이 아닙니다.
  }
}

/**
 * 헤더의 알림 종이 쓰는 상태.
 *
 * 쌓여 있던 알림은 목록 API 로 한 번 불러오고, 그 뒤에 생기는 알림은 SSE 로 받습니다.
 *
 * ⚠️ enabled 는 "/api 호출이 한 번 성공한 뒤"에 켜야 합니다 —
 *    EventSource 는 헤더를 못 실어서 쿠키로만 인증하는데, 액세스 토큰이 만료된 채로
 *    붙으면 REST 처럼 재발급 후 재시도를 할 수 없습니다(채팅 소켓과 같은 제약).
 *    로그인 세션(useAuth().user)이 채워졌다는 건 프로필 조회가 성공했다는 뜻이라,
 *    그 시점을 신호로 쓰면 됩니다.
 */
export function useNotifications(enabled: boolean): Result {
  const [items, setItems] = useState<AppNotification[]>([]);
  const [settled, setSettled] = useState(false);

  // 1. 그동안 쌓인 알림을 한 번 불러옵니다.
  useEffect(() => {
    if (!enabled) return;

    let ignore = false; // 로그아웃 등으로 꺼지면 늦게 온 응답은 버립니다.

    fetchNotifications()
      .then((list) => {
        if (ignore) return;

        // 불러오는 동안 SSE 로 먼저 온 알림이 묻히지 않게 합칩니다(서버 응답을 우선).
        setItems((prev) => {
          const fetched = new Set(list.map((item) => item.id));
          return [...prev.filter((item) => !fetched.has(item.id)), ...list].sort((a, b) => b.id - a.id);
        });
      })
      .catch(() => {
        // 알림은 화면의 주된 내용이 아니라서, 못 불러와도 에러를 띄우지 않고 빈 목록으로 둡니다.
        // (SSE 로 오는 새 알림은 그대로 받습니다)
      })
      .finally(() => {
        if (!ignore) setSettled(true);
      });

    return () => {
      ignore = true;
    };
  }, [enabled]);

  // 2. 새 알림을 실시간으로 받습니다.
  useEffect(() => {
    if (!enabled) return;

    const source = new EventSource(NOTIFICATION_SSE_PATH, { withCredentials: true });

    source.onmessage = (event: MessageEvent<string>) => {
      const received = parseNotification(event.data);
      if (!received) return;

      // 재연결 직후 같은 알림이 다시 올 수 있어서 id 로 한 번 걸러 냅니다.
      setItems((prev) => (prev.some((item) => item.id === received.id) ? prev : [received, ...prev]));
    };

    // 끊기면 EventSource 가 서버가 지정한 간격(3초)으로 알아서 다시 붙습니다.
    // 백엔드 emitter 는 5분 뒤 스스로 닫히는데, 그때도 같은 경로로 재연결됩니다.
    source.onerror = () => {};

    return () => source.close();
  }, [enabled]);

  const markRead = useCallback((id: number) => {
    // 먼저 화면을 바꾸고 서버에 알립니다. 실패하면 다음에 목록을 불러올 때 맞춰집니다.
    setItems((prev) => prev.map((item) => (item.id === id ? { ...item, read: true } : item)));
    void markNotificationRead(id).catch(() => {});
  }, []);

  const markAllRead = useCallback(() => {
    // 먼저 화면을 바꾸고 서버에 알립니다. 실패하면 다음에 목록을 불러올 때 맞춰집니다.
    setItems((prev) => prev.map((item) => (item.read ? item : { ...item, read: true })));
    void markNotificationsRead().catch(() => {});
  }, []);

  const remove = useCallback(
    (id: number) => {
      const removed = items.find((item) => item.id === id);
      setItems((prev) => prev.filter((item) => item.id !== id));

      void deleteNotification(id).catch(() => {
        // 서버가 못 지웠으면 되돌립니다. 지워진 듯 보이다가 새로고침에 되살아나는 쪽이 더 혼란스럽습니다.
        if (!removed) return;
        setItems((prev) =>
          prev.some((item) => item.id === id) ? prev : [removed, ...prev].sort((a, b) => b.id - a.id),
        );
      });
    },
    [items],
  );

  return {
    items,
    unreadCount: items.reduce((count, item) => (item.read ? count : count + 1), 0),
    loading: !settled,
    markRead,
    markAllRead,
    remove,
  };
}
