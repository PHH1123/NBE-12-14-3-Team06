'use client';

import {
  loadTossPayments,
  type TossPaymentsWidgets,
} from '@tosspayments/tosspayments-sdk';
import { ChevronLeft, CreditCard, ShieldCheck } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { useRequireAuth } from '@/features/auth';
import { fetchPayment, type PaymentDto } from '@/features/payment';
import { fetchPostRaw, type PostDto } from '@/features/post';

const clientKey = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY;
const CARD = 'rounded-2xl border border-[#E1E5E8] bg-white shadow-[0_1px_3px_rgba(25,33,61,0.04)]';

function formatWon(value: number) {
  return `${value.toLocaleString('ko-KR')}원`;
}

function paymentOrderId() {
  return btoa(Math.random().toString()).slice(0, 20);
}

function customerPhone(phone: string) {
  const digits = phone.replace(/\D/g, '');
  return digits.length >= 8 && digits.length <= 15 ? digits : undefined;
}

function formatEscortDate(dateTime: string) {
  const date = new Date(dateTime);
  if (!Number.isFinite(date.getTime())) return dateTime;
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric',
    hour: 'numeric', minute: '2-digit', hour12: true,
  }).format(date);
}

function Notice({ title, message, href, action }: { title: string; message: string; href: string; action: string }) {
  return (
    <main className="mx-auto w-full max-w-[720px] px-5 py-14 sm:px-8 lg:py-16">
      <div className={`${CARD} px-6 py-10 text-center sm:px-10`}>
        <h1 className="text-2xl font-semibold text-[#14243A]">{title}</h1>
        <p className="mt-3 text-sm leading-6 text-[#67717C]">{message}</p>
        <Link href={href} className="mt-8 inline-flex h-[48px] items-center justify-center rounded-[10px] bg-[#14243A] px-6 text-sm font-semibold text-white hover:bg-[#1f3757]">
          {action}
        </Link>
      </div>
    </main>
  );
}

/** 실제 결제 금액·결제수단·결과는 서버 Payment 및 토스페이먼츠 위젯을 기준으로 처리합니다. */
export default function PaymentPage() {
  const { user, loading: userLoading, unauthenticated, error: userError } = useRequireAuth('CLIENT');
  const searchParams = useSearchParams();
  const paymentId = Number(searchParams.get('paymentId'));
  const queryPostId = Number(searchParams.get('postId'));
  const hasPaymentId = Number.isSafeInteger(paymentId) && paymentId > 0;
  const isExtra = searchParams.get('flow') === 'extra';
  const pay = searchParams.get('pay') ?? '0';

  const [payment, setPayment] = useState<PaymentDto | null>(null);
  const [post, setPost] = useState<PostDto | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [widgets, setWidgets] = useState<TossPaymentsWidgets | null>(null);
  const [widgetError, setWidgetError] = useState<string | null>(null);
  const [ready, setReady] = useState(false);
  const [requesting, setRequesting] = useState(false);
  const [requestError, setRequestError] = useState<string | null>(null);
  const renderedRef = useRef<{ widgets: TossPaymentsWidgets; paymentId: number; promise: Promise<void> } | null>(null);

  useEffect(() => {
    if (!user || !hasPaymentId) return;
    let active = true;
    setLoadError(null);
    setPayment(null);
    setPost(null);
    setReady(false);

    fetchPayment(paymentId)
      .then(async (data) => {
        if (!active) return;
        if (data.paymentStatus !== 'READY') {
          setLoadError(data.paymentStatus === 'DONE' ? '이미 결제를 완료한 건입니다.' : '현재 결제할 수 없는 상태입니다.');
          return;
        }
        if (!Number.isFinite(data.amount) || data.amount <= 0) {
          setLoadError('결제 금액을 확인할 수 없습니다.');
          return;
        }
        if (data.postId != null && Number.isSafeInteger(queryPostId) && queryPostId > 0 && queryPostId !== data.postId) {
          setLoadError('공고 정보와 결제 정보가 일치하지 않습니다.');
          return;
        }
        const targetPostId = data.postId ?? queryPostId;
        if (!Number.isSafeInteger(targetPostId) || targetPostId <= 0) {
          setLoadError('결제에 연결된 공고를 확인할 수 없습니다.');
          return;
        }
        setPayment(data);
        try {
          const detail = await fetchPostRaw(targetPostId);
          if (active) setPost(detail);
        } catch {
          // 공고 상세 조회 실패가 실제 결제 승인까지 막지 않도록 결제 정보만 표시합니다.
        }
      })
      .catch((error: unknown) => {
        if (active) setLoadError(error instanceof Error ? error.message : '결제 정보를 불러오지 못했습니다.');
      });

    return () => { active = false; };
  }, [user, paymentId, queryPostId, hasPaymentId]);

  useEffect(() => {
    if (!user) return;
    let active = true;
    setWidgetError(null);

    if (!clientKey) {
      setWidgetError('토스페이먼츠 클라이언트 키가 설정되지 않았습니다.');
      return;
    }
    loadTossPayments(clientKey)
      .then((toss) => {
        if (active) setWidgets(toss.widgets({ customerKey: user.username }));
      })
      .catch((error: unknown) => {
        if (active) setWidgetError(error instanceof Error ? error.message : '결제 위젯을 불러오지 못했습니다.');
      });
    return () => { active = false; };
  }, [user]);

  useEffect(() => {
    if (!widgets || !payment) return;
    let active = true;
    setReady(false);
    setWidgetError(null);

    // 개발 모드의 이중 effect 실행에서도 동일한 결제 위젯을 중복 렌더하지 않습니다.
    let current = renderedRef.current;
    if (!current || current.widgets !== widgets || current.paymentId !== payment.id) {
      const promise = (async () => {
        await widgets.setAmount({ currency: 'KRW', value: payment.amount });
        await widgets.renderPaymentMethods({ selector: '#payment-method', variantKey: 'DEFAULT' });
        await widgets.renderAgreement({ selector: '#agreement', variantKey: 'AGREEMENT' });
      })();
      current = { widgets, paymentId: payment.id, promise };
      renderedRef.current = current;
    }
    current.promise
      .then(() => { if (active) setReady(true); })
      .catch((error: unknown) => {
        if (active) setWidgetError(error instanceof Error ? error.message : '결제 수단을 표시하지 못했습니다.');
      });
    return () => { active = false; };
  }, [widgets, payment]);

  const resolvedPostId = payment?.postId ?? queryPostId;
  const validPostId = Number.isSafeInteger(resolvedPostId) && resolvedPostId > 0;
  const backHref = isExtra && validPostId ? `/client/posts/${resolvedPostId}` : '/client/posts';
  const backLabel = isExtra ? '공고 상세로 돌아가기' : '내 공고 목록으로 돌아가기';

  if (userLoading) {
    return <Notice title="결제 정보 확인 중" message="로그인 정보를 확인하고 있습니다." href="/client/posts" action="내 공고 목록으로" />;
  }
  if (unauthenticated || !user) {
    return <Notice title="로그인이 필요합니다" message={userError ?? '결제를 진행하려면 의뢰인 계정으로 로그인해 주세요.'} href="/login" action="로그인하기" />;
  }
  if (!hasPaymentId || loadError) {
    return <Notice title="결제를 진행할 수 없습니다" message={loadError ?? '결제 정보(paymentId)가 없습니다.'} href={backHref} action={backLabel} />;
  }
  if (!payment) {
    return <Notice title="결제 금액 확인 중" message="서버에서 결제 금액을 불러오고 있습니다." href={backHref} action={backLabel} />;
  }

  const currency = formatWon(payment.amount);
  const redirect = new URLSearchParams({
    postId: String(resolvedPostId),
    paymentId: String(payment.id),
    pay,
    flow: isExtra ? 'extra' : '',
  });

  async function requestPayment() {
    if (!widgets || !ready || requesting || !user) return;
    setRequestError(null);
    setRequesting(true);
    try {
      await widgets.requestPayment({
        orderId: paymentOrderId(),
        orderName: isExtra ? '동행 추가 결제' : '동행 공고 결제',
        successUrl: `${window.location.origin}/client/posts/new/payment/success?${redirect}`,
        failUrl: `${window.location.origin}/client/posts/new/payment/fail?${redirect}`,
        customerEmail: user.email,
        customerName: user.name,
        customerMobilePhone: customerPhone(user.phoneNum),
      });
    } catch (error) {
      setRequestError(error instanceof Error ? error.message : '결제 요청을 진행하지 못했습니다.');
      setRequesting(false);
    }
  }

  return (
    <main className="w-full py-12 lg:py-16">
      <div className="mx-auto max-w-[1240px] px-5 sm:px-8 lg:px-16">
        <div className="mx-auto max-w-2xl space-y-8">
          <Link href={backHref} className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] hover:text-[#14243A]">
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            {backLabel}
          </Link>

          <div>
            <h1 className="text-3xl font-semibold tracking-tight text-[#14243A]">
              {isExtra ? '동행 추가 결제' : '동행 공고 결제'}
            </h1>
            <p className="mt-1 text-sm leading-6 text-[#67717C]">
              결제 금액을 확인하고 안전한 결제 수단을 선택해 주세요.
            </p>
          </div>

          <section className={`${CARD} space-y-5 p-6 sm:p-8`} aria-labelledby="payment-summary-heading">
            <h2 id="payment-summary-heading" className="border-b border-[#E1E5E8] pb-3 text-lg font-semibold text-[#14243A]">
              결제 내역 요약
            </h2>
            <div className="space-y-3 text-sm">
              {post && (
                <>
                  <div className="flex items-start justify-between gap-4">
                    <span className="shrink-0 text-[#67717C]">공고</span>
                    <span className="max-w-xs text-right font-semibold text-[#14243A]">{post.title}</span>
                  </div>
                  <div className="flex items-start justify-between gap-4">
                    <span className="shrink-0 text-[#67717C]">방문 병원</span>
                    <span className="text-right font-medium text-[#202832]">{post.hospitalName}</span>
                  </div>
                  <div className="flex items-start justify-between gap-4">
                    <span className="shrink-0 text-[#67717C]">동행 일시</span>
                    <span className="text-right font-medium text-[#202832]">{formatEscortDate(post.escortStartAt)}</span>
                  </div>
                </>
              )}
              <div className="flex justify-between gap-3">
                <span className="text-[#67717C]">결제 기준 시간</span>
                <span className="font-medium tabular-nums text-[#202832]">{payment.hours}시간</span>
              </div>
              <div className="flex justify-between gap-3">
                <span className="text-[#67717C]">시간당 시급</span>
                <span className="font-medium tabular-nums text-[#202832]">{formatWon(payment.hourlyPaySnapshot)}</span>
              </div>
              {isExtra && (
                <p className="rounded-xl bg-[#F7F8F8] px-4 py-3 text-xs leading-5 text-[#67717C]">
                  추가 결제 금액은 동행 완료 후 서버에서 계산한 차액을 기준으로 합니다.
                </p>
              )}
              <div className="flex items-baseline justify-between gap-4 border-t border-[#E1E5E8] pt-5">
                <span className="font-semibold text-[#14243A]">최종 결제 금액</span>
                <span className="text-3xl font-bold tabular-nums text-[#14243A]">
                  {payment.amount.toLocaleString('ko-KR')}<span className="ml-1 text-base font-normal text-[#67717C]">원</span>
                </span>
              </div>
              <p className="text-xs leading-5 text-[#67717C]">최종 결제 금액은 서버의 결제 내역을 기준으로 표시됩니다.</p>
            </div>
          </section>

          <section className={`${CARD} space-y-3 p-4 sm:p-6`} aria-labelledby="payment-method-heading">
            <div className="flex items-center gap-2 px-2 pb-2">
              <CreditCard className="h-4 w-4 text-[#426B93]" aria-hidden="true" />
              <h2 id="payment-method-heading" className="text-sm font-semibold text-[#14243A]">결제 수단 선택</h2>
            </div>
            <div id="payment-method" className="min-h-[180px]" />
            <div id="agreement" />
            {!ready && !widgetError && <p className="px-2 text-sm text-[#67717C]">결제 수단을 불러오고 있습니다.</p>}
            {widgetError && <p role="alert" className="px-2 text-sm text-[#B4413C]">{widgetError}</p>}
          </section>

          <div className="space-y-3">
            {requestError && <p role="alert" className="text-sm text-[#B4413C]">{requestError}</p>}
            <button
              type="button"
              disabled={!ready || requesting || !!widgetError}
              onClick={requestPayment}
              className="flex h-[52px] w-full items-center justify-center rounded-[10px] bg-[#14243A] text-base font-semibold text-white transition-colors hover:bg-[#1f3757] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {requesting ? '결제 요청 중...' : `${currency} 결제하기`}
            </button>
            <p className="flex items-center justify-center gap-1.5 text-center text-xs text-[#67717C]">
              <ShieldCheck className="h-4 w-4 text-[#426B93]" aria-hidden="true" />
              실제 결제는 토스페이먼츠에서 처리됩니다.
            </p>
          </div>
        </div>
      </div>
    </main>
  );
}
