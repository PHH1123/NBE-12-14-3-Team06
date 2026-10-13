import { cn } from '@/lib/cn';
import { formatMessageTime } from '../lib/date';
import type { ChatMessage } from '../types';

type Props = {
  message: ChatMessage;
  /** 바로 위 메시지도 같은 사람이 보냈는지 — 보낸이 이름을 한 번만 보여 주기 위해 받습니다. */
  sameSenderAsPrevious: boolean;
};

const BUBBLE = 'max-w-[220px] px-3.5 py-2.5 text-[13.5px] leading-[1.45] whitespace-pre-wrap break-words';

/**
 * 말풍선 한 줄.
 * ENTER/LEAVE(입장·나가기)는 말풍선이 아니라 가운데 안내 문구로 보여 줍니다.
 */
export default function MessageBubble({ message, sameSenderAsPrevious }: Props) {
  if (message.type !== 'TALK') {
    return (
      <p className="my-1 text-center text-[11.5px] text-brand-muted">{message.content}</p>
    );
  }

  const { mine } = message;

  return (
    <div className={cn('flex flex-col', mine ? 'items-end' : 'items-start')}>
      {/* 같은 사람이 연달아 보내면 이름을 반복하지 않습니다. 내 메시지는 이름을 아예 안 붙입니다. */}
      {!mine && !sameSenderAsPrevious && (
        <span className="mb-1 pl-1 text-[11.5px] font-medium text-brand-muted">{message.senderName}</span>
      )}
      <div className={cn('flex items-end gap-1.5', mine && 'flex-row-reverse')}>
        <div
          className={cn(
            BUBBLE,
            mine
              ? 'rounded-[18px] rounded-br-[6px] bg-brand text-white'
              : 'rounded-[18px] rounded-bl-[6px] bg-line-soft text-brand',
          )}
        >
          {message.content}
        </div>
        <time
          dateTime={message.sentAt}
          className={cn('shrink-0 pb-0.5 text-[10.5px]', message.pending ? 'text-brand-muted italic' : 'text-brand-muted')}
        >
          {message.pending ? '전송 중' : formatMessageTime(message.sentAt)}
        </time>
      </div>
    </div>
  );
}
