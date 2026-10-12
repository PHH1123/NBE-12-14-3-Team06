'use client';

import { useRef, useState, type KeyboardEvent } from 'react';
import { cn } from '@/lib/cn';

type Props = {
  /** 메시지 전송. 공백만 입력한 경우에는 불리지 않습니다. */
  onSend: (content: string) => void;
  disabled?: boolean;
};

/**
 * 패널 아래쪽 입력창.
 * Enter 로 보내고 Shift+Enter 로 줄바꿈합니다. 줄 수에 따라 높이가 조금 늘어납니다.
 */
export default function ChatComposer({ onSend, disabled }: Props) {
  const [value, setValue] = useState('');
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const send = () => {
    const content = value.trim();
    if (!content || disabled) return;

    onSend(content);
    setValue('');
    // 보낸 뒤에도 계속 입력할 수 있게 포커스를 유지합니다.
    inputRef.current?.focus();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    // 한글 입력 중(조합 중)인 Enter 는 글자 확정이라서 전송하지 않습니다.
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return;
    event.preventDefault();
    send();
  };

  return (
    <form
      className="flex items-end gap-2 border-t border-line bg-white px-3 py-3"
      onSubmit={(event) => {
        event.preventDefault();
        send();
      }}
    >
      <label className="sr-only" htmlFor="chat-composer-input">
        메시지 입력
      </label>
      <textarea
        id="chat-composer-input"
        ref={inputRef}
        rows={1}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        onKeyDown={handleKeyDown}
        disabled={disabled}
        placeholder="메시지를 입력하세요"
        className={cn(
          'max-h-24 min-h-10 flex-1 resize-none rounded-[18px] border border-line bg-line-soft px-3.5 py-2.5',
          'text-[13.5px] leading-[1.45] text-brand placeholder:text-brand-muted',
          'focus:border-brand-muted focus:bg-white focus:outline-none disabled:opacity-60',
        )}
      />
      <button
        type="submit"
        disabled={disabled || !value.trim()}
        className={cn(
          'h-10 shrink-0 rounded-[18px] px-4 text-[13.5px] font-semibold text-white transition-colors',
          'bg-brand hover:bg-brand-hover disabled:cursor-not-allowed disabled:bg-brand-muted',
        )}
      >
        전송
      </button>
    </form>
  );
}
