'use client';

import { useEffect, useRef } from 'react';
import { formatMessageDate, isNewDay } from '../lib/date';
import type { ChatMessage } from '../types';
import MessageBubble from './MessageBubble';

type Props = {
  messages: ChatMessage[];
  /** 더 불러올 과거 메시지가 남았는지 */
  hasMore: boolean;
  loadingOlder: boolean;
  onLoadOlder: () => void;
};

/** 맨 위에서 이만큼 안쪽에 들어오면 과거 메시지를 더 불러옵니다. */
const LOAD_THRESHOLD_PX = 60;

/**
 * 방 하나의 메시지 목록.
 *
 * 스크롤 동작이 두 가지라 구분해서 다룹니다.
 * - 새 메시지가 아래에 붙으면 → 맨 아래로 따라 내려갑니다.
 * - 과거 메시지가 위에 붙으면 → 보고 있던 자리를 그대로 유지합니다(안 하면 화면이 튑니다).
 */
export default function ChatRoomView({ messages, hasMore, loadingOlder, onLoadOlder }: Props) {
  const scrollRef = useRef<HTMLDivElement>(null);
  /** 직전 렌더의 마지막 메시지 id — 바뀌었으면 "아래에 새 메시지" 입니다. */
  const lastIdRef = useRef<number | undefined>(undefined);
  /** 과거 메시지를 요청한 순간의 스크롤 상태 — 붙고 나서 자리를 되돌리는 데 씁니다. */
  const anchorRef = useRef<{ height: number; top: number } | null>(null);

  useEffect(() => {
    const element = scrollRef.current;
    if (!element) return;

    const lastId = messages.at(-1)?.id;

    // 방을 처음 열었거나, 맨 아래에 새 메시지가 붙은 경우
    if (lastId !== lastIdRef.current) {
      lastIdRef.current = lastId;
      anchorRef.current = null;
      element.scrollTop = element.scrollHeight;
      return;
    }

    // 과거 메시지가 앞에 붙은 경우 — 늘어난 높이만큼 내려 보던 자리를 유지합니다.
    const anchor = anchorRef.current;
    if (anchor) {
      element.scrollTop = element.scrollHeight - anchor.height + anchor.top;
      anchorRef.current = null;
    }
  }, [messages]);

  const handleScroll = () => {
    const element = scrollRef.current;
    if (!element || !hasMore || loadingOlder) return;
    if (element.scrollTop > LOAD_THRESHOLD_PX) return;

    anchorRef.current = { height: element.scrollHeight, top: element.scrollTop };
    onLoadOlder();
  };

  return (
    <div
      ref={scrollRef}
      onScroll={handleScroll}
      className="flex flex-1 flex-col gap-2 overflow-y-auto overscroll-contain px-4 py-4"
    >
      {loadingOlder && (
        <p className="py-1 text-center text-[11px] text-brand-muted">이전 메시지를 불러오는 중입니다…</p>
      )}
      {!hasMore && messages.length > 0 && (
        <p className="py-1 text-center text-[11px] text-brand-muted">대화의 시작입니다</p>
      )}
      {messages.map((message, index) => {
        const previous = messages[index - 1];

        return (
          <div key={message.id} className="flex flex-col gap-2">
            {isNewDay(message.sentAt, previous?.sentAt) && (
              <p className="my-1 text-center text-[11px] text-brand-muted">{formatMessageDate(message.sentAt)}</p>
            )}
            <MessageBubble
              message={message}
              sameSenderAsPrevious={previous?.type === 'TALK' && previous.senderId === message.senderId}
            />
          </div>
        );
      })}
    </div>
  );
}
