'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { applyToPost, fetchMyApplications } from '@/features/application';
import { useCurrentUser } from '@/features/auth';
import { usePostCreationEligibility, postCreationUnavailableReason } from '@/features/payment';
import { isEducationRequiredError } from '@/features/education/api';
import { fetchPosts, type PostPage } from '../api';
import { daysFromNow, formatMonthDay } from '../lib/date';
import { DEFAULT_FILTERS, PAY_OPTIONS, PERIOD_OPTIONS, optionLabel } from '../model';
import { postStatusLabel, toPostStatusKey } from '../model/status';
import type { PostFilters, PostSummary } from '../types';
import PostGuestNotice from './PostGuestNotice';
import { useActionDialog } from '@/components/ui/useActionDialog';
import PostSearchPanel from './PostSearchPanel';

const PAGE_SIZE = 8;
const STATUS_TABS = [
  { openOnly: true, label: '모집 중' },
  { openOnly: false, label: '모집 종료' },
] as const;

type Props = { detailBasePath?: string };
type Role = 'ADMIN' | 'CLIENT' | 'ESCORT';

function StatusBadge({ status }: { status: string }) {
  const key = toPostStatusKey(status);
  const label = postStatusLabel(status).text;
  const tone = {
    open: 'border-[#426B93]/35 bg-[#E5EEF7] text-[#305879]',
    matched: 'border-[#2F7D5B]/35 bg-[#E5F1EA] text-[#286849]',
    inProgress: 'border-[#14243A]/30 bg-[#E5E9F0] text-[#14243A]',
    completed: 'border-[#5E697A]/30 bg-[#E9EDF1] text-[#344055]',
    canceled: 'border-[#B4413C]/25 bg-[#F7ECEB] text-[#99433E]',
    expired: 'border-[#9A6B12]/30 bg-[#F7F0DE] text-[#84601B]',
  }[key];
  const dot = {
    open: 'bg-[#426B93]', matched: 'bg-[#2F7D5B]', inProgress: 'bg-[#14243A]',
    completed: 'bg-[#67717C]', canceled: 'bg-[#B4413C]', expired: 'bg-[#9A6B12]',
  }[key];

  return (
    <span className={`inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-md border px-2.5 py-1 text-xs font-bold ${tone}`}>
      <span aria-hidden="true" className={`size-1.5 shrink-0 rounded-full ${dot}`} />
      {label}
    </span>
  );
}

function ListingRow({
  post,
  detailPath,
  role,
  applied,
  applyStatus,
  onApply,
}: {
  post: PostSummary;
  detailPath: string;
  role: Role;
  applied: boolean;
  applyStatus?: 'applying' | 'applied';
  onApply: (postId: number) => void;
}) {
  const isOpen = toPostStatusKey(post.postStatus) === 'open';
  const canApply = role === 'ESCORT' && isOpen && !applied && !applyStatus;
  const dateLabel = formatMonthDay(daysFromNow(post.startsInDays));
  const location = `${post.region} ${post.district}`.trim();

  return (
    <li className="group px-5 py-5 transition-colors hover:bg-[#F7F8F8] sm:px-6">
      <div className="hidden min-[1100px]:grid grid-cols-[110px_minmax(0,2.5fr)_120px_130px_75px_95px_148px] items-center gap-4">
        <div className="min-w-0"><StatusBadge status={post.postStatus} /></div>
        <div className="min-w-0 pr-1">
          <div className="mb-1 flex min-w-0 items-center gap-2">
            <Link href={detailPath} className="truncate text-base font-semibold text-[#14243A] transition-colors hover:text-[#426B93] hover:underline">{post.title}</Link>
            {post.badge === 'new' && <span className="shrink-0 rounded bg-[#B4413C]/10 px-1.5 py-0.5 text-[11px] font-semibold text-[#B4413C]">신규</span>}
            {post.badge === 'closing' && <span className="shrink-0 rounded bg-[#9A6B12]/15 px-1.5 py-0.5 text-[11px] font-semibold text-[#9A6B12]">오늘 마감</span>}
          </div>
          <p className="truncate text-xs text-[#67717C]">{post.hospitalName}</p>
        </div>
        <div className="min-w-0 truncate text-sm font-medium text-[#202832]" title={location}>{location}</div>
        <div className="min-w-0 text-sm text-[#202832]">
          <p className="font-medium tabular-nums">{dateLabel}</p>
          <p className="text-xs text-[#426B93]">{post.startTime}</p>
        </div>
        <div className="min-w-0 whitespace-nowrap text-sm text-[#67717C]">약 {post.hours}시간</div>
        <div className="min-w-0 whitespace-nowrap text-right">
          <span className="text-[15px] font-bold tabular-nums text-[#14243A]">{post.hourlyPay.toLocaleString()}</span><span className="ml-0.5 text-xs text-[#67717C]">원</span>
        </div>
        <div className="flex min-w-0 items-center justify-center gap-1.5">
          <Link href={detailPath} className="flex h-9 items-center justify-center whitespace-nowrap rounded-lg border border-[#E1E5E8] bg-white px-2.5 text-xs font-semibold text-[#14243A] transition-colors duration-200 hover:bg-[#EDF0F2]">상세보기</Link>
          {role === 'ESCORT' && (
            <button
              type="button"
              onClick={() => onApply(post.id)}
              disabled={!canApply}
              className="h-9 whitespace-nowrap rounded-lg bg-[#14243A] px-2.5 text-xs font-semibold text-white transition-colors duration-200 hover:bg-[#426B93] disabled:cursor-not-allowed disabled:bg-[#EDF0F2] disabled:text-[#67717C]"
            >
              {applied || applyStatus === 'applied' ? '지원완료' : applyStatus === 'applying' ? '지원 중…' : isOpen ? '지원하기' : '지원 불가'}
            </button>
          )}
        </div>
      </div>

      <div className="space-y-3 min-[1100px]:hidden">
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={post.postStatus} />
          {post.badge === 'new' && <span className="rounded bg-[#B4413C]/10 px-1.5 py-0.5 text-[11px] font-semibold text-[#B4413C]">신규</span>}
          {post.badge === 'closing' && <span className="rounded bg-[#9A6B12]/15 px-1.5 py-0.5 text-[11px] font-semibold text-[#9A6B12]">오늘 마감</span>}
        </div>
        <div>
          <Link href={detailPath} className="mb-1 block text-base font-semibold text-[#14243A] transition-colors hover:text-[#426B93] hover:underline">{post.title}</Link>
          <p className="text-xs text-[#67717C]">{post.hospitalName} · {location}</p>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[#E1E5E8]/60 pt-3 text-xs text-[#67717C]">
          <span>일정: <strong className="font-medium text-[#202832]">{dateLabel} {post.startTime}</strong> · 약 {post.hours}시간</span>
          <strong className="text-sm text-[#14243A]">시급 {post.hourlyPay.toLocaleString()}원</strong>
        </div>
        <div className="flex justify-end gap-2">
          <Link href={detailPath} className="flex h-9 items-center justify-center rounded-lg border border-[#E1E5E8] px-4 text-xs font-semibold text-[#14243A] transition-colors duration-200 hover:bg-[#EDF0F2]">상세보기</Link>
          {role === 'ESCORT' && (
            <button
              type="button"
              onClick={() => onApply(post.id)}
              disabled={!canApply}
              className="h-9 rounded-lg bg-[#14243A] px-4 text-xs font-semibold text-white transition-colors duration-200 hover:bg-[#426B93] disabled:cursor-not-allowed disabled:bg-[#EDF0F2] disabled:text-[#67717C]"
            >
              {applied || applyStatus === 'applied' ? '지원완료' : applyStatus === 'applying' ? '지원 중…' : isOpen ? '지원하기' : '지원 불가'}
            </button>
          )}
        </div>
      </div>
    </li>
  );
}

function AuthenticatedPostList({ detailBasePath, role }: { detailBasePath: string; role: Role }) {
  const router = useRouter();
  const { confirm, notice, dialog } = useActionDialog();
  const creationAvailability = usePostCreationEligibility(role === 'CLIENT');
  const [filters, setFilters] = useState<PostFilters>(DEFAULT_FILTERS);
  const [keywordInput, setKeywordInput] = useState('');
  const [page, setPage] = useState(0);
  const [applyStatus, setApplyStatus] = useState<Record<number, 'applying' | 'applied'>>({});
  const [appliedPostIds, setAppliedPostIds] = useState<Set<number>>(new Set());
  const [retryCount, setRetryCount] = useState(0);
  const requestKey = JSON.stringify([filters, page, retryCount]);
  const [result, setResult] = useState<{ key: string; data?: PostPage; error?: string }>();

  useEffect(() => {
    let ignore = false;
    fetchPosts(filters, page, PAGE_SIZE)
      .then((data) => { if (!ignore) setResult({ key: requestKey, data }); })
      .catch((error: Error) => { if (!ignore) setResult({ key: requestKey, error: error.message }); });
    return () => { ignore = true; };
  }, [filters, page, requestKey, retryCount]);

  useEffect(() => {
    if (role !== 'ESCORT') return;
    let ignore = false;
    fetchMyApplications()
      .then((applications) => {
        if (ignore) return;
        setAppliedPostIds(new Set(applications.filter((application) => application.applicationStatus !== 'CANCELED').map((application) => application.postId)));
      })
      .catch(() => { if (!ignore) setAppliedPostIds(new Set()); });
    return () => { ignore = true; };
  }, [role]);

  const loading = result?.key !== requestKey;
  const posts = (!loading && result?.data?.posts) || [];
  const totalCount = (!loading && result?.data?.totalElements) || 0;
  const pageCount = Math.max(1, (!loading && result?.data?.totalPages) || 1);
  const errorMessage = loading ? undefined : result?.error;

  const updateFilters = (patch: Partial<PostFilters>) => {
    setFilters((previous) => ({ ...previous, ...patch }));
    setPage(0);
  };

  const reset = () => {
    setFilters(DEFAULT_FILTERS);
    setKeywordInput('');
    setPage(0);
  };

  const handleApply = async (postId: number) => {
    if (role !== 'ESCORT' || appliedPostIds.has(postId) || applyStatus[postId]) return;
    if (!await confirm({ title: '이 공고에 지원하시겠습니까?', confirmLabel: '지원하기' })) return;
    setApplyStatus((previous) => ({ ...previous, [postId]: 'applying' }));
    try {
      await applyToPost(postId);
      setApplyStatus((previous) => ({ ...previous, [postId]: 'applied' }));
      setAppliedPostIds((previous) => new Set([...previous, postId]));
    } catch (error) {
      setApplyStatus((previous) => {
        const next = { ...previous };
        delete next[postId];
        return next;
      });
      if (isEducationRequiredError(error)) {
        if (await confirm({ title: '교육 영상 화면으로 이동할까요?', description: error instanceof Error ? error.message : '교육 영상 시청을 완료한 후 지원할 수 있습니다.', confirmLabel: '교육 영상 보기' })) {
          router.push('/mypage/education');
        }
        return;
      }
      await notice({ title: '지원에 실패했습니다.', description: error instanceof Error ? error.message : '잠시 후 다시 시도해 주세요.' });
    }
  };

  const chips: { key: string; label: string; clear: Partial<PostFilters> }[] = [];
  if (filters.keyword) chips.push({ key: 'keyword', label: `“${filters.keyword}”`, clear: { keyword: '' } });
  if (filters.region !== 'all') chips.push({ key: 'region', label: filters.region, clear: { region: 'all' } });
  if (filters.period !== 'all') chips.push({ key: 'period', label: optionLabel(PERIOD_OPTIONS, filters.period), clear: { period: 'all' } });
  if (filters.pay !== 'all') chips.push({ key: 'pay', label: optionLabel(PAY_OPTIONS, filters.pay), clear: { pay: 'all' } });

  return (
    <section className="w-full bg-[#F7F8F8] py-12 lg:py-16">
      <div className="mx-auto max-w-[1240px] px-5 sm:px-8 lg:px-16">
        <div className="mb-8 flex flex-col justify-between gap-4 md:flex-row md:items-end">
          <div>
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#426B93]">Escort Opportunities</span>
            <h1 className="text-3xl font-semibold tracking-tight text-[#14243A] sm:text-4xl">동행 공고 찾기</h1>
          </div>
          {role === 'CLIENT' && (
            creationAvailability === 'allowed' ? <Link href="/client/posts/new" className="inline-flex h-11 items-center justify-center self-start rounded-[10px] bg-[#14243A] px-5 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[#1F3757]">새 공고 등록</Link> : <span aria-disabled="true" title={postCreationUnavailableReason(creationAvailability)} className="inline-flex h-11 cursor-not-allowed items-center justify-center self-start rounded-[10px] bg-[#14243A] px-5 text-sm font-semibold text-white opacity-50">새 공고 등록</span>
          )}
        </div>

        <PostSearchPanel
          filters={filters}
          keywordInput={keywordInput}
          onKeywordInputChange={setKeywordInput}
          onSearch={() => updateFilters({ keyword: keywordInput.trim() })}
          onFilterChange={updateFilters}
          onReset={reset}
        />

        <div className="mb-4 flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div role="tablist" aria-label="공고 모집 상태" className="relative grid w-[208px] grid-cols-2 self-start rounded-xl border border-[#E1E5E8] bg-[#EDF0F2] p-1">
            <span
              aria-hidden="true"
              className={`pointer-events-none absolute bottom-1 left-1 top-1 w-[calc(50%_-_4px)] rounded-lg bg-white shadow-xs transition-transform duration-300 ease-out motion-reduce:transition-none ${filters.openOnly ? '' : 'translate-x-full'}`}
            />
            {STATUS_TABS.map((tab) => (
              <button
                type="button"
                role="tab"
                key={String(tab.openOnly)}
                aria-selected={filters.openOnly === tab.openOnly}
                onClick={() => updateFilters({ openOnly: tab.openOnly })}
                className={`relative z-10 rounded-lg px-2 py-1.5 text-sm font-semibold transition-colors duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93] ${filters.openOnly === tab.openOnly ? 'text-[#14243A]' : 'text-[#67717C] hover:text-[#14243A]'}`}
              >{tab.label}</button>
            ))}
          </div>
          <p aria-live="polite" className="text-sm text-[#67717C]">검색 결과 <strong className="font-semibold tabular-nums text-[#14243A]">{loading ? '–' : totalCount}</strong>건</p>
        </div>

        {chips.length > 0 && (
          <div className="mb-4 flex flex-wrap items-center gap-2">
            {chips.map((chip) => (
              <button key={chip.key} type="button" onClick={() => { updateFilters(chip.clear); if (chip.key === 'keyword') setKeywordInput(''); }} className="inline-flex items-center gap-1.5 rounded-full border border-[#E1E5E8] bg-white px-3 py-1.5 text-xs font-semibold text-[#14243A] hover:bg-[#EDF0F2]">
                {chip.label}<X aria-hidden="true" className="size-3" />
              </button>
            ))}
          </div>
        )}

        <div className="overflow-hidden rounded-2xl border border-[#E1E5E8] bg-white shadow-xs">
          <div className="hidden min-[1100px]:grid grid-cols-[110px_minmax(0,2.5fr)_120px_130px_75px_95px_148px] gap-4 border-b border-[#E1E5E8] bg-[#EDF0F2]/60 px-6 py-3.5 text-xs font-semibold text-[#67717C]">
            <div className="min-w-0">상태</div>
            <div className="min-w-0">공고 · 병원</div>
            <div className="min-w-0">지역</div>
            <div className="min-w-0">일정</div>
            <div className="min-w-0">소요</div>
            <div className="text-right">시급</div>
            <div className="text-center">상세 · 지원</div>
          </div>
          {loading ? (
            <div role="status" className="py-20 text-center text-sm text-[#67717C]">공고를 불러오는 중입니다.</div>
          ) : errorMessage ? (
            <div role="alert" className="space-y-2 px-5 py-20 text-center">
              <p className="font-medium text-[#14243A]">공고를 불러오지 못했습니다.</p>
              <p className="text-sm text-[#67717C]">{errorMessage}</p>
              <button type="button" onClick={() => setRetryCount((count) => count + 1)} className="mt-3 rounded-lg border border-[#E1E5E8] px-4 py-2 text-sm font-semibold text-[#14243A]">다시 시도</button>
            </div>
          ) : posts.length === 0 ? (
            <div className="space-y-2 px-5 py-20 text-center">
              <p className="font-medium text-[#14243A]">조건에 맞는 공고가 없습니다.</p>
              <p className="text-sm text-[#67717C]">검색어나 필터 조건을 변경해 보세요.</p>
              <button type="button" onClick={reset} className="mt-4 rounded-lg border border-[#E1E5E8] px-4 py-2 text-xs font-semibold text-[#14243A] hover:bg-[#F7F8F8]">필터 초기화</button>
            </div>
          ) : (
            <ul className="divide-y divide-[#E1E5E8]">
              {posts.map((post) => (
                <ListingRow
                  key={post.id}
                  post={post}
                  detailPath={`${detailBasePath}/${post.id}`}
                  role={role}
                  applied={appliedPostIds.has(post.id)}
                  applyStatus={applyStatus[post.id]}
                  onApply={handleApply}
                />
              ))}
            </ul>
          )}
        </div>

        {!loading && !errorMessage && pageCount > 1 && (
          <nav aria-label="공고 페이지 이동" className="mt-8 flex items-center justify-between gap-4 border-t border-[#E1E5E8] pt-6">
            <button type="button" disabled={page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))} className="inline-flex h-12 items-center gap-1 rounded-[10px] border border-[#E1E5E8] px-4 text-sm font-semibold text-[#14243A] hover:bg-white disabled:cursor-not-allowed disabled:opacity-40"><ChevronLeft aria-hidden="true" className="size-4" />이전</button>
            <div className="hidden items-center gap-2 sm:flex">
              {Array.from({ length: pageCount }, (_, index) => index).filter((index) => index === 0 || index === pageCount - 1 || Math.abs(index - page) <= 2).map((index) => (
                <button key={index} type="button" aria-current={index === page ? 'page' : undefined} onClick={() => setPage(index)} className={`size-12 rounded-[10px] text-sm font-semibold transition-colors ${index === page ? 'bg-[#14243A] text-white' : 'border border-[#E1E5E8] text-[#14243A] hover:bg-white'}`}>{index + 1}</button>
              ))}
            </div>
            <span className="text-sm font-medium text-[#67717C] sm:hidden">{page + 1} / {pageCount}</span>
            <button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((current) => Math.min(pageCount - 1, current + 1))} className="inline-flex h-12 items-center gap-1 rounded-[10px] border border-[#E1E5E8] px-4 text-sm font-semibold text-[#14243A] hover:bg-white disabled:cursor-not-allowed disabled:opacity-40">다음<ChevronRight aria-hidden="true" className="size-4" /></button>
          </nav>
        )}
        {dialog}
      </div>
    </section>
  );
}

export default function PostListPage({ detailBasePath = '/posts' }: Props) {
  const { user, loading, unauthenticated, error } = useCurrentUser();
  if (loading) return <section className="bg-[#F7F8F8] py-28 text-center text-sm text-[#67717C]">로그인 상태를 확인하는 중입니다.</section>;
  if (unauthenticated) return <PostGuestNotice nextPath={detailBasePath} />;
  if (!user) {
    return (
      <section className="bg-[#F7F8F8] px-5 py-28 text-center">
        <p role="alert" className="mb-4 text-sm text-[#67717C]">{error || '로그인 상태를 확인하지 못했습니다.'}</p>
        <button type="button" onClick={() => window.location.reload()} className="rounded-[10px] bg-[#14243A] px-5 py-3 text-sm font-semibold text-white">다시 확인하기</button>
      </section>
    );
  }
  return <AuthenticatedPostList detailBasePath={detailBasePath} role={user.role} />;
}
