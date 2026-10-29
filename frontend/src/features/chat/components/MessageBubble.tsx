import { formatMessageTime } from '../lib/date';
import type { ChatMessage } from '../types';

type Props = {
  message: ChatMessage;
  sameSenderAsPrevious: boolean;
};

/** 상대 메시지는 흰색, 내 메시지는 네이비. 서버가 반환하는 실제 내용만 표시합니다. */
export default function MessageBubble({ message, sameSenderAsPrevious }: Props) {
  if (message.type !== 'TALK') {
    return (
      <div className="flex justify-center py-1.5">
        <p className="max-w-[92%] rounded-full bg-[#EDF0F2] px-3.5 py-1.5 text-center text-[11px] leading-5 text-[#67717C]">
          {message.content}
        </p>
      </div>
    );
  }

  return (
    <div className={`flex flex-col ${message.mine ? 'items-end' : 'items-start'}`}>
      {!message.mine && !sameSenderAsPrevious && (
        <span className="mb-1.5 pl-1 text-[11px] font-semibold text-[#67717C]">{message.senderName}</span>
      )}
      <div className={`flex max-w-full items-end gap-2 ${message.mine ? 'flex-row-reverse' : ''}`}>
        <div
          className={`max-w-[min(260px,70vw)] whitespace-pre-wrap break-words px-3.5 py-2.5 text-[12.5px] leading-[1.7] shadow-[0_1px_2px_rgba(20,36,58,0.04)] ${
            message.mine
              ? 'rounded-2xl rounded-br-[10px] bg-[#14243A] text-white'
              : 'rounded-2xl rounded-bl-[10px] border border-[#E1E5E8] bg-white text-[#202832]'
          }`}
        >
          {message.content}
        </div>
        <time dateTime={message.sentAt} className="shrink-0 pb-0.5 text-[10px] leading-4 tabular-nums text-[#939DA6]">
          {message.pending ? '전송 중' : formatMessageTime(message.sentAt)}
        </time>
      </div>
    </div>
  );
}
