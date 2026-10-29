'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { AlertCircle, ArrowLeft, RotateCcw } from 'lucide-react';
import { AppShell } from '@/components/layout';

/** 결제 실패와 서버 승인 실패 모두 동일한 화면에서 이유 및 다음 행동을 안내합니다. */
export default function FailPage() {
  const searchParams = useSearchParams();
  const code = searchParams.get('code') ?? 'UNKNOWN';
  const message = searchParams.get('message') ?? '결제 중 문제가 발생했습니다.';
  const postId = searchParams.get('postId') ?? '';
  const paymentId = searchParams.get('paymentId') ?? '';
  const flow = searchParams.get('flow') ?? '';
  const isExtra = flow === 'extra';
  const canRetry = Number.isSafeInteger(Number(postId)) && Number(postId) > 0 && Number.isSafeInteger(Number(paymentId)) && Number(paymentId) > 0;
  const retryHref = `/client/posts/new/payment?${new URLSearchParams({ postId, paymentId, flow })}`;
  const backHref = isExtra && Number(postId) > 0 ? `/client/posts/${postId}` : '/client/posts';

  return (
    <AppShell>
      <section className="min-h-[calc(100vh-90px)] bg-[#F5F6F8] px-5 py-16 text-[#1E2739] sm:py-24">
        <div className="mx-auto w-full max-w-[720px]">
          <div className="rounded-[20px] border border-[#E0E4E9] bg-white px-6 py-12 text-center shadow-[0_2px_3px_rgba(30,39,57,0.04)] sm:px-12 sm:py-14">
            <div className="mx-auto flex h-[88px] w-[88px] items-center justify-center rounded-full bg-[#F9ECEA]">
              <AlertCircle size={42} strokeWidth={1.9} className="text-[#AD5B52]" />
            </div>
            <h1 className="mt-7 text-[30px] font-bold tracking-[-0.035em] sm:text-[34px]">결제를 완료하지 못했습니다</h1>
            <p className="mt-3 text-[15px] text-[#657184]">결제 내역을 확인하고 다시 시도해 주세요.</p>

            <div className="mt-9 rounded-[14px] border border-[#E3E7EC] bg-[#FAFBFC] px-5 py-6 text-left sm:px-7">
              <dl className="space-y-5 text-[14px] sm:text-[15px]">
                <div className="flex items-start justify-between gap-6">
                  <dt className="shrink-0 text-[#657184]">실패 사유</dt>
                  <dd className="break-words text-right font-medium text-[#1E2739]">{message}</dd>
                </div>
                <div className="flex items-start justify-between gap-6 border-t border-[#E3E7EC] pt-5">
                  <dt className="shrink-0 text-[#657184]">오류 코드</dt>
                  <dd className="break-all text-right text-[#657184]">{code}</dd>
                </div>
              </dl>
            </div>

            <div className="mt-8 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Link href={backHref} className="flex h-[52px] items-center justify-center gap-2 rounded-[12px] border border-[#DDE2E8] bg-white text-[15px] font-semibold text-[#344055] transition-colors hover:bg-[#F5F6F8]">
                <ArrowLeft size={16} />{isExtra ? '공고 상세로' : '내 공고 목록으로'}
              </Link>
              {canRetry && (
                <Link href={retryHref} className="flex h-[52px] items-center justify-center gap-2 rounded-[12px] bg-[#1E2739] text-[15px] font-semibold text-white transition-colors hover:bg-[#344055]">
                  <RotateCcw size={16} />다시 결제하기
                </Link>
              )}
            </div>
          </div>
        </div>
      </section>
    </AppShell>
  );
}
