'use client';

import { useEffect, useRef } from 'react';
import { formatMessageDate, isNewDay } from '../lib/date';
import type { ChatMessage } from '../types';
import MessageBubble from './MessageBubble';

type Props = {
  messages: ChatMessage[];
};

/**
 * 방 하나의 메시지 목록.
 * 날짜가 바뀌는 자리에 구분선을 넣고, 메시지가 늘어나면 맨 아래로 따라 내려갑니다.
 */
export default function ChatRoomView({ messages }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null);

  // 방을 열었을 때와 메시지가 추가될 때 가장 최근 메시지가 보이도록 맨 아래로 내립니다.
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' });
  }, [messages.length]);

  return (
    <div className="flex flex-1 flex-col gap-2 overflow-y-auto overscroll-contain px-4 py-4">
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
      <div ref={bottomRef} />
    </div>
  );
}
