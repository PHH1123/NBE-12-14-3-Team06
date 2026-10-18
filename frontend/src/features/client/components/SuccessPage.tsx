'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { Check, LoaderCircle } from 'lucide-react';
import { AppShell } from '@/components/layout';
import { ApiError } from '@/lib/api';
import { fetchConfirmPayment, type PaymentConfirm } from '../api';

const REDIRECT_DELAY_MS = 3000;

/** 토스 결제 승인 결과를 확인한 뒤 최초 결제 또는 추가 결제의 다음 화면으로 이동합니다. */
export default function SuccessPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [confirmed, setConfirmed] = useState<PaymentConfirm | null>(null);

  const orderId = searchParams.get('orderId') ?? '';
  const paymentKey = searchParams.get('paymentKey') ?? '';
  const amount = searchParams.get('amount') ?? '';
  const paymentIdValue = searchParams.get('paymentId') ?? '';
  const postIdValue = searchParams.get('postId') ?? '';
  const flow = searchParams.get('flow') ?? '';
  const isExtra = flow === 'extra';
  const paymentId = Number(paymentIdValue);
  const postId = Number(postIdValue);
  const validIds = Number.isSafeInteger(paymentId) && paymentId > 0 && Number.isSafeInteger(postId) && postId > 0;

  const nextHref = isExtra
    ? `/client/posts/${postIdValue}`
    : `/client/posts/complete?${new URLSearchParams({ postId: postIdValue, paymentId: paymentIdValue })}`;
  const nextLabel = isExtra ? '공고 상세로 이동' : '공고 등록 완료 확인';

  // 한 번 승인한 결제에 대해 중복 승인 요청이 나가지 않도록 Promise 자체를 보관합니다.
  // 개발 모드에서 effect 정리/재실행이 발생해도 다음 effect 가 같은 요청의 결과를 이어받습니다.
  const requestRef = useRef<{ key: string; promise: Promise<PaymentConfirm> } | null>(null);

  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const requestKey = `${paymentIdValue}:${orderId}:${paymentKey}`;
    if (requestRef.current?.key !== requestKey) {
      const promise = validIds && orderId && paymentKey && Number.isFinite(Number(amount)) && Number(amount) > 0
        ? fetchConfirmPayment(paymentId, { orderId, paymentKey, amount })
        : Promise.reject(new Error('결제 승인에 필요한 정보가 부족합니다.'));
      requestRef.current = { key: requestKey, promise };
      setConfirmed(null);
    }

    requestRef.current.promise
      .then((data) => {
        if (!active) return;
        setConfirmed(data);
        timer = setTimeout(() => router.replace(nextHref), REDIRECT_DELAY_MS);
      })
      .catch((error: unknown) => {
        if (!active) return;
        const code = error instanceof ApiError ? error.statusCode : 'UNKNOWN';
        const message = error instanceof Error ? error.message : '결제 승인에 실패했습니다.';
        const query = new URLSearchParams({
          code: String(code), message, postId: postIdValue, paymentId: paymentIdValue, flow,
        });
        router.replace(`/client/posts/new/payment/fail?${query.toString()}`);
      });

    return () => {
      active = false;
      if (timer) clearTimeout(timer);
    };
  }, [amount, flow, nextHref, orderId, paymentId, paymentIdValue, paymentKey, postIdValue, router, validIds]);

  const paidAmount = confirmed ? Number(confirmed.amount) : null;

  return (
    <AppShell>
      <section className="min-h-[calc(100vh-90px)] bg-[#F5F6F8] px-5 py-16 text-[#1E2739] sm:py-24">
        <div className="mx-auto w-full max-w-[720px]">
          <div className="rounded-[20px] border border-[#E0E4E9] bg-white px-6 py-12 text-center shadow-[0_2px_3px_rgba(30,39,57,0.04)] sm:px-12 sm:py-14">
            <div className="mx-auto flex h-[88px] w-[88px] items-center justify-center rounded-full bg-[#E8EFF7]">
              {confirmed ? <Check size={44} strokeWidth={2.6} className="text-[#55749B]" /> : <LoaderCircle size={40} className="animate-spin text-[#55749B]" />}
            </div>
            <h1 className="mt-7 text-[30px] font-bold tracking-[-0.035em] sm:text-[34px]">
              {confirmed ? (isExtra ? '추가 결제가 완료되었습니다' : '결제가 완료되었습니다') : '결제 승인을 확인하고 있습니다'}
            </h1>
            <p className="mt-3 text-[15px] text-[#657184]">
              {confirmed ? '결제 정보가 안전하게 확인되었습니다.' : '승인 결과를 확인하는 동안 잠시만 기다려 주세요.'}
            </p>

            {confirmed && (
              <div className="mt-9 rounded-[14px] border border-[#E3E7EC] bg-[#FAFBFC] px-5 py-6 text-left sm:px-7">
                <dl className="space-y-5 text-[14px] sm:text-[15px]">
                  <div className="flex items-center justify-between gap-4">
                    <dt className="text-[#657184]">결제 상태</dt>
                    <dd className="font-semibold text-[#476A50]">결제 완료</dd>
                  </div>
                  <div className="flex items-center justify-between gap-4">
                    <dt className="text-[#657184]">결제 금액</dt>
                    <dd className="text-[22px] font-bold tabular-nums text-[#1E2739]">{paidAmount != null && Number.isFinite(paidAmount) ? `${paidAmount.toLocaleString('ko-KR')}원` : '금액 확인 중'}</dd>
                  </div>
                  <div className="flex items-start justify-between gap-4 border-t border-[#E3E7EC] pt-5">
                    <dt className="shrink-0 text-[#657184]">주문 번호</dt>
                    <dd className="break-all text-right font-medium text-[#344055]">{confirmed.orderId}</dd>
                  </div>
                </dl>
              </div>
            )}

            <p className="mt-7 text-[13px] text-[#738094]">
              {confirmed ? `잠시 후 ${isExtra ? '공고 상세' : '공고 등록 완료'} 화면으로 이동합니다.` : '결제 확인 중에는 페이지를 닫지 말아 주세요.'}
            </p>
            {confirmed && (
              <button type="button" onClick={() => router.replace(nextHref)} className="mt-5 flex h-[52px] w-full items-center justify-center rounded-[12px] bg-[#1E2739] text-[15px] font-semibold text-white transition-colors hover:bg-[#344055]">
                {nextLabel}
              </button>
            )}
          </div>
          <div className="mt-5 text-center text-[13px] text-[#738094]">
            {confirmed ? <Link href="/client/posts" className="underline underline-offset-4 hover:text-[#1E2739]">내 공고 목록</Link> : 'GAJI · 안전한 병원 동행 서비스'}
          </div>
        </div>
      </section>
    </AppShell>
  );
}
