'use client';

import { useRef, useState, type KeyboardEvent } from 'react';
import { SendHorizontal } from 'lucide-react';

type Props = {
  /** 서버 연결이 끊겼다면 false — 입력 내용은 지우지 않습니다. */
  onSend: (content: string) => boolean;
  disabled?: boolean;
};

/** Enter로 전송하고 Shift+Enter로 줄바꿈합니다. */
export default function ChatComposer({ onSend, disabled }: Props) {
  const [value, setValue] = useState('');
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const send = () => {
    const content = value.trim();
    if (!content || disabled) return;
    if (!onSend(content)) return;
    setValue('');
    inputRef.current?.focus();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return;
    event.preventDefault();
    send();
  };

  return (
    <form className="flex shrink-0 items-end gap-2.5 border-t border-[#E1E5E8] bg-white px-4 py-3.5" onSubmit={(event) => { event.preventDefault(); send(); }}>
      <label className="sr-only" htmlFor="chat-composer-input">메시지 입력</label>
      <textarea
        id="chat-composer-input"
        ref={inputRef}
        rows={1}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        onKeyDown={handleKeyDown}
        disabled={disabled}
        placeholder={disabled ? '연결을 기다리는 중…' : '메시지를 입력하세요'}
        className="max-h-28 min-h-11 min-w-0 flex-1 resize-none rounded-xl border border-[#E1E5E8] bg-[#F7F8F8] px-3.5 py-3 text-[12.5px] leading-[1.55] text-[#14243A] outline-none transition-[border-color,background-color] duration-200 placeholder:text-[#9AA3AC] focus:border-[#426B93] focus:bg-white disabled:cursor-not-allowed disabled:opacity-70"
      />
      <button
        type="submit"
        disabled={disabled || !value.trim()}
        aria-label="메시지 전송"
        title="메시지 전송"
        className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-[#14243A] text-white transition-colors duration-200 hover:bg-[#426B93] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93] disabled:cursor-not-allowed disabled:bg-[#D5DBE0] disabled:text-[#8B949E]"
      >
        <SendHorizontal className="h-[19px] w-[19px]" aria-hidden="true" strokeWidth={1.8} />
      </button>
    </form>
  );
}
