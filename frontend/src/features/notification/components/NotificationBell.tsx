'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { BellIcon, CloseIcon } from '@/components/ui';
// 배럴(@/features/auth)로 가져오면 auth → LoginPage → AppShell → Header → notification 으로
// 순환 import 가 생겨서 모듈 경로로 직접 가져옵니다. (AppShell·채팅 위젯도 같은 이유로 그렇게 합니다)
import { useAuth } from '@/features/auth/lib/AuthProvider';
import { cn } from '@/lib/cn';
import { formatNotificationTime } from '../lib/date';
import { useNotifications } from '../lib/useNotifications';
import type { AppNotification } from '../types';

/** 배지에 그대로 적는 최대 개수. 넘으면 "99+" */
const MAX_BADGE_COUNT = 99;

/**
 * 헤더의 알림 종(이름 왼쪽).
 *
 * 종을 누르면 알림 목록이 펼쳐지고, 안 읽은 알림이 있으면 개수를 배지로 보여 줍니다.
 * 로그인 세션이 없으면 아무것도 그리지 않습니다 — 모의 데이터로 user 를 직접 넘기는
 * 화면에서도 종이 혼자 떠 있지 않게 하려는 것입니다.
 */
export default function NotificationBell() {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  // 세션이 채워졌다는 건 프로필 조회가 성공했다는 뜻 — 그때 SSE 를 붙입니다(useNotifications 주석 참고).
  const { items, unreadCount, loading, markRead, markAllRead, remove } = useNotifications(Boolean(user));

  useEffect(() => {
    if (!open) return;

    // 목록 바깥을 누르면 닫습니다.
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };

    // Esc 로 닫고, 포커스는 종으로 되돌립니다.
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      setOpen(false);
      buttonRef.current?.focus();
    };

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  if (!user) return null;

  return (
    <div ref={rootRef} className="relative flex items-center">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-expanded={open}
        aria-haspopup="true"
        aria-label={unreadCount > 0 ? `알림 ${unreadCount}개 ${open ? '닫기' : '열기'}` : `알림 ${open ? '닫기' : '열기'}`}
        className="relative grid size-9 shrink-0 cursor-pointer place-items-center rounded-full text-brand transition-colors hover:bg-line-soft focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
      >
        <BellIcon className="size-6" />

        {unreadCount > 0 && (
          <span className="absolute top-0.5 right-0.5 grid min-w-4 place-items-center rounded-full bg-[#b91d1d] px-1 text-[10px] leading-4 font-bold text-white">
            {unreadCount > MAX_BADGE_COUNT ? `${MAX_BADGE_COUNT}+` : unreadCount}
          </span>
        )}
      </button>

      {/* 패널 너비는 좌우 버튼 자리를 빼고도 본문이 충분히 들어가도록 340px 보다 조금 넓게 잡았습니다. */}
      {open && (
        <div className="absolute top-full right-0 z-20 mt-3 w-[380px] overflow-hidden rounded-[20px] border border-line bg-white shadow-card">
          <div className="flex items-center justify-between gap-2 border-b border-line px-5 py-3.5">
            <p className="text-base leading-5 font-semibold text-brand">알림</p>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={markAllRead}
                className="cursor-pointer text-sm leading-5 font-medium text-brand-muted transition-colors hover:text-brand"
              >
                모두 읽음
              </button>
            )}
          </div>

          {items.length === 0 ? (
            <p className="px-5 py-10 text-center text-sm leading-5 text-brand-muted">
              {loading ? '알림을 불러오는 중입니다.' : '새로운 알림이 없습니다.'}
            </p>
          ) : (
            <ul className="max-h-[360px] overflow-y-auto">
              {items.map((item) => (
                <li key={item.id} className="relative border-b border-line-soft last:border-b-0">
                  <NotificationRow
                    notification={item}
                    onRead={() => {
                      if (!item.read) markRead(item.id);
                      if (item.href) setOpen(false);
                    }}
                  />

                  {/*
                    행 전체가 button/Link 라서 그 안에 버튼을 넣을 수 없습니다(중첩 불가).
                    형제로 두고 겹쳐 올립니다 — 행의 pl-[58px]·pr-10 이 글자와 겹치지 않게 자리를 비워 둡니다.
                  */}
                  {!item.read && (
                    // 안 읽은 알림에만 나옵니다. 행을 눌러도 읽음이 되지만 그때는 링크를 타고 이동해 버려서,
                    // "이동하지 않고 읽음만" 하려면 이 버튼이 필요합니다.
                    <button
                      type="button"
                      onClick={() => markRead(item.id)}
                      className="absolute top-3.5 left-4 cursor-pointer rounded-full border border-line bg-white px-2 py-[3px] text-[11px] leading-[14px] font-semibold text-brand-muted transition-colors hover:border-brand-muted hover:text-brand focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
                    >
                      읽음
                    </button>
                  )}

                  <button
                    type="button"
                    onClick={() => remove(item.id)}
                    aria-label="알림 삭제"
                    className="absolute top-3 right-3 grid size-6 cursor-pointer place-items-center rounded-full text-brand-muted transition-colors hover:bg-line hover:text-brand focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
                  >
                    <CloseIcon className="size-3.5" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * 알림 한 줄.
 * url 이 있으면 눌러서 이동하고, 없으면(지금 백엔드가 보내는 대부분) 눌러서 읽음 처리만 합니다.
 */
function NotificationRow({ notification, onRead }: { notification: AppNotification; onRead: () => void }) {
  const time = formatNotificationTime(notification.createdAt);

  const body = (
    <span className="block">
      <span
        className={cn(
          'block text-sm leading-5 break-keep',
          notification.read ? 'text-brand-muted' : 'font-medium text-brand',
        )}
      >
        {notification.content}
      </span>
      {time && <span className="mt-1 block text-xs leading-4 text-brand-muted">{time}</span>}
    </span>
  );

  // 좌우 여백은 겹쳐 올린 버튼 자리입니다 — 왼쪽은 "읽음", 오른쪽은 삭제(NotificationBell 의 주석 참고).
  // 읽은 알림은 왼쪽 버튼이 없지만, 줄이 밀리지 않도록 같은 여백을 유지합니다.
  const className =
    'block w-full cursor-pointer py-3.5 pr-10 pl-[58px] text-left transition-colors hover:bg-line-soft';

  return notification.href ? (
    <Link href={notification.href} onClick={onRead} className={className}>
      {body}
    </Link>
  ) : (
    <button type="button" onClick={onRead} className={className}>
      {body}
    </button>
  );
}
