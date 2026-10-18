'use client';

import Link from 'next/link';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState, type FormEvent } from 'react';
import { AlertCircle, Check, ChevronLeft, Star } from 'lucide-react';
import { AppShell } from '@/components/layout';
import { useRequireAuth } from '@/features/auth';
import { MAX_TAGS, REVIEW_TAG_ROWS, writeReview } from '@/features/review';
import { fetchClientEscortCase } from '../api';
import type { ClientEscortCase } from '../types';

const RATINGS = [1, 2, 3, 4, 5];
const MAX_COMMENT_LENGTH = 500;
const FOCUS = 'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]';

export default function ClientReviewPage() {
  const { loading: authLoading, user } = useRequireAuth('CLIENT');
  const params = useParams<{ applicationId: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const applicationId = Number(params.applicationId);
  const postIdParam = searchParams.get('postId');
  const postId = postIdParam ? Number(postIdParam) : undefined;

  const [live, setLive] = useState<{ key?: number; escort?: ClientEscortCase }>({});
  const [rating, setRating] = useState(0);
  const [hoverRating, setHoverRating] = useState(0);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [reviewText, setReviewText] = useState('');
  const [showRatingError, setShowRatingError] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');

  useEffect(() => {
    if (postId === undefined || !Number.isFinite(postId) || !Number.isFinite(applicationId)) return;
    let ignore = false;
    fetchClientEscortCase(postId, applicationId)
      .then((escort) => {
        if (!ignore) setLive({ key: postId, escort });
      })
      .catch(() => {
        if (!ignore) setLive({ key: postId });
      });
    return () => { ignore = true; };
  }, [postId, applicationId]);

  const escortLoading = postId !== undefined && Number.isFinite(postId) && live.key !== postId;
  const escort = live.key === postId ? live.escort : undefined;

  const toggleTag = (value: string) => {
    setSelectedTags((prev) =>
      prev.includes(value)
        ? prev.filter((tag) => tag !== value)
        : prev.length < MAX_TAGS
          ? [...prev, value]
          : prev,
    );
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) return;
    if (rating === 0) {
      setShowRatingError(true);
      return;
    }
    setSubmitError('');
    setShowConfirmModal(true);
  };

  const handleConfirmSubmit = async () => {
    if (!escort || submitting) return;
    setSubmitting(true);
    setSubmitError('');
    try {
      await writeReview(escort.applicationId, {
        rating,
        tags: selectedTags,
        content: reviewText.trim(),
      });
      setShowConfirmModal(false);
      router.push(`/client/escort/${escort.applicationId}?postId=${escort.postId}`);
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : '리뷰 제출에 실패했습니다.');
      setShowConfirmModal(false);
      setSubmitting(false);
    }
  };

  if (authLoading || escortLoading) {
    return (
      <AppShell>
        <section className="min-h-[400px] bg-white py-24 text-center text-sm text-[#67717C]">불러오는 중입니다...</section>
      </AppShell>
    );
  }
  if (!user) return null;
  if (!escort) {
    return (
      <AppShell>
        <section className="min-h-[400px] bg-white py-24 text-center text-sm text-[#67717C]">
          동행 정보를 찾을 수 없습니다.
          <div className="mt-6"><Link href="/client/posts" className="font-semibold text-[#14243A] hover:underline">내 공고 목록으로 돌아가기</Link></div>
        </section>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <section className="w-full bg-white py-12 lg:py-16">
        <div className="mx-auto max-w-[1240px] px-5 sm:px-8 lg:px-16">
          <div className="mx-auto max-w-2xl space-y-8">
            <Link
              href="/client/posts"
              className={`inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] transition-colors hover:text-[#14243A] ${FOCUS}`}
            >
              <ChevronLeft className="h-4 w-4" aria-hidden="true" />
              <span>내 공고 목록으로 돌아가기</span>
            </Link>

            <div>
              <h1 className="text-3xl font-semibold tracking-tight text-[#14243A]">동행 매니저 리뷰 작성</h1>
              <p className="mt-1 text-sm text-[#67717C]">
                어르신과 함께한 병원 동행 경험은 어떠셨나요? 소중한 리뷰는 다른 보호자분들께 큰 도움이 됩니다.
              </p>
            </div>

            <form onSubmit={handleSubmit} className="space-y-8 rounded-2xl border border-[#E1E5E8] bg-white p-6 shadow-xs sm:p-8">
              <div className="flex items-center gap-3.5 rounded-xl border border-[#E1E5E8] bg-[#F7F8F8] p-4">
                <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-[#14243A] text-base font-bold text-white" aria-hidden="true">
                  {escort.manager.name.slice(0, 1)}
                </div>
                <div className="min-w-0">
                  <h2 className="text-base font-semibold text-[#14243A]">{escort.manager.name} 동행 매니저</h2>
                  <p className="mt-0.5 text-xs text-[#67717C]">{escort.hospitalName} · {escort.title}</p>
                </div>
              </div>

              <div className="space-y-3">
                <p className="text-sm font-semibold text-[#14243A]" id="client-review-rating-label">동행 만족도 평가</p>
                <div role="radiogroup" aria-labelledby="client-review-rating-label" className="flex flex-wrap items-center justify-center gap-2 sm:justify-start">
                  {RATINGS.map((value) => {
                    const filled = value <= (hoverRating || rating);
                    return (
                      <button
                        key={value}
                        type="button"
                        role="radio"
                        aria-checked={rating === value}
                        aria-label={`${value}점`}
                        onMouseEnter={() => setHoverRating(value)}
                        onMouseLeave={() => setHoverRating(0)}
                        onFocus={() => setHoverRating(value)}
                        onBlur={() => setHoverRating(0)}
                        onClick={() => { setRating(value); setShowRatingError(false); }}
                        className={`flex h-12 w-12 items-center justify-center rounded-xl border border-[#E1E5E8] transition-colors hover:bg-[#F7F8F8] ${FOCUS}`}
                      >
                        <Star className={`h-7 w-7 transition-colors ${filled ? 'fill-[#9A6B12] text-[#9A6B12]' : 'text-[#E1E5E8]'}`} aria-hidden="true" />
                      </button>
                    );
                  })}
                  <span className="ml-2 text-lg font-bold tabular-nums text-[#14243A]">{rating}점 / 5점</span>
                </div>
                {showRatingError && <p role="alert" className="text-sm text-[#B44234]">평점을 선택해주세요.</p>}
              </div>

              <div className="space-y-3">
                <div className="flex items-center justify-between gap-2">
                  <h2 className="text-sm font-semibold text-[#14243A]">어떤 점이 좋으셨나요?</h2>
                  <span className="text-xs text-[#67717C]">(복수 선택 가능)</span>
                </div>
                <div className="space-y-2">
                  {REVIEW_TAG_ROWS.map((row, rowIndex) => (
                    <div key={rowIndex} className="flex flex-wrap gap-2">
                      {row.map((tag) => {
                        const selected = selectedTags.includes(tag.value);
                        const disabled = !selected && selectedTags.length >= MAX_TAGS;
                        return (
                          <button
                            type="button"
                            key={tag.value}
                            aria-pressed={selected}
                            disabled={disabled}
                            onClick={() => toggleTag(tag.value)}
                            className={`h-[42px] rounded-xl border px-4 text-xs font-semibold transition-colors ${FOCUS} ${
                              selected
                                ? 'border-[#14243A] bg-[#14243A] text-white shadow-xs'
                                : 'border-[#E1E5E8] bg-white text-[#202832] hover:bg-[#F7F8F8]'
                            } disabled:cursor-not-allowed disabled:opacity-45`}
                          >
                            {selected ? <Check className="mr-1 inline h-3.5 w-3.5" aria-hidden="true" /> : '+ '}{tag.label}
                          </button>
                        );
                      })}
                    </div>
                  ))}
                </div>
                {selectedTags.length >= MAX_TAGS && <p className="text-xs text-[#67717C]">평가 항목은 최대 {MAX_TAGS}개까지 선택할 수 있어요.</p>}
              </div>

              <div className="space-y-2">
                <label htmlFor="review-comment" className="block text-sm font-semibold text-[#14243A]">
                  상세 후기 작성 <span className="text-xs font-normal text-[#67717C]">(선택)</span>
                </label>
                <textarea
                  id="review-comment"
                  rows={4}
                  maxLength={MAX_COMMENT_LENGTH}
                  value={reviewText}
                  onChange={(event) => setReviewText(event.target.value)}
                  placeholder="어르신과의 동행 중 감동적이었던 순간이나 감사 인사를 자유롭게 적어주세요."
                  className={`w-full resize-none rounded-xl border border-[#E1E5E8] bg-white p-3.5 text-sm text-[#202832] placeholder:text-[#67717C] focus:border-[#426B93] ${FOCUS}`}
                />
              </div>

              <div className="space-y-1 rounded-xl border border-[#E1E5E8] bg-[#EDF0F2] p-4 text-xs text-[#67717C]">
                <p className="flex items-center gap-1.5 font-semibold text-[#14243A]">
                  <AlertCircle className="h-3.5 w-3.5 text-[#426B93]" aria-hidden="true" />
                  리뷰 작성 유의사항
                </p>
                <p>• 리뷰는 한 번만 작성될 수 있으며, 제출 후 수정이 어려울 수 있습니다.</p>
                <p>• 비방, 욕설, 개인정보가 포함된 내용은 운영정책에 따라 가림 처리될 수 있습니다.</p>
              </div>

              {submitError && <p role="alert" className="text-sm font-medium text-[#B44234]">{submitError}</p>}
              <button
                type="submit"
                disabled={submitting}
                className={`h-[52px] w-full rounded-[10px] bg-[#14243A] text-base font-semibold text-white shadow-xs transition-colors hover:bg-[#1f3757] disabled:cursor-not-allowed disabled:opacity-60 ${FOCUS}`}
              >
                {submitting ? '제출 중...' : '리뷰 제출하기'}
              </button>
            </form>
          </div>
        </div>
      </section>

      {showConfirmModal && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4" role="presentation">
          <div role="dialog" aria-modal="true" aria-labelledby="confirm-review-title" className="w-full max-w-sm space-y-4 rounded-2xl bg-white p-6 text-center shadow-xl">
            <h2 id="confirm-review-title" className="text-lg font-semibold text-[#14243A]">리뷰를 제출하시겠습니까?</h2>
            <p className="text-xs text-[#67717C]">
              <span className="block">제출 후에는 수정이 어려울 수 있으니</span>
              <span className="block">작성하신 내용을 다시 한 번 확인해 주세요.</span>
            </p>
            <div className="flex gap-2 pt-2">
              <button
                type="button"
                disabled={submitting}
                onClick={() => setShowConfirmModal(false)}
                className={`h-11 flex-1 rounded-lg border border-[#E1E5E8] text-xs font-semibold text-[#67717C] transition-colors hover:bg-[#F7F8F8] disabled:opacity-50 ${FOCUS}`}
              >
                다시 확인
              </button>
              <button
                type="button"
                disabled={submitting}
                onClick={handleConfirmSubmit}
                className={`h-11 flex-1 rounded-lg bg-[#14243A] text-xs font-semibold text-white transition-colors hover:bg-[#1f3757] disabled:opacity-60 ${FOCUS}`}
              >
                {submitting ? '제출 중...' : '제출 완료'}
              </button>
            </div>
          </div>
        </div>
      )}
    </AppShell>
  );
}
