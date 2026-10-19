'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { ChevronLeft, MapPin, ShieldCheck, Star } from 'lucide-react';
import { AppShell } from '@/components/layout';
import DropdownSelect from '@/components/ui/DropdownSelect';
import ManagerGradeCharacter from '@/components/ui/ManagerGradeCharacter';
import { fetchEscortProfile, type EscortGrade, type EscortProfileDto } from '@/features/application';
import { useRequireAuth } from '@/features/auth';
import { fetchUserReviews, reviewTagInfo, type ReviewDto } from '@/features/review';

const GRADE_NAMES: Record<EscortGrade, string> = {
  SEED: '씨앗',
  SPROUT: '새싹',
  FLOWER: '꽃',
  EGGPLANT: '가지',
};

// FAQ의 '매니저 등급 및 서비스 이용 안내'에 사용된 등급별 배지 색상과 통일합니다.
const GRADE_BADGE_COLORS: Record<EscortGrade, string> = {
  SEED: 'bg-[#F4F6F3] text-[#4A6351] border-[#D9E2DA]',
  SPROUT: 'bg-[#F0F5F1] text-[#3E6547] border-[#CDE0D2]',
  FLOWER: 'bg-[#F5F2F8] text-[#5C4F6B] border-[#DDD5E6]',
  EGGPLANT: 'bg-[#F0EEF5] text-[#3E3452] border-[#D3CDE0]',
};

type ReviewSort = 'latest' | 'oldest';
type ReviewLoad = 'loading' | 'ready' | 'error';
type ProfileState = {
  applicationId: number;
  profile?: EscortProfileDto;
  profileError?: string;
  reviewStatus?: ReviewLoad;
  reviews?: ReviewDto[];
  reviewsError?: string;
};

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '잠시 후 다시 시도해 주세요.';
}

function dateLabel(value: string): string {
  return value.slice(0, 10).replaceAll('-', '.');
}

function ScoreStars({ score }: { score: number }) {
  const rating = Math.max(0, Math.min(5, score));
  return (
    <span aria-label={`5점 만점에 ${rating.toFixed(1)}점`} className="inline-flex items-center gap-1 text-[#9A6B12]">
      {Array.from({ length: 5 }, (_, index) => (
        <Star key={index} aria-hidden="true" className={`h-4 w-4 ${index < Math.round(rating) ? 'fill-[#9A6B12]' : 'fill-transparent'}`} />
      ))}
    </span>
  );
}

/** 지원자 상세: 실제 지원자 프로필과 공개 리뷰를 각각 조회해 별도로 오류를 처리합니다. */
export default function ApplicantProfilePage() {
  const { loading: authLoading, user } = useRequireAuth('CLIENT');
  const params = useParams<{ postId: string; applicationId: string }>();
  const postId = Number(params.postId);
  const applicationId = Number(params.applicationId);
  const [state, setState] = useState<ProfileState>();
  const [reloadKey, setReloadKey] = useState(0);
  const [reviewSort, setReviewSort] = useState<ReviewSort>('latest');

  useEffect(() => {
    if (authLoading || !user || user.role !== 'CLIENT' || !Number.isInteger(applicationId) || applicationId <= 0) return;
    let canceled = false;
    setState({ applicationId });

    fetchEscortProfile(applicationId)
      .then((profile) => {
        if (canceled) return;
        setState({ applicationId, profile, reviewStatus: 'loading' });
        return fetchUserReviews(profile.escortId)
          .then((reviews) => {
            if (!canceled) setState({ applicationId, profile, reviews, reviewStatus: 'ready' });
          })
          .catch((error: unknown) => {
            if (!canceled) setState({ applicationId, profile, reviewStatus: 'error', reviewsError: errorMessage(error) });
          });
      })
      .catch((error: unknown) => {
        if (!canceled) setState({ applicationId, profileError: errorMessage(error) });
      });

    return () => { canceled = true; };
  }, [applicationId, authLoading, reloadKey, user?.role]);

  const backHref = Number.isInteger(postId) && postId > 0 ? `/client/posts/${postId}/applicants` : '/client/posts';
  const current = state?.applicationId === applicationId ? state : undefined;
  const profile = current?.profile;

  if (authLoading || !user || user.role !== 'CLIENT') {
    return <AppShell><div className="min-h-[60vh] bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]">사용자 정보를 확인 중입니다.</div></AppShell>;
  }

  const reviews = current?.reviews ?? [];
  const sortedReviews = [...reviews].sort((a, b) => reviewSort === 'latest'
    ? b.createdAt.localeCompare(a.createdAt) || b.id - a.id
    : a.createdAt.localeCompare(b.createdAt) || a.id - b.id);

  return (
    <AppShell>
      <div className="min-h-[calc(100vh-80px)] bg-[#F7F8F8] py-12 lg:py-16">
        <div className="mx-auto max-w-[1120px] space-y-8 px-5 sm:px-8">
          <Link href={backHref} className="group inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] transition-colors hover:text-[#14243A] sc-focus">
            <ChevronLeft aria-hidden="true" className="h-4 w-4 transition-transform duration-200 group-hover:-translate-x-0.5" />
            지원자 확인으로 돌아가기
          </Link>

          <header>
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#426B93]">Escort Profile</span>
            <h1 className="text-3xl font-semibold tracking-tight text-[#14243A]">지원자 프로필</h1>
            <p className="mt-1 text-sm text-[#67717C]">지원한 동행 매니저의 활동 정보와 실제 이용자가 남긴 리뷰를 확인할 수 있어요.</p>
          </header>

          {!current || (!profile && !current.profileError) ? (
            <div role="status" className="rounded-2xl border border-[#E1E5E8] bg-white px-6 py-20 text-center text-sm text-[#67717C]">지원자 프로필을 불러오는 중입니다.</div>
          ) : current.profileError ? (
            <div role="alert" className="space-y-4 rounded-2xl border border-[#E1E5E8] bg-white px-6 py-16 text-center">
              <p className="text-sm text-[#B4413C]">지원자 정보를 불러오지 못했습니다. ({current.profileError})</p>
              <button type="button" onClick={() => setReloadKey((value) => value + 1)} className="rounded-lg bg-[#14243A] px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-[#426B93]">다시 시도</button>
            </div>
          ) : profile ? (
            <>
              <section aria-label="동행 매니저 프로필" className="overflow-hidden rounded-2xl border border-[#E1E5E8] bg-white shadow-xs">
                <div className="space-y-5 p-6 sm:p-8">
                  <div className="flex flex-col gap-5 sm:flex-row sm:items-center">
                    <div className="flex h-[76px] w-[76px] shrink-0 items-center justify-center rounded-2xl bg-[#14243A] text-3xl font-semibold text-white" aria-hidden="true">
                      {profile.name.slice(0, 1)}
                    </div>
                    <div className="min-w-0 flex-1 space-y-2">
                      <div className="flex flex-wrap items-center gap-2.5">
                        <h2 className="text-2xl font-semibold tracking-tight text-[#14243A]">{profile.name} <span className="text-lg font-medium">매니저</span></h2>
                        <span className={`inline-flex h-8 shrink-0 items-center gap-1.5 rounded-lg border px-2.5 text-xs font-semibold leading-none ${GRADE_BADGE_COLORS[profile.grade]}`}>
                          <ManagerGradeCharacter grade={profile.grade} bare className="h-5 w-5 shrink-0" />
                          {GRADE_NAMES[profile.grade]}
                        </span>
                        {profile.verified && <span className="inline-flex h-8 shrink-0 items-center gap-1 rounded-lg border border-[#D5E2F3] bg-[#EAF2FC] px-2.5 text-xs font-semibold leading-none text-[#426B93]"><ShieldCheck aria-hidden="true" className="h-4 w-4 shrink-0" />인증완료</span>}
                      </div>
                      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-[#67717C]">
                        <span>{profile.age}세 · {profile.gender === 'MALE' ? '남성' : '여성'}</span>
                        {profile.region && <span className="inline-flex items-center gap-1"><MapPin aria-hidden="true" className="h-3.5 w-3.5" />{profile.region}</span>}
                      </div>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-3 border-t border-[#E1E5E8] pt-5 sm:grid-cols-4">
                    <div className="rounded-xl bg-[#F7F8F8] p-4"><p className="text-xs text-[#67717C]">평균 평점</p><p className="mt-2 text-xl font-semibold tabular-nums text-[#14243A]">{profile.ratingCount > 0 ? profile.rating.toFixed(1) : '—'} <span className="text-xs font-normal text-[#67717C]">/ 5.0</span></p></div>
                    <div className="rounded-xl bg-[#F7F8F8] p-4"><p className="text-xs text-[#67717C]">받은 평가</p><p className="mt-2 text-xl font-semibold tabular-nums text-[#14243A]">{profile.ratingCount} <span className="text-xs font-normal text-[#67717C]">건</span></p></div>
                    <div className="rounded-xl bg-[#F7F8F8] p-4"><p className="text-xs text-[#67717C]">완료 동행</p><p className="mt-2 text-xl font-semibold tabular-nums text-[#14243A]">{profile.completedCount} <span className="text-xs font-normal text-[#67717C]">회</span></p></div>
                    <div className="rounded-xl bg-[#F7F8F8] p-4"><p className="text-xs text-[#67717C]">노쇼 횟수</p><p className="mt-2 text-xl font-semibold tabular-nums text-[#14243A]">{profile.noShowCount} <span className="text-xs font-normal text-[#67717C]">회</span></p></div>
                  </div>
                </div>
                <div className="border-t border-[#E1E5E8] px-6 py-6 sm:px-8">
                  <h3 className="mb-2 text-sm font-semibold text-[#14243A]">자기소개</h3>
                  <p className="whitespace-pre-wrap break-words text-sm leading-7 text-[#67717C]">{profile.intro?.trim() || '아직 작성된 자기소개가 없습니다.'}</p>
                </div>
              </section>

              <section aria-label="받은 리뷰" className="overflow-hidden rounded-2xl border border-[#E1E5E8] bg-white shadow-xs">
                <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#E1E5E8] px-6 py-5 sm:px-8">
                  <div>
                    <h2 className="text-lg font-semibold text-[#14243A]">받은 리뷰</h2>
                    <p className="mt-1 text-xs text-[#67717C]">동행을 마친 의뢰인이 남긴 평가입니다.</p>
                  </div>
                  {current.reviewStatus === 'ready' && (
                    <div className="flex items-center gap-3">
                      <span className="text-xs tabular-nums text-[#67717C]">총 {reviews.length}건</span>
                      <DropdownSelect id="applicant-profile-review-sort" value={reviewSort} onValueChange={(value) => setReviewSort(value as ReviewSort)} options={[{ value: 'latest', label: '최신 순' }, { value: 'oldest', label: '오래된 순' }]} size="filter" className="w-[100px] [&>button]:h-[42px] [&>button]:text-xs [&>button]:font-semibold" />
                    </div>
                  )}
                </div>

                {current.reviewStatus === 'loading' ? (
                  <p role="status" className="px-6 py-16 text-center text-sm text-[#67717C]">받은 리뷰를 불러오는 중입니다.</p>
                ) : current.reviewStatus === 'error' ? (
                  <div role="alert" className="space-y-3 px-6 py-14 text-center">
                    <p className="text-sm text-[#B4413C]">받은 리뷰를 불러오지 못했습니다. ({current.reviewsError})</p>
                    <p className="text-xs text-[#67717C]">조회 실패는 리뷰가 없는 것과 다릅니다.</p>
                    <button type="button" onClick={() => setReloadKey((value) => value + 1)} className="rounded-lg bg-[#14243A] px-5 py-2.5 text-xs font-semibold text-white transition-colors hover:bg-[#426B93]">다시 시도</button>
                  </div>
                ) : sortedReviews.length === 0 ? (
                  <div className="space-y-2 px-6 py-16 text-center">
                    <p className="text-sm font-semibold text-[#14243A]">아직 받은 리뷰가 없습니다.</p>
                    <p className="text-xs text-[#67717C]">실제 리뷰가 등록되면 이곳에서 확인할 수 있습니다.</p>
                  </div>
                ) : (
                  <ul className="divide-y divide-[#E1E5E8]">
                    {sortedReviews.map((review) => (
                      <li key={review.id} className="space-y-3.5 p-6 transition-colors hover:bg-[#F7F8F8] sm:p-8">
                        <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
                          <div className="flex items-center gap-3">
                            <div aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#14243A] text-white"><Star className="h-4 w-4 fill-white" /></div>
                            <div><span className="text-sm font-semibold text-[#14243A]">동행 후기</span><p className="text-xs text-[#67717C]">의뢰인이 남긴 평가</p></div>
                          </div>
                          <div className="flex items-center gap-2 text-xs">
                            <ScoreStars score={review.rating} />
                            <span className="font-semibold tabular-nums text-[#9A6B12]">{review.rating.toFixed(1)}</span>
                            <span className="tabular-nums text-[#67717C]">· {dateLabel(review.createdAt)}</span>
                          </div>
                        </div>
                        {review.tags.length > 0 && (
                          <ul aria-label="리뷰 평가 태그" className="flex flex-wrap gap-1.5">
                            {review.tags.map((tag) => {
                              const info = reviewTagInfo(tag);
                              return <li key={tag} className="rounded bg-[#EDF0F2] px-2.5 py-1 text-xs font-medium text-[#14243A]">{info.positive ? '✓ ' : '· '}{info.label}</li>;
                            })}
                          </ul>
                        )}
                        <p className={review.content?.trim() ? 'whitespace-pre-wrap break-words text-sm leading-relaxed text-[#202832]' : 'text-sm text-[#67717C]'}>
                          {review.content?.trim() ? `“${review.content}”` : '작성된 후기 내용이 없습니다.'}
                        </p>
                      </li>
                    ))}
                  </ul>
                )}
              </section>

            </>
          ) : null}
        </div>
      </div>
    </AppShell>
  );
}
