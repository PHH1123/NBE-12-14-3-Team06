'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { ChevronLeft, Receipt, X } from 'lucide-react';
import { AppShell } from '@/components/layout';
import { useRequireAuth } from '@/features/auth';
import { fetchMyPayments, type PaymentDto, type PaymentStatus } from '@/features/payment';
import { fetchMyPostsRaw } from '@/features/post';

type PostLabel = { title: string; hospitalName: string; hours: number };
type State = { payments: PaymentDto[]; posts: Record<number, PostLabel> };

function paymentDateTimestamp(payment: PaymentDto): number {
  const date = payment.approvedAt ?? payment.canceledAt;
  if (!date) return Number.NEGATIVE_INFINITY;
  const timestamp = Date.parse(date);
  return Number.isNaN(timestamp) ? Number.NEGATIVE_INFINITY : timestamp;
}

const STATUS: Record<PaymentStatus, { label: string; className: string }> = {
  READY: { label: '미결제', className: 'border-[#9A6B12]/20 bg-[#9A6B12]/10 text-[#84601B]' },
  IN_PROGRESS: { label: '결제 진행 중', className: 'border-[#426B93]/20 bg-[#426B93]/10 text-[#426B93]' },
  DONE: { label: '결제 완료', className: 'border-[#2F7D5B]/20 bg-[#2F7D5B]/10 text-[#2F7D5B]' },
  CANCELED: { label: '결제 취소', className: 'border-[#B4413C]/20 bg-[#B4413C]/10 text-[#B4413C]' },
  PARTIAL_CANCELED: { label: '부분 취소', className: 'border-[#426B93]/20 bg-[#426B93]/10 text-[#426B93]' },
  DELETED: { label: '공고 삭제', className: 'border-[#E1E5E8] bg-[#EDF0F2] text-[#67717C]' },
};

function StatusBadge({ status }: { status: PaymentStatus }) {
  const style = STATUS[status] ?? STATUS.READY;
  return (
    <span className={`inline-flex whitespace-nowrap rounded border px-2 py-0.5 text-xs font-semibold ${style.className}`}>
      {style.label}
    </span>
  );
}

function formatAt(value: string | null): string {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

function Money({ amount }: { amount: number }) {
  return <>{amount.toLocaleString('ko-KR')}원</>;
}

function DetailDialog({ payment, post, onClose }: { payment: PaymentDto; post?: PostLabel; onClose: () => void }) {
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    closeRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-[#262626]/45 p-4"
      onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="payment-detail-title"
        className="w-full max-w-lg space-y-6 rounded-2xl bg-white p-6 shadow-xl sm:p-8"
      >
        <div className="flex items-center justify-between border-b border-[#E1E5E8] pb-4">
          <h2 id="payment-detail-title" className="flex items-center gap-2 text-xl font-semibold text-[#14243A]">
            <Receipt className="h-5 w-5 text-[#426B93]" aria-hidden="true" />
            결제 상세
          </h2>
          <button ref={closeRef} type="button" aria-label="상세 닫기" onClick={onClose} className="rounded-lg p-1.5 text-[#67717C] hover:bg-[#F7F8F8] hover:text-[#14243A]">
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="flex items-center justify-between rounded-xl border border-[#E1E5E8] bg-[#F7F8F8] p-4 text-xs">
          <div className="space-y-1.5">
            <span className="block text-[#67717C]">결제 상태</span>
            <StatusBadge status={payment.paymentStatus} />
          </div>
          <div className="text-right">
            <span className="block text-[#67717C]">결제 번호</span>
            <span className="mt-1 block font-semibold text-[#14243A]">#{payment.id}</span>
          </div>
        </div>

        <dl className="space-y-3 text-sm">
          <div className="flex justify-between gap-4"><dt className="shrink-0 text-[#67717C]">공고명</dt><dd className="text-right font-semibold text-[#14243A]">{post?.title ?? (payment.postId != null ? `공고 #${payment.postId}` : '연결된 공고 없음')}</dd></div>
          {post && <div className="flex justify-between gap-4"><dt className="text-[#67717C]">방문 병원</dt><dd className="text-right text-[#14243A]">{post.hospitalName}</dd></div>}
          <div className="flex justify-between gap-4"><dt className="text-[#67717C]">결제 기준</dt><dd className="text-right text-[#14243A]">{payment.hours}시간 · 시급 <Money amount={payment.hourlyPaySnapshot} /></dd></div>
          {payment.orderId && <div className="flex justify-between gap-4"><dt className="shrink-0 text-[#67717C]">주문 번호</dt><dd className="min-w-0 break-all text-right text-[#14243A]">{payment.orderId}</dd></div>}
          {payment.approvedAt && <div className="flex justify-between gap-4"><dt className="text-[#67717C]">결제 승인일</dt><dd className="text-right text-[#14243A]">{formatAt(payment.approvedAt)}</dd></div>}
          {payment.canceledAt && <div className="flex justify-between gap-4"><dt className="text-[#67717C]">취소일</dt><dd className="text-right text-[#14243A]">{formatAt(payment.canceledAt)}</dd></div>}
          {payment.cancelReason && <div className="flex justify-between gap-4"><dt className="shrink-0 text-[#67717C]">취소 사유</dt><dd className="text-right text-[#14243A]">{payment.cancelReason}</dd></div>}
          <div className="flex items-baseline justify-between gap-4 border-t border-[#E1E5E8] pt-4"><dt className="font-semibold text-[#14243A]">결제 금액</dt><dd className="text-2xl font-bold tabular-nums text-[#14243A]"><Money amount={payment.amount} /></dd></div>
        </dl>
        <p className="text-xs leading-5 text-[#67717C]">
          <span className="block">공식 영수증 발급 링크는 현재 결제 조회 응답에 제공되지 않습니다.</span>
          <span className="block">결제 취소는 해당 공고의 상세 화면에서 진행할 수 있습니다.</span>
        </p>
        {payment.postId != null && (
          <div className="flex justify-end">
            <Link
              href={`/client/posts/${payment.postId}`}
              onClick={onClose}
              className="flex h-11 items-center justify-center rounded-xl border border-[#E1E5E8] px-5 text-xs font-semibold text-[#14243A] transition-colors hover:bg-[#F7F8F8]"
            >
              공고 상세 보기
            </Link>
          </div>
        )}
      </section>
    </div>
  );
}

export default function ClientPaymentHistoryPage() {
  const { user, loading } = useRequireAuth('CLIENT');
  const [data, setData] = useState<State>();
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<PaymentDto | null>(null);
  const [reload, setReload] = useState(0);
  const closeDetail = useCallback(() => setSelected(null), []);

  useEffect(() => {
    if (loading || user?.role !== 'CLIENT') return;
    let cancelled = false;
    setData(undefined);
    setError(undefined);
    const load = async () => {
      try {
        const payments = await fetchMyPayments();
        // 공고 정보 조회가 실패해도 결제 내역은 그대로 표시합니다.
        let posts: Record<number, PostLabel> = {};
        try {
          const first = await fetchMyPostsRaw(0, 100);
          const pages = await Promise.all(
            Array.from({ length: Math.max(first.totalPages - 1, 0) }, (_, i) => fetchMyPostsRaw(i + 1, 100)),
          );
          for (const post of [...first.posts, ...pages.flatMap((page) => page.posts)]) {
            posts[post.id] = { title: post.title, hospitalName: post.hospitalName, hours: post.hours };
          }
        } catch {
          posts = {};
        }
        if (!cancelled) setData({
          payments: [...payments].sort((a, b) => paymentDateTimestamp(b) - paymentDateTimestamp(a) || b.id - a.id),
          posts,
        });
      } catch (cause: unknown) {
        if (!cancelled) setError(cause instanceof Error ? cause.message : '결제 내역을 불러오지 못했습니다.');
      }
    };
    void load();
    return () => { cancelled = true; };
  }, [loading, user?.role, reload]);

  return (
    <AppShell>
      <div className="min-h-[calc(100vh-72px)] bg-[#F7F8F8] py-12 lg:py-16">
        <div className="mx-auto max-w-[1240px] space-y-8 px-5 sm:px-8 lg:px-16">
          <nav aria-label="돌아가기" className="flex flex-wrap items-center gap-x-5 gap-y-2">
            <Link href="/client" className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] transition-colors hover:text-[#426B93]">
              <ChevronLeft className="h-4 w-4" aria-hidden="true" /> 마이페이지로 돌아가기
            </Link>
          </nav>
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <h1 className="text-3xl font-semibold tracking-tight text-[#14243A]">결제 내역</h1>
              <p className="mt-1 text-sm text-[#67717C]">동행 서비스 결제 내역과 결제 상태를 확인합니다.</p>
            </div>
            <button type="button" onClick={() => setReload((value) => value + 1)} className="rounded-lg border border-[#E1E5E8] bg-white px-4 py-2.5 text-xs font-semibold text-[#14243A] hover:bg-[#EDF0F2]">새로고침</button>
          </div>
          <div className="overflow-hidden rounded-2xl border border-[#E1E5E8] bg-white shadow-sm">
            <div className="hidden grid-cols-12 gap-4 border-b border-[#E1E5E8] bg-[#EDF0F2]/60 px-6 py-3.5 text-xs font-semibold text-[#67717C] min-[1100px]:grid">
              <span className="col-span-2">결제일</span>
              <span className="col-span-5">공고</span>
              <span className="col-span-2 text-right">결제 금액</span>
              <span className="col-span-2 text-center">상태</span>
              <span className="col-span-1 text-center">상세</span>
            </div>
            {loading || (!data && !error) ? (
              <div className="px-6 py-16 text-center text-sm text-[#67717C]">결제 내역을 불러오는 중입니다.</div>
            ) : error ? (
              <div role="alert" className="space-y-3 px-6 py-16 text-center text-sm text-[#B4413C]">
                <p>{error}</p>
                <button type="button" onClick={() => setReload((value) => value + 1)} className="rounded-lg border border-[#E1E5E8] bg-white px-4 py-2 text-[#14243A]">다시 시도</button>
              </div>
            ) : data && data.payments.length === 0 ? (
              <div className="px-6 py-16 text-center text-sm text-[#67717C]">결제 내역이 없습니다.</div>
            ) : (
              <div className="divide-y divide-[#E1E5E8]">
                {data?.payments.map((payment) => {
                  const post = payment.postId != null ? data?.posts[payment.postId] : undefined;
                  const title = post?.title ?? (payment.postId != null ? `공고 #${payment.postId}` : '연결된 공고 없음');
                  const dateLabel = payment.approvedAt ?? payment.canceledAt;
                  return (
                    <div key={payment.id} className="group px-6 py-5 transition-colors hover:bg-[#F7F8F8]">
                      <div className="hidden grid-cols-12 items-center gap-4 text-sm min-[1100px]:grid">
                        <div className="col-span-2 text-xs font-medium tabular-nums text-[#67717C]">{formatAt(dateLabel)}</div>
                        <div className="col-span-5 pr-2">
                          <p className="font-semibold text-[#14243A]">{title}</p>
                          <p className="mt-1 text-xs text-[#67717C]">{post ? `${post.hospitalName} · ` : ''}{payment.hours}시간 · 결제 #{payment.id}</p>
                        </div>
                        <div className="col-span-2 text-right text-base font-bold tabular-nums text-[#14243A]"><Money amount={payment.amount} /></div>
                        <div className="col-span-2 text-center"><StatusBadge status={payment.paymentStatus} /></div>
                        <div className="col-span-1 text-center">
                          <button type="button" onClick={() => setSelected(payment)} className="h-9 rounded-lg border border-[#E1E5E8] bg-white px-3 text-xs font-semibold text-[#14243A] hover:bg-[#EDF0F2]">상세</button>
                        </div>
                      </div>
                      <div className="space-y-2 min-[1100px]:hidden">
                        <div className="flex items-center justify-between gap-2 text-xs text-[#67717C]">
                          <span className="tabular-nums">{formatAt(dateLabel)}</span>
                          <StatusBadge status={payment.paymentStatus} />
                        </div>
                        <h2 className="font-semibold text-[#14243A]">{title}</h2>
                        <p className="text-xs text-[#67717C]">{post?.hospitalName ?? `결제 #${payment.id}`} · {payment.hours}시간</p>
                        <div className="flex items-center justify-between gap-4 pt-2">
                          <span className="font-bold tabular-nums text-[#14243A]"><Money amount={payment.amount} /></span>
                          <button type="button" onClick={() => setSelected(payment)} className="h-9 rounded-lg border border-[#E1E5E8] px-3 text-xs font-semibold text-[#14243A]">상세 보기</button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>
      {selected && <DetailDialog payment={selected} post={selected.postId != null ? data?.posts[selected.postId] : undefined} onClose={closeDetail} />}
    </AppShell>
  );
}
