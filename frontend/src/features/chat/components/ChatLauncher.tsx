'use client';

import { MessageCircle, X } from 'lucide-react';

type Props = {
  open: boolean;
  onToggle: () => void;
};

/** 기존 전역 채팅 위젯 진입점. 연결 상태·읽음 수 등 없는 데이터는 표시하지 않습니다. */
export default function ChatLauncher({ open, onToggle }: Props) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={open}
      aria-label={open ? '채팅 닫기' : '채팅 열기'}
      aria-controls={open ? 'gaji-chat-panel' : undefined}
      title={open ? '채팅 닫기' : '채팅'}
      className="fixed bottom-5 right-5 z-50 flex h-14 w-14 items-center justify-center rounded-2xl border border-white/15 bg-[#14243A] text-white shadow-[0_8px_28px_rgba(20,36,58,0.20)] transition-[background-color,box-shadow] duration-200 hover:bg-[#426B93] hover:shadow-[0_10px_32px_rgba(20,36,58,0.23)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
    >
      {open ? <X className="h-[23px] w-[23px]" aria-hidden="true" /> : <MessageCircle className="h-[24px] w-[24px]" aria-hidden="true" strokeWidth={1.85} />}
    </button>
  );
}
