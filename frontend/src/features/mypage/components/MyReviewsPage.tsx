'use client';

import Link from 'next/link';
import { useEffect, useState, type FormEvent } from 'react';
import { ChevronDown, ChevronLeft, Star } from 'lucide-react';
import { AppShell } from '@/components/layout';
import DropdownSelect from '@/components/ui/DropdownSelect';
import { useRequireAuth } from '@/features/auth';
import { reviewTagInfo } from '@/features/review';
import { fetchMyEscortProfile, fetchMyReviews } from '../api';
import type { ReviewDto } from '../types';

type SortOrder = 'latest' | 'oldest';
type ReviewState = { reviews?: ReviewDto[]; error?: string };

const DATE_INPUT = 'h-[42px] w-full rounded-lg border border-[#E1E5E8] bg-white px-3 text-sm text-[#14243A] outline-none transition-colors hover:border-[#BCC7D2] focus-visible:border-[#426B93] sm:w-[165px]';

function formatDate(value: string): string {
  return value.slice(0, 10).replaceAll('-', '.');
}

function reviewDay(review: ReviewDto): string {
  return review.createdAt.slice(0, 10);
}

function RatingStars({ rating, size = 'small' }: { rating: number; size?: 'small' | 'summary' }) {
  const starSize = size === 'summary' ? 'h-4 w-4' : 'h-3.5 w-3.5';
  return (
    <span aria-label={`5점 만점에 ${rating.toFixed(1)}점`} className="inline-flex items-center gap-1 text-[#9A6B12]">
      {Array.from({ length: 5 }, (_, index) => (
        <Star
          key={index}
          aria-hidden="true"
          className={`${starSize} ${index < Math.round(rating) ? 'fill-[#9A6B12]' : 'fill-transparent'}`}
        />
      ))}
    </span>
  );
}

/** 받은 리뷰: 원본의 평점 요약 + 단일 목록 레이아웃을 실제 리뷰 API와 연결합니다. */
export default function MyReviewsPage() {
  const { loading: authLoading, user } = useRequireAuth('ESCORT');
  const [state, setState] = useState<ReviewState>();
  const [reloadKey, setReloadKey] = useState(0);
  const [sortOrder, setSortOrder] = useState<SortOrder>('latest');
  const [filterOpen, setFilterOpen] = useState(false);
  const [fromInput, setFromInput] = useState('');
  const [toInput, setToInput] = useState('');
  const [range, setRange] = useState({ from: '', to: '' });
  const [filterError, setFilterError] = useState('');

  useEffect(() => {
    if (authLoading || user?.role !== 'ESCORT') return;
    let ignore = false;
    setState(undefined);
    fetchMyEscortProfile()
      .then((profile) => fetchMyReviews(profile.userId))
      .then((reviews) => { if (!ignore) setState({ reviews }); })
      .catch((cause: unknown) => {
        if (!ignore) setState({ error: cause instanceof Error ? cause.message : '리뷰를 불러오지 못했습니다.' });
      });
    return () => { ignore = true; };
  }, [authLoading, user?.role, user?.username, reloadKey]);

  const applyRange = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (fromInput && toInput && fromInput > toInput) {
      setFilterError('시작일이 종료일보다 늦을 수 없습니다.');
      return;
    }
    setFilterError('');
    setRange({ from: fromInput, to: toInput });
    setFilterOpen(false);
  };

  const resetRange = () => {
    setFromInput('');
    setToInput('');
    setRange({ from: '', to: '' });
    setFilterError('');
    setFilterOpen(false);
  };

  if (authLoading || !user || user.role !== 'ESCORT') {
    return <AppShell><div className="min-h-[60vh] bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]">확인 중입니다...</div></AppShell>;
  }

  const allReviews = state?.reviews ?? [];
  const totalCount = allReviews.length;
  const averageRating = totalCount
    ? allReviews.reduce((sum, review) => sum + review.rating, 0) / totalCount
    : 0;
  const scoreBars = [5, 4, 3, 2, 1].map((star) => {
    const count = allReviews.filter((review) => Math.round(review.rating) === star).length;
    return { star, count, pct: totalCount ? (count / totalCount) * 100 : 0 };
  });
  const reviews = allReviews
    .filter((review) => (!range.from || reviewDay(review) >= range.from) && (!range.to || reviewDay(review) <= range.to))
    .sort((a, b) => sortOrder === 'latest'
      ? b.createdAt.localeCompare(a.createdAt) || b.id - a.id
      : a.createdAt.localeCompare(b.createdAt) || a.id - b.id);

  return (
    <AppShell>
      <div className="min-h-[calc(100vh-80px)] w-full bg-[#F7F8F8] py-12 lg:py-16">
        <div className="mx-auto max-w-[1240px] space-y-8 px-5 sm:px-8 lg:px-16">
          <Link
            href="/mypage"
            className="group inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] transition-colors hover:text-[#14243A] sc-focus"
          >
            <ChevronLeft aria-hidden="true" className="h-4 w-4 transition-transform duration-200 group-hover:-translate-x-0.5" />
            <span>마이페이지로 돌아가기</span>
          </Link>

          <div>
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#426B93]">Care Reputation &amp; Ratings</span>
            <h1 className="text-3xl font-semibold tracking-tight text-[#14243A]">받은 리뷰</h1>
            <p className="mt-1 text-sm text-[#67717C]">동행 완료 후 의뢰인과 보호자분들이 남겨주신 소중한 감사 후기입니다.</p>
          </div>

          {!state ? (
            <div role="status" className="rounded-2xl border border-[#E1E5E8] bg-white px-6 py-20 text-center text-sm text-[#67717C]">받은 리뷰를 불러오는 중입니다.</div>
          ) : state.error ? (
            <div role="alert" className="space-y-4 rounded-2xl border border-[#E1E5E8] bg-white px-6 py-14 text-center">
              <p className="text-sm text-[#B4413C]">받은 리뷰를 불러오지 못했습니다. ({state.error})</p>
              <button type="button" onClick={() => setReloadKey((value) => value + 1)} className="rounded-lg bg-[#14243A] px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-[#1F3757]">다시 시도</button>
            </div>
          ) : (
            <>
              <section aria-label="받은 리뷰 평점 요약" className="rounded-2xl border border-[#E1E5E8] bg-white p-6 shadow-xs sm:p-8">
                <div className="grid grid-cols-1 items-center gap-8 md:grid-cols-12">
                  <div className="space-y-2 text-center md:col-span-4 md:border-r md:border-[#E1E5E8] md:pr-6 md:text-left">
                    <span className="block text-xs font-semibold uppercase tracking-wider text-[#67717C]">전체 평균 평점</span>
                    <div className="flex items-center justify-center gap-2 md:justify-start">
                      <span className="text-5xl font-extrabold tabular-nums text-[#14243A]">{averageRating.toFixed(2)}</span>
                      <span className="text-base text-[#67717C]">/ 5.0</span>
                    </div>
                    <div className="flex items-center justify-center md:justify-start">
                      <RatingStars rating={averageRating} size="summary" />
                    </div>
                    <p className="pt-1 text-xs text-[#67717C]">총 <strong className="tabular-nums text-[#14243A]">{totalCount}건</strong>의 동행 후기 집계</p>
                  </div>
                  <div className="space-y-2 md:col-span-8">
                    {scoreBars.map(({ star, count, pct }) => (
                      <div key={star} className="flex items-center gap-3 text-xs">
                        <span className="w-8 text-right font-medium text-[#14243A]">{star}점</span>
                        <div className="h-2.5 flex-1 overflow-hidden rounded-full bg-[#EDF0F2]" role="meter" aria-label={`${star}점 리뷰 비율`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(pct)}>
                          <div className="h-full rounded-full bg-[#9A6B12] transition-[width] duration-300" style={{ width: `${pct}%` }} />
                        </div>
                        <span className="w-10 text-right font-mono tabular-nums text-[#67717C]">{count}건</span>
                      </div>
                    ))}
                  </div>
                </div>
              </section>

              <section aria-label="받은 리뷰 목록" className="rounded-2xl border border-[#E1E5E8] bg-white shadow-xs">
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-t-2xl border-b border-[#E1E5E8] bg-[#EDF0F2]/60 px-6 py-4">
                  <span className="text-xs font-semibold text-[#67717C]">리뷰 목록 ({sortOrder === 'latest' ? '최신순' : '오래된순'})</span>
                  <div className="flex flex-wrap items-center justify-end gap-3">
                    <span className="text-xs font-medium tabular-nums text-[#67717C]">총 {reviews.length}건 표시</span>
                    <button
                      type="button"
                      aria-expanded={filterOpen}
                      aria-controls="escort-review-date-filter"
                      onClick={() => setFilterOpen((value) => !value)}
                      className={`inline-flex h-[42px] items-center gap-1.5 rounded-lg border px-3 text-xs font-semibold transition-colors ${filterOpen || range.from || range.to ? 'border-[#426B93] bg-[#EDF3F8] text-[#426B93]' : 'border-[#E1E5E8] bg-white text-[#14243A] hover:bg-[#F7F8F8]'}`}
                    >
                      기간 조회
                      <ChevronDown aria-hidden="true" className={`h-3.5 w-3.5 transition-transform duration-200 ${filterOpen ? 'rotate-180' : ''}`} />
                    </button>
                    <DropdownSelect
                      id="escort-review-sort"
                      value={sortOrder}
                      placeholder="리뷰 정렬"
                      options={[{ value: 'latest', label: '최신 순' }, { value: 'oldest', label: '오래된 순' }]}
                      onValueChange={(value) => setSortOrder(value as SortOrder)}
                      size="filter"
                      className="w-[100px] [&>button]:h-[42px] [&>button]:text-xs [&>button]:font-semibold [&>button]:text-[#14243A]"
                    />
                  </div>
                </div>

                {filterOpen && (
                  <form id="escort-review-date-filter" onSubmit={applyRange} className="flex flex-wrap items-end gap-3 border-b border-[#E1E5E8] bg-[#F7F8F8] px-6 py-4">
                    <label className="flex flex-1 flex-col gap-1.5 text-xs font-semibold text-[#67717C] sm:flex-none">
                      시작일
                      <input type="date" value={fromInput} max={toInput || undefined} onChange={(event) => { setFromInput(event.target.value); setFilterError(''); }} className={DATE_INPUT} />
                    </label>
                    <label className="flex flex-1 flex-col gap-1.5 text-xs font-semibold text-[#67717C] sm:flex-none">
                      종료일
                      <input type="date" value={toInput} min={fromInput || undefined} onChange={(event) => { setToInput(event.target.value); setFilterError(''); }} className={DATE_INPUT} />
                    </label>
                    <button type="submit" className="h-[42px] rounded-lg bg-[#14243A] px-5 text-xs font-semibold text-white transition-colors hover:bg-[#1F3757]">조회</button>
                    <button type="button" onClick={resetRange} className="h-[42px] rounded-lg border border-[#E1E5E8] bg-white px-4 text-xs font-semibold text-[#14243A] transition-colors hover:bg-[#EDF0F2]">초기화</button>
                    {filterError && <p role="alert" className="w-full text-xs font-medium text-[#B4413C]">{filterError}</p>}
                  </form>
                )}

                {reviews.length === 0 ? (
                  <div className="space-y-2 rounded-b-2xl px-6 py-16 text-center">
                    <p className="text-sm font-semibold text-[#14243A]">{totalCount === 0 ? '아직 받은 리뷰가 없습니다.' : '해당 기간에 받은 리뷰가 없습니다.'}</p>
                    <p className="text-xs text-[#67717C]">{totalCount === 0 ? '동행을 마치고 의뢰인이 리뷰를 남기면 이곳에서 확인할 수 있습니다.' : '기간을 변경하거나 초기화하여 다시 확인해 보세요.'}</p>
                    {(range.from || range.to) && <button type="button" onClick={resetRange} className="mt-3 rounded-lg border border-[#E1E5E8] bg-white px-4 py-2 text-xs font-semibold text-[#14243A] transition-colors hover:bg-[#F7F8F8]">기간 초기화</button>}
                  </div>
                ) : (
                  <ul className="divide-y divide-[#E1E5E8] overflow-hidden rounded-b-2xl">
                    {reviews.map((review) => (
                      <li key={review.id} className="space-y-3.5 p-6 transition-colors hover:bg-[#F7F8F8] sm:p-8">
                        <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
                          <div className="flex items-center gap-3">
                            <div aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#14243A] text-white">
                              <Star className="h-4 w-4 fill-white" />
                            </div>
                            <div>
                              <span className="text-sm font-semibold text-[#14243A]">동행 후기</span>
                              <p className="text-xs text-[#67717C]">의뢰인이 남긴 평가</p>
                            </div>
                          </div>
                          <div className="flex items-center gap-2 self-start text-xs sm:self-auto">
                            <span className="inline-flex items-center gap-1 font-bold text-[#9A6B12]">
                              <Star aria-hidden="true" className="h-3.5 w-3.5 fill-[#9A6B12]" />
                              {review.rating.toFixed(1)}
                            </span>
                            <span className="tabular-nums text-[#67717C]">· {formatDate(review.createdAt)}</span>
                          </div>
                        </div>

                        {review.tags.length > 0 && (
                          <ul aria-label="리뷰 평가 태그" className="flex flex-wrap gap-1.5 pt-1 text-xs">
                            {review.tags.map((tag) => {
                              const info = reviewTagInfo(tag);
                              return (
                                <li key={tag} className="rounded bg-[#EDF0F2] px-2.5 py-1 font-medium text-[#14243A]">
                                  <span aria-hidden="true">{info.positive ? '✓ ' : '· '}</span>{info.label}
                                </li>
                              );
                            })}
                          </ul>
                        )}

                        {review.content?.trim() ? (
                          <p className="pt-1 text-sm leading-relaxed text-[#202832]">“{review.content}”</p>
                        ) : (
                          <p className="pt-1 text-sm text-[#67717C]">작성된 후기 내용이 없습니다.</p>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </>
          )}
        </div>
      </div>
    </AppShell>
  );
}
