'use client';

import { ChatIcon, CloseIcon } from '@/components/ui';
import { cn } from '@/lib/cn';

type Props = {
  open: boolean;
  onToggle: () => void;
};

/** 우하단에 항상 떠 있는 원형 채팅 버튼. 패널을 열고 닫습니다. */
export default function ChatLauncher({ open, onToggle }: Props) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={open}
      aria-label={open ? '채팅 닫기' : '채팅 열기'}
      className={cn(
        'fixed right-4 bottom-4 z-50 grid size-14 place-items-center rounded-full text-white',
        'bg-brand shadow-card transition-colors hover:bg-brand-hover',
      )}
    >
      {open ? <CloseIcon className="size-6" /> : <ChatIcon className="size-7" />}
    </button>
  );
}
