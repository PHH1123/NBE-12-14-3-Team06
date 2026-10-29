'use client';

import { useEffect, useRef } from 'react';
import { formatMessageDate, isNewDay } from '../lib/date';
import type { ChatMessage } from '../types';
import MessageBubble from './MessageBubble';

type Props = {
  messages: ChatMessage[];
  hasMore: boolean;
  loadingOlder: boolean;
  onLoadOlder: () => void;
};

const LOAD_THRESHOLD_PX = 60;

/** 기존 읽기 위치 보존·이전 메시지 불러오기 동작은 유지합니다. */
export default function ChatRoomView({ messages, hasMore, loadingOlder, onLoadOlder }: Props) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const lastIdRef = useRef<number | undefined>(undefined);
  const anchorRef = useRef<{ height: number; top: number } | null>(null);

  useEffect(() => {
    const element = scrollRef.current;
    if (!element) return;
    const lastId = messages.at(-1)?.id;

    if (lastId !== lastIdRef.current) {
      lastIdRef.current = lastId;
      anchorRef.current = null;
      element.scrollTop = element.scrollHeight;
      return;
    }
    const anchor = anchorRef.current;
    if (anchor) {
      element.scrollTop = element.scrollHeight - anchor.height + anchor.top;
      anchorRef.current = null;
    }
  }, [messages]);

  const handleScroll = () => {
    const element = scrollRef.current;
    if (!element || !hasMore || loadingOlder || element.scrollTop > LOAD_THRESHOLD_PX) return;
    anchorRef.current = { height: element.scrollHeight, top: element.scrollTop };
    onLoadOlder();
  };

  return (
    <div
      ref={scrollRef}
      onScroll={handleScroll}
      aria-label="채팅 메시지"
      className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto overscroll-contain bg-[#F7F8F8] px-4 py-5"
    >
      {loadingOlder && <p className="py-1 text-center text-[11px] text-[#67717C]">이전 메시지를 불러오는 중입니다…</p>}
      {!hasMore && messages.length > 0 && <p className="py-1 text-center text-[10.5px] text-[#9AA3AC]">대화의 시작입니다</p>}
      {messages.length === 0 && (
        <div className="flex flex-1 flex-col items-center justify-center text-center">
          <p className="text-[13px] font-semibold text-[#14243A]">첫 메시지를 보내보세요</p>
          <p className="mt-1.5 text-xs text-[#7B858F]">상대방과의 대화가 여기에 표시됩니다.</p>
        </div>
      )}
      {messages.map((message, index) => {
        const previous = messages[index - 1];
        return (
          <div key={message.id} className="flex flex-col gap-3">
            {isNewDay(message.sentAt, previous?.sentAt) && (
              <div className="flex items-center gap-3 py-1.5" aria-label={formatMessageDate(message.sentAt)}>
                <span aria-hidden="true" className="h-px flex-1 bg-[#E1E5E8]" />
                <span className="shrink-0 text-[10px] font-medium text-[#8B949E]">{formatMessageDate(message.sentAt)}</span>
                <span aria-hidden="true" className="h-px flex-1 bg-[#E1E5E8]" />
              </div>
            )}
            <MessageBubble message={message} sameSenderAsPrevious={previous?.type === 'TALK' && previous.senderId === message.senderId} />
          </div>
        );
      })}
    </div>
  );
}
