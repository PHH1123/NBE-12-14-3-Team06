'use client';

import Link from 'next/link';
import { AlertCircle, ArrowRight, LoaderCircle, ShieldAlert } from 'lucide-react';

type Props = {
  kind: 'loading' | 'error' | 'forbidden' | 'empty';
  title: string;
  description?: string;
  href?: string;
  action?: string;
  onRetry?: () => void;
  className?: string;
};

/** 화면 전체를 차지하는 로딩·오류 상태만 통일합니다. 정상 페이지 레이아웃이나 표 내부 로딩에는 사용하지 않습니다. */
export default function PageFeedback({ kind, title, description, href, action, onRetry, className = '' }: Props) {
  const loading = kind === 'loading';
  const Icon = kind === 'forbidden' ? ShieldAlert : AlertCircle;
  return (
    <section className={`flex min-h-[55vh] w-full items-center justify-center bg-[#F7F8F8] px-5 py-12 ${className}`}>
      {loading ? (
        <div role="status" className="flex flex-col items-center gap-4 text-center">
          <LoaderCircle aria-hidden="true" className="size-9 animate-spin text-[#426B93]" strokeWidth={1.8} />
          <p className="text-sm font-medium text-[#67717C]">{title}</p>
          {description && <p className="max-w-md text-xs leading-6 text-[#67717C]">{description}</p>}
        </div>
      ) : (
        <div role="alert" className="w-full max-w-[520px] rounded-2xl border border-[#E1E5E8] bg-white px-6 py-10 text-center shadow-[0_4px_20px_rgba(30,39,57,0.045)] sm:px-10">
          <div className="mx-auto flex size-12 items-center justify-center rounded-xl bg-[#EDF0F2] text-[#426B93]">
            <Icon aria-hidden="true" className="size-6" strokeWidth={1.8} />
          </div>
          <h1 className="mt-5 text-lg font-semibold text-[#14243A] sm:text-xl">{title}</h1>
          {description && <p className="mt-3 break-words text-sm leading-6 text-[#67717C]">{description}</p>}
          {(href || onRetry) && <div className="mt-7 flex justify-center">
            {href ? <Link href={href} className="group inline-flex h-11 items-center gap-2 rounded-[10px] border border-[#DDE3EA] bg-white px-5 text-sm font-semibold text-[#14243A] transition-colors hover:bg-[#F7F8F8]">{action || '목록으로 돌아가기'}<ArrowRight aria-hidden="true" className="size-4 transition-transform group-hover:translate-x-1" /></Link>
              : <button type="button" onClick={onRetry} className="h-11 rounded-[10px] bg-[#14243A] px-5 text-sm font-semibold text-white transition-colors hover:bg-[#1F3757]">{action || '다시 시도'}</button>}
          </div>}
        </div>
      )}
    </section>
  );
}
