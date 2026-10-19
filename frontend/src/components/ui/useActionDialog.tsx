'use client';

import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { AlertTriangle, CheckCircle2 } from 'lucide-react';

/** 기존 GAJI 리뷰·승인 확인창 디자인을 따르는 공통 확인/안내 모달. */
type DialogOptions = {
  title: string;
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: 'default' | 'danger';
  kind?: 'confirm' | 'notice';
};

export function useActionDialog() {
  const [active, setActive] = useState<DialogOptions | null>(null);
  const resolver = useRef<((confirmed: boolean) => void) | null>(null);
  const previousFocus = useRef<HTMLElement | null>(null);
  const primaryButton = useRef<HTMLButtonElement | null>(null);
  const cancelButton = useRef<HTMLButtonElement | null>(null);
  const titleId = useId();
  const descriptionId = useId();

  const close = useCallback((confirmed: boolean) => {
    const resolve = resolver.current;
    if (!resolve) return;
    resolver.current = null;
    setActive(null);
    resolve(confirmed);
  }, []);

  const confirm = useCallback((options: DialogOptions): Promise<boolean> => {
    return new Promise<boolean>((resolve) => {
      // 클릭이 중복 발생해도 기존 요청을 덮어쓰지 않는다.
      if (resolver.current) { resolve(false); return; }
      previousFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      resolver.current = resolve;
      setActive({ ...options, kind: 'confirm' });
    });
  }, []);

  const notice = useCallback((options: Omit<DialogOptions, 'kind' | 'cancelLabel'>): Promise<void> => {
    return new Promise<void>((resolve) => {
      if (resolver.current) { resolve(); return; }
      previousFocus.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      resolver.current = () => resolve();
      setActive({ ...options, kind: 'notice' });
    });
  }, []);

  useEffect(() => {
    if (!active) {
      previousFocus.current?.focus();
      previousFocus.current = null;
      return;
    }
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    (active.kind === 'notice' ? primaryButton : cancelButton).current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        close(false);
      }
      // 포커스가 모달 밖으로 빠져나가지 않도록 유지
      if (event.key === 'Tab') {
        const buttons = [cancelButton.current, primaryButton.current].filter((button): button is HTMLButtonElement => !!button);
        if (!buttons.length) return;
        const first = buttons[0];
        const last = buttons[buttons.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [active, close]);

  useEffect(() => () => {
    // 페이지 이동 중 대기 중인 확인 Promise가 남지 않도록 정리한다.
    resolver.current?.(false);
    resolver.current = null;
  }, []);

  const dialog = active && typeof document !== 'undefined' ? createPortal(
    <div
      className="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 p-4"
      onMouseDown={(event) => { if (event.target === event.currentTarget && active.kind === 'notice') close(false); }}
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={active.description ? descriptionId : undefined}
        className="w-full max-w-sm rounded-2xl border border-[#E1E5E8] bg-white p-6 text-center shadow-xl"
      >
        <div className={`mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full ${active.tone === 'danger' ? 'bg-[#B4413C]/10 text-[#B4413C]' : 'bg-[#426B93]/10 text-[#426B93]'}`}>
          {active.tone === 'danger' ? <AlertTriangle className="h-6 w-6" aria-hidden="true" /> : <CheckCircle2 className="h-6 w-6" aria-hidden="true" />}
        </div>
        <h2 id={titleId} className="text-lg font-semibold text-[#14243A]">{active.title}</h2>
        {active.description && <p id={descriptionId} className="mt-3 whitespace-pre-line text-xs leading-relaxed text-[#67717C]">{active.description}</p>}
        <div className="mt-6 flex gap-2">
          {active.kind !== 'notice' && (
            <button
              type="button"
              ref={cancelButton}
              onClick={() => close(false)}
              className="h-11 flex-1 rounded-lg border border-[#E1E5E8] bg-white text-xs font-semibold text-[#67717C] transition-colors hover:bg-[#F7F8F8] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
            >
              {active.cancelLabel ?? '취소'}
            </button>
          )}
          <button
            type="button"
            ref={primaryButton}
            onClick={() => close(true)}
            className={`h-11 flex-1 rounded-lg text-xs font-semibold text-white transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93] ${active.tone === 'danger' ? 'bg-[#B4413C] hover:bg-[#9A3530]' : 'bg-[#14243A] hover:bg-[#1F3757]'}`}
          >
            {active.confirmLabel ?? (active.kind === 'notice' ? '확인' : '진행하기')}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  ) : null;

  return { confirm, notice, dialog };
}
