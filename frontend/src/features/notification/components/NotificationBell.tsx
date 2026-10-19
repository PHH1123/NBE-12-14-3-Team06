'use client';

import { useEffect, useId, useRef, useState } from 'react';
import Link from 'next/link';
import { Check } from 'lucide-react';
import { BellIcon, CloseIcon } from '@/components/ui';
import { useAuth } from '@/features/auth/lib/AuthProvider';
import { cn } from '@/lib/cn';
import { formatNotificationTime } from '../lib/date';
import { useNotifications } from '../lib/useNotifications';
import type { AppNotification } from '../types';

const MAX_BADGE_COUNT = 99;

export default function NotificationBell({ transparent = false }: { transparent?: boolean }) {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelId = useId();
  const { items, unreadCount, loading, markRead, markAllRead, remove } = useNotifications(Boolean(user));

  useEffect(() => {
    if (!open) return;

    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
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
        aria-haspopup="dialog"
        aria-controls={open ? panelId : undefined}
        aria-label={unreadCount > 0 ? `알림 ${unreadCount}개 ${open ? '닫기' : '열기'}` : `알림 ${open ? '닫기' : '열기'}`}
        className={cn(
          'relative grid h-[44px] w-[44px] shrink-0 cursor-pointer place-items-center rounded-[10px] border transition-colors duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]',
          transparent
            ? 'border-white/30 text-white hover:bg-white/10'
            : 'border-[#E1E5E8] bg-white text-[#14243A] hover:bg-[#F7F8F8]',
        )}
      >
        <BellIcon className="h-5 w-5" />
        {unreadCount > 0 && (
          <span className="absolute top-0.5 right-0.5 grid min-w-4 place-items-center rounded-full bg-[#b91d1d] px-1 text-[10px] leading-4 font-bold text-white">
            {unreadCount > MAX_BADGE_COUNT ? `${MAX_BADGE_COUNT}+` : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div
          id={panelId}
          role="dialog"
          aria-label="알림 목록"
          className="absolute top-full right-0 z-50 mt-2 w-[min(380px,calc(100vw-32px))] overflow-hidden rounded-xl border border-[#E1E5E8] bg-white text-[#14243A] shadow-lg"
        >
          <div className="flex items-center justify-between gap-3 border-b border-[#E1E5E8] px-4 py-3.5">
            <div className="flex min-w-0 items-center gap-2">
              <span className="text-[15px] font-semibold">알림</span>
              {unreadCount > 0 && (
                <span className="rounded-md bg-[#E9EFF6] px-2 py-0.5 text-[11px] font-semibold text-[#426B93]">
                  {unreadCount}개 안 읽음
                </span>
              )}
            </div>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={markAllRead}
                className="shrink-0 cursor-pointer text-xs font-medium text-[#55749B] transition-colors hover:text-[#14243A] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
              >
                모두 읽음
              </button>
            )}
          </div>

          {items.length === 0 ? (
            <div className="flex flex-col items-center px-5 py-9 text-center">
              <span className="mb-3 grid h-10 w-10 place-items-center rounded-full bg-[#F2F4F6] text-[#8A96A4]">
                <BellIcon className="h-5 w-5" />
              </span>
              <p className="text-sm text-[#67717C]">
                {loading ? '알림을 불러오는 중입니다.' : '새로운 알림이 없습니다.'}
              </p>
            </div>
          ) : (
            <ul className="max-h-[360px] overflow-y-auto">
              {items.map((item) => (
                <li
                  key={item.id}
                  className={cn(
                    'flex items-start gap-2 border-b border-[#E9ECEF] px-3 py-3 last:border-b-0',
                    item.read ? 'bg-white' : 'bg-[#F6F8FB]',
                  )}
                >
                  <span
                    aria-hidden="true"
                    className={cn(
                      'mt-[7px] h-1.5 w-1.5 shrink-0 rounded-full',
                      item.read ? 'bg-[#CBD2DA]' : 'bg-[#55749B]',
                    )}
                  />
                  <NotificationRow
                    notification={item}
                    onRead={() => {
                      if (!item.read) markRead(item.id);
                      if (item.href) setOpen(false);
                    }}
                  />
                  <div className="flex shrink-0 items-center gap-0.5">
                    {!item.read && (
                      <button
                        type="button"
                        onClick={() => markRead(item.id)}
                        title="읽음 처리"
                        aria-label="알림 읽음 처리"
                        className="grid h-7 w-7 cursor-pointer place-items-center rounded-md text-[#8A96A4] transition-colors hover:bg-[#E9EFF6] hover:text-[#426B93] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
                      >
                        <Check className="h-4 w-4" />
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => remove(item.id)}
                      title="알림 삭제"
                      aria-label="알림 삭제"
                      className="grid h-7 w-7 cursor-pointer place-items-center rounded-md text-[#A1AAB6] transition-colors hover:bg-[#F0F2F4] hover:text-[#344055] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
                    >
                      <CloseIcon className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

function NotificationRow({ notification, onRead }: { notification: AppNotification; onRead: () => void }) {
  const time = formatNotificationTime(notification.createdAt);
  const body = (
    <>
      <span className={cn(
        'block text-[13px] leading-[1.55] break-keep',
        notification.read ? 'text-[#67717C]' : 'font-medium text-[#14243A]',
      )}>
        {notification.content}
      </span>
      {time && <span className="mt-1 block text-[11px] leading-4 text-[#8A96A4]">{time}</span>}
    </>
  );
  const className = 'block min-w-0 flex-1 cursor-pointer rounded-md text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]';

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
