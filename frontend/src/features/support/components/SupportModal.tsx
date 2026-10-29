'use client';

import { useEffect, useId, useRef } from 'react';
import { PhoneCall, X } from 'lucide-react';

// 이전 고객센터 화면에서 사용하던 임시 대표 번호입니다. 실제 운영 번호 확정 후 교체하세요.
const SUPPORT_PHONE = '1234-1234';
const SUPPORT_PHONE_HREF = 'tel:12341234';

type SupportContentProps = {
  onClose: () => void;
};

export function SupportContent({ onClose }: SupportContentProps) {
  const headingId = useId();

  return (
    <div className="w-full max-w-lg space-y-6 rounded-2xl bg-white p-6 shadow-xl sm:p-8">
      <div className="flex items-center justify-between border-b border-[#E1E5E8] pb-4">
        <div className="flex items-center gap-2">
          <PhoneCall className="h-5 w-5 text-[#426B93]" />
          <h3 id={headingId} className="text-xl font-semibold text-[#14243A]">고객센터 &amp; 문의</h3>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="고객센터 닫기"
          className="rounded p-1 text-[#67717C] transition-colors hover:text-[#14243A] focus-visible:outline-2 focus-visible:outline-[#426B93]"
        >
          <X className="h-5 w-5" />
        </button>
      </div>

      <div className="space-y-1 rounded-xl border border-[#E1E5E8] bg-[#F7F8F8] p-4 text-xs">
        <p className="font-semibold text-[#14243A]">GAJI 고객안심 지원팀</p>
        <p className="leading-relaxed text-[#67717C]">
          서비스 이용 중 궁금한 사항은 아래 자주 묻는 질문을 확인하시거나,
          하단의 전화 문의를 이용해 주세요.
        </p>
      </div>

      <div className="space-y-3">
        <h4 className="text-xs font-semibold uppercase tracking-wider text-[#67717C]">자주 묻는 질문</h4>
        <div className="space-y-2 text-xs">
          <div className="rounded-lg bg-[#EDF0F2] p-3">
            <p className="font-semibold text-[#14243A]">Q. 동행 매니저는 어떻게 검증되나요?</p>
            <p className="mt-1 text-[#67717C]">
              본인 인증과 필수 안전 동행 교육을 이수한 매니저가 활동하며,
              경력·활동 내역 등의 정보는 프로필을 통해 확인할 수 있습니다.
            </p>
          </div>
          <div className="rounded-lg bg-[#EDF0F2] p-3">
            <p className="font-semibold text-[#14243A]">Q. 노쇼 및 무단 취소는 어떻게 처리되나요?</p>
            <p className="mt-1 text-[#67717C]">
              서비스 운영 정책에 따라 활동 제재 및 등급 하향 등의 조치가 적용될 수 있습니다.
            </p>
          </div>
        </div>
      </div>

      <div className="space-y-3 border-t border-[#E1E5E8] pt-4">
        <h4 className="text-xs font-semibold uppercase tracking-wider text-[#67717C]">전화 문의</h4>
        <p className="text-xs leading-relaxed text-[#67717C]">
          서비스 이용 문의는 아래 번호를 눌러 전화로 연결할 수 있습니다.
        </p>
        <a
          href={SUPPORT_PHONE_HREF}
          aria-label={`고객센터 ${SUPPORT_PHONE} 전화 걸기`}
          className="group flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-[#14243A] px-5 py-3 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[#426B93] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
        >
          <PhoneCall className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5" />
          전화 연결 · {SUPPORT_PHONE}
        </a>
      </div>
    </div>
  );
}

type SupportModalProps = {
  isOpen: boolean;
  onClose: () => void;
};

export default function SupportModal({ isOpen, onClose }: SupportModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) return;
    const before = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const firstButton = dialogRef.current?.querySelector<HTMLButtonElement>('button');
    firstButton?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const elements = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not(:disabled), textarea:not(:disabled), input:not(:disabled), a[href]'));
      if (!elements.length) return;
      const first = elements[0];
      const last = elements[elements.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = before;
      document.removeEventListener('keydown', handleKeyDown);
      previousFocus?.focus();
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center overflow-y-auto bg-black/60 p-4"
      onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}
    >
      <div ref={dialogRef} role="dialog" aria-modal="true" aria-label="고객센터 및 문의" className="my-auto w-full max-w-lg motion-safe:animate-in motion-safe:fade-in motion-safe:zoom-in-95 motion-safe:duration-150">
        <SupportContent onClose={onClose} />
      </div>
    </div>
  );
}
