'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type MouseEvent } from 'react';
import { Activity, ChevronLeft, FileText, PlusCircle, Receipt, Search, Star, Users, X } from 'lucide-react';
import { AppShell } from '@/components/layout';
import DropdownSelect from '@/components/ui/DropdownSelect';
import { useRequireAuth } from '@/features/auth';
import { fetchEscortProfile } from '@/features/application';
import { fetchUserReviews } from '@/features/review';
import { fetchMyPayments, usePostCreationEligibility, postCreationUnavailableReason, type PaymentDto, type PaymentStatus } from '@/features/payment';
import { cn } from '@/lib/cn';
import { fetchMyPosts } from '../api';
import { SORT_OPTIONS, filterClientPosts, type ClientPostSort } from '../model/posts';
import type { ClientPost, ClientPostStatus } from '../types';

// 화면의 상태 탭은 디자인의 다섯 단계를 유지합니다. 취소·만료된 공고는 '전체'에 표시합니다.
type Tab = 'all' | 'open' | 'matched' | 'inProgress' | 'completed';
const STATUS_TABS: { id: Tab; label: string }[] = [
  { id: 'all', label: '전체' },
  { id: 'open', label: '모집 중' },
  { id: 'matched', label: '매칭 완료' },
  { id: 'inProgress', label: '진행 중' },
  { id: 'completed', label: '동행 완료' },
];

/** Keep the existing tab dimensions and colors; only slide the selected white highlight. */
function SlidingStatusTabs({ value, onChange }: { value: Tab; onChange: (next: Tab) => void }) {
  const groupRef = useRef<HTMLDivElement>(null);
  const buttonRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const [indicator, setIndicator] = useState({ left: 0, width: 0 });
  const selectedIndex = STATUS_TABS.findIndex((tab) => tab.id === value);

  useLayoutEffect(() => {
    const measure = () => {
      const button = buttonRefs.current[selectedIndex];
      if (!button) return;
      const next = { left: button.offsetLeft, width: button.offsetWidth };
      setIndicator((previous) =>
        previous.left === next.left && previous.width === next.width ? previous : next,
      );
    };
    measure();
    const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(measure) : null;
    if (groupRef.current) observer?.observe(groupRef.current);
    return () => observer?.disconnect();
  }, [selectedIndex]);

  return (
    <div
      ref={groupRef}
      role="tablist"
      aria-label="공고 상태"
      className="relative inline-flex max-w-full gap-1 overflow-x-auto rounded-xl border border-[#E1E5E8] bg-[#EDF0F2] p-1"
    >
      <span
        aria-hidden="true"
        className="pointer-events-none absolute top-1 bottom-1 rounded-lg bg-white shadow-xs transition-[left,width] duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] motion-reduce:transition-none"
        style={{ left: indicator.left, width: indicator.width, opacity: indicator.width ? 1 : 0 }}
      />
      {STATUS_TABS.map((tab, index) => (
        <button
          ref={(element) => { buttonRefs.current[index] = element; }}
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={value === tab.id}
          onClick={() => onChange(tab.id)}
          className={cn(
            'relative z-10 whitespace-nowrap rounded-lg px-3.5 py-1.5 text-sm font-semibold transition-colors',
            value === tab.id ? 'text-[#14243A]' : 'text-[#67717C] hover:text-[#14243A]',
          )}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}

const PAGE_SIZE = 10;
const STATUS_STYLES: Record<ClientPostStatus, string> = {
  open: 'border-[#426B93]/35 bg-[#E5EEF7] text-[#305879]',
  matched: 'border-[#2F7D5B]/35 bg-[#E5F1EA] text-[#286849]',
  inProgress: 'border-[#14243A]/30 bg-[#E5E9F0] text-[#14243A]',
  completed: 'border-[#5E697A]/30 bg-[#E9EDF1] text-[#344055]',
  canceled: 'border-[#B4413C]/25 bg-[#F7ECEB] text-[#99433E]',
  expired: 'border-[#9A6B12]/30 bg-[#F7F0DE] text-[#84601B]',
};
const STATUS_DOTS: Record<ClientPostStatus, string> = {
  open: 'bg-[#426B93]',
  matched: 'bg-[#2F7D5B]',
  inProgress: 'bg-[#14243A]',
  completed: 'bg-[#67717C]',
  canceled: 'bg-[#B4413C]',
  expired: 'bg-[#9A6B12]',
};
const STATUS_TEXT: Record<ClientPostStatus, string> = {
  open: '모집 중',
  matched: '매칭 완료',
  inProgress: '진행 중',
  completed: '동행 완료',
  canceled: '취소됨',
  expired: '모집 마감',
};

function StatusBadge({ status, unpaid }: { status: ClientPostStatus; unpaid?: boolean }) {
  const needsPayment = unpaid && status !== 'completed';
  const needsExtra = unpaid && status === 'completed';
  const tone = needsPayment
    ? 'border-[#B4413C]/30 bg-[#F7ECEB] text-[#99433E]'
    : needsExtra
      ? 'border-[#9A6B12]/30 bg-[#F7F0DE] text-[#84601B]'
      : STATUS_STYLES[status];
  const dot = needsPayment ? 'bg-[#B4413C]' : needsExtra ? 'bg-[#9A6B12]' : STATUS_DOTS[status];
  return (
    <span className={cn('inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-md border px-2.5 py-1 text-xs font-bold', tone)}>
      <span aria-hidden="true" className={cn('size-1.5 shrink-0 rounded-full', dot)} />
      {needsPayment ? '미결제' : needsExtra ? '추가 결제' : STATUS_TEXT[status]}
    </span>
  );
}

function paymentHref(post: ClientPost) {
  if (!post.unpaid) return '';
  const query = new URLSearchParams({
    postId: String(post.id),
    paymentId: String(post.unpaid.paymentId),
    pay: String(post.unpaid.hourlyPay),
    ...(post.status === 'completed' ? { flow: 'extra' } : {}),
  });
  return `/client/posts/new/payment?${query.toString()}`;
}

const ACTION_SIZE = 'inline-flex h-9 w-[104px] shrink-0 items-center justify-center gap-1.5 whitespace-nowrap rounded-lg px-3 text-xs font-semibold transition-colors';
const PRIMARY = `${ACTION_SIZE} bg-[#14243A] text-white hover:bg-[#1f3757]`;
const BLUE = `${ACTION_SIZE} bg-[#426B93] text-white hover:bg-[#345778]`;
const SECONDARY = `${ACTION_SIZE} border border-[#E1E5E8] bg-white text-[#14243A] hover:bg-[#F7F8F8]`;

/** 현재 노출된 완료 공고만 리뷰 작성 여부를 조회합니다. 확인 전에는 작성 가능으로 추정하지 않습니다. */
function ReviewAction({ applicationId, postId }: { applicationId: number; postId: number }) {
  const [state, setState] = useState<'loading' | 'written' | 'unwritten' | 'error'>('loading');
  useEffect(() => {
    let canceled = false;
    setState('loading');
    fetchEscortProfile(applicationId)
      .then((profile) => fetchUserReviews(profile.escortId))
      .then((reviews) => {
        if (!canceled) setState(reviews.some((review) => review.applicationId === applicationId) ? 'written' : 'unwritten');
      })
      .catch(() => { if (!canceled) setState('error'); });
    return () => { canceled = true; };
  }, [applicationId]);

  if (state === 'loading') {
    return <span className={`${SECONDARY} cursor-wait opacity-60`} aria-live="polite" aria-label="리뷰 작성 여부 확인 중"><Star className="size-3.5 text-[#9A6B12]" aria-hidden="true" />확인 중</span>;
  }
  return (
    <Link href={`/client/escort/${applicationId}/review?postId=${postId}`} onClick={(event) => event.stopPropagation()} className={SECONDARY}>
      <Star className="size-3.5 text-[#9A6B12]" aria-hidden="true" />
      {state === 'written' ? '리뷰 보기' : state === 'unwritten' ? '리뷰 쓰기' : '리뷰 확인'}
    </Link>
  );
}

/** 목록 항목의 실제 이동 주소. 각 버튼은 클릭 이벤트를 전파시키지 않습니다. */
function RowActions({ post }: { post: ClientPost }) {
  const stop = (event: MouseEvent<HTMLAnchorElement>) => event.stopPropagation();
  if (post.unpaid) {
    return (
      <Link href={paymentHref(post)} onClick={stop} className={PRIMARY}>
        {post.status === 'completed' ? '추가 결제' : '결제하기'}
      </Link>
    );
  }
  if (post.status === 'open') {
    return (
      <Link href={`/client/posts/${post.id}/applicants`} onClick={stop} className={BLUE}>
        <Users className="size-3.5" aria-hidden="true" />
        지원자 확인
      </Link>
    );
  }
  if (post.status === 'matched' || post.status === 'inProgress') {
    return post.applicationId ? (
      <Link
        href={`/client/escort/${post.applicationId}?postId=${post.id}`}
        onClick={stop}
        className={PRIMARY}
      >
        <Activity className="size-3.5 text-[#78B99A]" aria-hidden="true" />
        동행 현황
      </Link>
    ) : (
      <Link href={`/client/posts/${post.id}`} onClick={stop} className={SECONDARY}>공고 상세</Link>
    );
  }
  if (post.status === 'completed') {
    return post.applicationId ? (
      <div className="flex flex-col items-center gap-2">
        <Link
          href={`/client/escort/${post.applicationId}/report?postId=${post.id}`}
          onClick={stop}
          className={PRIMARY}
        >
          <FileText className="size-3.5" aria-hidden="true" />
          보고서 보기
        </Link>
        <ReviewAction applicationId={post.applicationId} postId={post.id} />
      </div>
    ) : (
      <Link href={`/client/posts/${post.id}`} onClick={stop} className={SECONDARY}>공고 상세</Link>
    );
  }
  return <span className="text-xs text-[#67717C]">{STATUS_TEXT[post.status]} 공고</span>;
}


const PAYMENT_STATUS: Record<PaymentStatus, string> = {
  READY: '미결제',
  IN_PROGRESS: '결제 진행 중',
  DONE: '결제 완료',
  CANCELED: '결제 취소',
  PARTIAL_CANCELED: '부분 취소',
  DELETED: '공고 삭제',
};

/** 전체 결제 내역은 결제 목록 API에서 조회하며, 상세·취소는 해당 공고에서 진행합니다. */
function PaymentHistoryDialog({ onClose }: { onClose: () => void }) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const [payments, setPayments] = useState<PaymentDto[]>();
  const [error, setError] = useState<string>();
  useEffect(() => {
    closeButtonRef.current?.focus();
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleEscape);
    return () => document.removeEventListener('keydown', handleEscape);
  }, [onClose]);

  useEffect(() => {
    let ignore = false;
    fetchMyPayments()
      .then((records) => { if (!ignore) setPayments([...records].sort((a, b) => b.id - a.id)); })
      .catch((cause: unknown) => {
        if (!ignore) setError(cause instanceof Error ? cause.message : '결제 내역을 불러오지 못했습니다.');
      });
    return () => { ignore = true; };
  }, []);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-[#14243A]/55 p-4"
      onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}
      onKeyDown={(event) => { if (event.key === 'Escape') onClose(); }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="my-payments-title"
        className="flex max-h-[85vh] w-full max-w-[700px] flex-col overflow-hidden rounded-2xl border border-[#E1E5E8] bg-white shadow-xl"
      >
        <div className="flex items-center justify-between border-b border-[#E1E5E8] px-6 py-5">
          <h2 id="my-payments-title" className="flex items-center gap-2 text-xl font-semibold text-[#14243A]">
            <Receipt aria-hidden="true" className="size-5 text-[#426B93]" />
            결제 내역
          </h2>
          <button ref={closeButtonRef} type="button" aria-label="결제 내역 닫기" onClick={onClose} className="rounded-lg p-2 text-[#67717C] transition-colors hover:bg-[#EDF0F2]">
            <X className="size-5" aria-hidden="true" />
          </button>
        </div>
        <div className="overflow-y-auto px-6 py-4">
          {!payments && !error && <p className="py-12 text-center text-sm text-[#67717C]">결제 내역을 불러오는 중입니다.</p>}
          {error && <p role="alert" className="py-12 text-center text-sm text-[#B4413C]">{error}</p>}
          {payments?.length === 0 && <p className="py-12 text-center text-sm text-[#67717C]">결제 내역이 없습니다.</p>}
          {payments && payments.length > 0 && (
            <ul className="divide-y divide-[#E1E5E8]">
              {payments.map((payment) => (
                <li key={payment.id} className="flex flex-wrap items-center justify-between gap-3 py-4 text-sm">
                  <div className="min-w-0">
                    <p className="font-semibold text-[#14243A]">공고 #{payment.postId ?? '-'} · {payment.amount.toLocaleString()}원</p>
                    <p className="mt-1 text-xs text-[#67717C]">
                      {PAYMENT_STATUS[payment.paymentStatus]}
                      {payment.approvedAt && ` · 승인: ${payment.approvedAt.replace('T', ' ').slice(0, 16)}`}
                    </p>
                  </div>
                  {payment.postId != null && (
                    <Link href={`/client/posts/${payment.postId}`} onClick={onClose} className={SECONDARY}>
                      공고 상세 보기
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </div>
  );
}

/**
 * 의뢰인이 작성한 공고 목록. 데이터는 /api/v1/posts/me와 결제·지원 API에서 가져옵니다.
 * 기존 검색·정렬·결제 이동 기능을 유지하고, 표시 항목은 실제 응답에 있는 값만 사용합니다.
 */
export default function ClientPostsPage() {
  const router = useRouter();
  const { loading: authLoading, user } = useRequireAuth('CLIENT');
  const creationAvailability = usePostCreationEligibility(user?.role === 'CLIENT');
  const [status, setStatus] = useState<Tab>('all');
  const [showPayments, setShowPayments] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [sort, setSort] = useState<ClientPostSort>('latest');
  const [page, setPage] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [result, setResult] = useState<{ posts?: ClientPost[]; error?: string }>({});

  useEffect(() => {
    if (authLoading || !user) return;
    let ignore = false;
    fetchMyPosts()
      .then((posts) => { if (!ignore) setResult({ posts }); })
      .catch((error: unknown) => {
        if (!ignore) setResult({ error: error instanceof Error ? error.message : '공고 목록을 불러오지 못했습니다.' });
      });
    return () => { ignore = true; };
  }, [authLoading, user, reloadKey]);

  if (authLoading) {
    return <AppShell><div className="py-24 text-center text-[#67717C]">불러오는 중입니다.</div></AppShell>;
  }
  if (!user) return null;

  const filteredPosts = filterClientPosts(result.posts ?? [], status, keyword, sort);
  const totalPages = Math.max(1, Math.ceil(filteredPosts.length / PAGE_SIZE));
  const visiblePosts = filteredPosts.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const goToDetail = (postId: number) => router.push(`/client/posts/${postId}`);
  const onRowKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>, postId: number) => {
    if (event.target !== event.currentTarget) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      goToDetail(postId);
    }
  };

  return (
    <AppShell>
      <div className="w-full bg-[#F7F8F8] py-12 lg:py-16">
        <div className="mx-auto max-w-[1240px] space-y-8 px-5 sm:px-8 lg:px-16">
          {/* Header */}
          <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
            <div>
              <Link href="/client" className="mb-5 inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] transition-colors hover:text-[#426B93]">
                <ChevronLeft className="h-4 w-4" aria-hidden="true" /> 마이페이지로 돌아가기
              </Link>
              <h1 className="text-3xl font-semibold tracking-tight text-[#14243A] sm:text-4xl">작성한 공고</h1>
              <p className="mt-1 text-sm text-[#67717C]">내가 등록한 동행 의뢰의 매니저 지원 현황과 실시간 진행 상태를 확인하세요.</p>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={() => setShowPayments(true)}
                className="h-[44px] rounded-[10px] border border-[#E1E5E8] bg-white px-4 text-xs font-semibold text-[#14243A] transition-colors hover:bg-[#F7F8F8]"
              >
                결제 내역 보기
              </button>
              {creationAvailability === 'allowed' ? (
                <Link href="/client/posts/new" className="flex h-[44px] items-center justify-center gap-1.5 rounded-[10px] bg-[#14243A] px-5 text-xs font-semibold text-white transition-colors hover:bg-[#1f3757]">
                  <PlusCircle className="size-4" aria-hidden="true" />새 공고 작성
                </Link>
              ) : (
                <span aria-disabled="true" title={postCreationUnavailableReason(creationAvailability)} className="flex h-[44px] cursor-not-allowed items-center justify-center gap-1.5 rounded-[10px] bg-[#14243A] px-5 text-xs font-semibold text-white opacity-50">
                  <PlusCircle className="size-4" aria-hidden="true" />새 공고 작성
                </span>
              )}
            </div>
          </div>

          {creationAvailability !== 'allowed' && <p role="status" className="text-xs font-medium text-[#B4413C]">{postCreationUnavailableReason(creationAvailability)}</p>}

          {/* Status Tabs */}
          <div className="flex flex-wrap items-center justify-between gap-4">
            <SlidingStatusTabs value={status} onChange={(next) => { setStatus(next); setPage(0); }} />
            <div className="text-sm text-[#67717C]">
              총 <span className="font-semibold tabular-nums text-[#14243A]">{filteredPosts.length}</span>건
            </div>
          </div>

          {/* 기존 가지의 검색·정렬 기능. 레이아웃은 원본 목록 영역을 유지합니다. */}
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-end">
            <label className="flex h-[46px] w-full items-center gap-2.5 rounded-lg border border-[#E1E5E8] bg-white px-3.5 sm:max-w-[310px]">
              <Search className="size-4 shrink-0 text-[#8793A0]" aria-hidden="true" />
              <span className="sr-only">공고 검색</span>
              <input
                type="search"
                value={keyword}
                onChange={(event) => { setKeyword(event.target.value); setPage(0); }}
                placeholder="공고 제목, 병원명, 지역 검색"
                className="min-w-0 flex-1 bg-transparent text-sm text-[#14243A] outline-none placeholder:text-[#98A1AD]"
              />
            </label>
            <DropdownSelect
              id="client-posts-sort"
              label="정렬"
              value={sort}
              options={SORT_OPTIONS.map((option) => ({ value: option.value, label: option.label }))}
              onValueChange={(value) => { setSort(value as ClientPostSort); setPage(0); }}
              size="filter"
              className="w-full sm:w-[145px]"
            />
          </div>

          {/* List Container */}
          <div className="overflow-hidden rounded-2xl border border-[#E1E5E8] bg-white shadow-xs">
            {/* Desktop Table Header */}
            <div className="hidden grid-cols-12 gap-4 border-b border-[#E1E5E8] bg-[#EDF0F2]/60 px-6 py-3.5 text-xs font-semibold text-[#67717C] min-[1100px]:grid">
              <div className="col-span-1">상태</div>
              <div className="col-span-5 pl-2">공고 · 병원</div>
              <div className="col-span-2">동행 일정</div>
              <div className="col-span-1">소요</div>
              <div className="col-span-1 text-right">시급</div>
              <div className="col-span-2 text-center">진행 액션</div>
            </div>

            {!result.posts && !result.error ? (
              <div className="py-20 text-center text-sm text-[#67717C]">공고를 불러오는 중입니다.</div>
            ) : result.error ? (
              <div role="alert" className="space-y-3 py-20 text-center text-sm text-[#67717C]">
                <p>공고 목록을 불러오지 못했습니다. ({result.error})</p>
                <button
                  type="button"
                  onClick={() => { setResult({}); setReloadKey((key) => key + 1); }}
                  className={SECONDARY}
                >
                  다시 시도
                </button>
              </div>
            ) : visiblePosts.length === 0 ? (
              <div className="space-y-2 py-20 text-center text-[#67717C]">
                <p className="text-base font-medium text-[#14243A]">{keyword || status !== 'all' ? '조건에 맞는 공고가 없습니다.' : '등록된 공고가 없습니다.'}</p>
                <p className="text-sm">{keyword || status !== 'all' ? '다른 조건으로 찾아보세요.' : '새로운 병원 동행 공고를 등록해 보세요.'}</p>
              </div>
            ) : (
              <div className="divide-y divide-[#E1E5E8]">
                {visiblePosts.map((post) => (
                  <div
                    key={post.id}
                    role="link"
                    tabIndex={0}
                    aria-label={`${post.title} 공고 상세 보기`}
                    onClick={() => goToDetail(post.id)}
                    onKeyDown={(event) => onRowKeyDown(event, post.id)}
                    className="group cursor-pointer px-6 py-5 transition-colors hover:bg-[#F7F8F8] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[#426B93]"
                  >
                    {/* Desktop Layout (>= 1100px) */}
                    <div className="hidden grid-cols-12 items-center gap-4 min-[1100px]:grid">
                      <div className="col-span-1"><StatusBadge status={post.status} unpaid={!!post.unpaid} /></div>
                      <div className="col-span-5 min-w-0 pl-2 pr-2">
                        <div className="truncate text-base font-semibold text-[#14243A] transition-colors group-hover:text-[#426B93]">{post.title}</div>
                        <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-[#67717C]">
                          <span className="font-medium text-[#202832]">{post.hospitalName}</span>
                          {post.location.trim() && <><span>·</span><span>{post.location}</span></>}
                          {post.managerName && <><span>·</span><span>{post.managerName}</span></>}
                        </div>
                      </div>
                      <div className="col-span-2 text-sm tabular-nums text-[#202832]">
                        {post.dateLabel}<br />{post.timeLabel}
                      </div>
                      <div className="col-span-1 text-sm tabular-nums text-[#67717C]">{post.durationLabel}</div>
                      <div className="col-span-1 text-right font-bold tabular-nums text-[#14243A]">{post.payLabel.replace(/^시급\s*/, '')}</div>
                      <div className="col-span-2 flex items-center justify-center"><RowActions post={post} /></div>
                    </div>

                    {/* Mobile Layout (< 1100px) */}
                    <div className="space-y-3 min-[1100px]:hidden">
                      <div className="flex items-center justify-between gap-3">
                        <StatusBadge status={post.status} unpaid={!!post.unpaid} />
                        <span className="text-xs tabular-nums text-[#67717C]">{post.dateLabel} {post.timeLabel}</span>
                      </div>
                      <h3 className="font-semibold text-base text-[#14243A] transition-colors group-hover:text-[#426B93]">{post.title}</h3>
                      <p className="text-xs text-[#67717C]">{post.hospitalName} · {post.location} · {post.payLabel}</p>
                      <div className="flex justify-end border-t border-[#E1E5E8] pt-2"><RowActions post={post} /></div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {totalPages > 1 && (
            <nav aria-label="공고 목록 페이지" className="flex items-center justify-center gap-3">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((value) => Math.max(0, value - 1))}
                className={cn(SECONDARY, 'disabled:cursor-not-allowed disabled:opacity-40')}
              >
                이전
              </button>
              <span className="text-sm tabular-nums text-[#67717C]">{page + 1} / {totalPages}</span>
              <button
                type="button"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((value) => Math.min(totalPages - 1, value + 1))}
                className={cn(SECONDARY, 'disabled:cursor-not-allowed disabled:opacity-40')}
              >
                다음
              </button>
            </nav>
          )}
        </div>
      </div>
      {showPayments && <PaymentHistoryDialog onClose={() => setShowPayments(false)} />}
    </AppShell>
  );
}
