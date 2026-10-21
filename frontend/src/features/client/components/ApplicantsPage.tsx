'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { CheckCircle2, ChevronLeft, ShieldCheck, Star, XCircle } from 'lucide-react';
import { AppShell } from '@/components/layout';
import PageFeedback from '@/components/ui/PageFeedback';
import ManagerGradeCharacter from '@/components/ui/ManagerGradeCharacter';
import {
  acceptApplication,
  fetchApplicants,
  fetchEscortProfile,
  rejectApplication,
  type Applicant,
  type ApplicationStatus,
} from '@/features/application';
import { useRequireAuth } from '@/features/auth';
import { fetchPost, type PostDetail } from '@/features/post';

const gradeNames = {
  SEED: '씨앗',
  SPROUT: '새싹',
  FLOWER: '꽃',
  EGGPLANT: '가지',
} as const;

// Match each label to the grade character's main color, with sufficient contrast on white.
const gradeTextColors: Record<keyof typeof gradeNames, string> = {
  SEED: 'text-[#476A50]',
  SPROUT: 'text-[#486D51]',
  FLOWER: 'text-[#5E4E70]',
  EGGPLANT: 'text-[#3E3452]',
};

const stateLabels: Record<ApplicationStatus, string> = {
  PENDING: '승인 대기',
  ACCEPTED: '승인 완료',
  REJECTED: '거절됨',
  CANCELED: '지원 취소',
  NO_SHOW: '노쇼',
};

const BUTTON_BASE = 'h-[38px] rounded-lg px-4 text-xs font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50';

type PageData = { postId: number; post?: PostDetail; applicants?: Applicant[]; error?: string };
type DialogState = { action: 'approve' | 'reject'; applicant: Applicant } | null;

async function loadApplicants(postId: number): Promise<Applicant[]> {
  const items: Awaited<ReturnType<typeof fetchApplicants>> = [];
  const size = 20;
  let page = 0;
  while (true) {
    const next = await fetchApplicants(postId, page, size);
    items.push(...next);
    if (next.length < size) break;
    page += 1;
  }
  return Promise.all(
    items.map(async (item): Promise<Applicant> => ({
      applicationId: item.applicationId,
      escortId: item.escortId,
      name: item.escortName,
      status: item.status,
      profile: await fetchEscortProfile(item.applicationId).catch(() => undefined),
    })),
  );
}

export default function ApplicantsPage() {
  const { loading: authLoading, user } = useRequireAuth('CLIENT');
  const params = useParams<{ postId: string }>();
  const postId = Number(params.postId);
  const [data, setData] = useState<PageData>();
  const [dialog, setDialog] = useState<DialogState>(null);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [decisionError, setDecisionError] = useState<string>();
  const [toastMessage, setToastMessage] = useState('');

  useEffect(() => {
    if (authLoading || !user || !Number.isInteger(postId) || postId <= 0) return;
    let canceled = false;
    Promise.all([fetchPost(postId), loadApplicants(postId)])
      .then(([post, applicants]) => {
        if (!canceled) setData({ postId, post, applicants });
      })
      .catch((error: unknown) => {
        if (!canceled) setData({ postId, error: error instanceof Error ? error.message : '조회에 실패했습니다.' });
      });
    return () => { canceled = true; };
  }, [authLoading, user, postId]);

  useEffect(() => {
    if (!toastMessage) return;
    const timeout = window.setTimeout(() => setToastMessage(''), 3500);
    return () => window.clearTimeout(timeout);
  }, [toastMessage]);

  useEffect(() => {
    if (!dialog) return;
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && pendingId === null) {
        setDialog(null);
      }
    };
    window.addEventListener('keydown', close);
    return () => window.removeEventListener('keydown', close);
  }, [dialog, pendingId]);

  const decide = async () => {
    if (!dialog || pendingId !== null) return;
    const { applicant, action } = dialog;
    setPendingId(applicant.applicationId);
    setDecisionError(undefined);
    try {
      if (action === 'approve') {
        await acceptApplication(applicant.applicationId);
        setData((previous) => previous && ({
          ...previous,
          applicants: previous.applicants?.map((person) =>
            person.applicationId === applicant.applicationId
              ? { ...person, status: 'ACCEPTED' }
              : person.status === 'PENDING'
                ? { ...person, status: 'REJECTED' }
                : person,
          ),
        }));
        setToastMessage(`${applicant.name} 매니저의 지원을 승인했습니다.`);
      } else {
        await rejectApplication(applicant.applicationId);
        setData((previous) => previous && ({
          ...previous,
          applicants: previous.applicants?.map((person) =>
            person.applicationId === applicant.applicationId ? { ...person, status: 'REJECTED' } : person,
          ),
        }));
        setToastMessage(`${applicant.name} 매니저의 지원을 거절했습니다.`);
      }
      setDialog(null);
      fetchPost(postId).then((post) => setData((previous) => previous?.postId === postId ? { ...previous, post } : previous)).catch(() => undefined);
    } catch (error) {
      setDecisionError(error instanceof Error ? error.message : '처리하지 못했습니다. 다시 시도해주세요.');
    } finally {
      setPendingId(null);
    }
  };

  const result = data?.postId === postId ? data : undefined;
  const post = result?.post;
  const applicants = result?.applicants;
  const acceptedId = applicants?.find((person) => person.status === 'ACCEPTED')?.applicationId;
  const canChoose = post?.postStatus === '모집 중' && acceptedId === undefined;

  if (authLoading || !user) {
    return <AppShell><PageFeedback kind="loading" title="로그인 상태를 확인하는 중입니다." /></AppShell>;
  }

  if (!post || !applicants) {
    return (
      <AppShell>
        <PageFeedback kind={result?.error ? 'error' : 'loading'}
          title={result?.error ? '지원자 정보를 불러오지 못했습니다.' : '지원자를 불러오는 중입니다.'}
          description={result?.error}
          href={result?.error ? '/client/posts' : undefined}
          action="내 공고 목록으로 돌아가기" />
      </AppShell>
    );
  }

  return (
    <AppShell>
      <section className="w-full bg-[#F7F8F8] py-12 lg:py-16">
        <div className="mx-auto max-w-[1240px] px-5 sm:px-8 lg:px-16">
          <Link href="/client/posts" className="mb-8 inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] transition-colors hover:text-[#14243A]">
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            <span>내 공고 목록으로 돌아가기</span>
          </Link>

          <div className="mb-8 space-y-1">
            <h1 className="text-3xl font-semibold tracking-tight text-[#14243A]">지원자 확인</h1>
            <p className="text-sm text-[#67717C]">지원한 동행 매니저를 확인하고 승인 또는 거절할 수 있어요.</p>
          </div>

          <div className="mb-8 flex flex-col justify-between gap-3 rounded-2xl border border-[#E1E5E8] bg-[#EDF0F2] p-5 text-sm sm:flex-row sm:items-center">
            <div className="min-w-0">
              <span className="font-semibold text-base text-[#14243A]">{post.title}</span>
              <span className="ml-2 text-xs text-[#67717C]">{post.hospitalName} · {post.startTime} (약 {post.hours}시간)</span>
            </div>
            <div className="shrink-0 sm:text-right">
              <span className="block text-xs text-[#67717C]">공고 시급</span>
              <span className="font-bold tabular-nums text-[#14243A]">{post.hourlyPay.toLocaleString()}원</span>
            </div>
          </div>

          {decisionError && <p role="alert" className="mb-6 rounded-lg border border-[#B4413C]/30 bg-[#B4413C]/5 px-4 py-3 text-sm text-[#B4413C]">{decisionError}</p>}

          <div className="grid grid-cols-1 items-start gap-8 lg:grid-cols-12">
            <section className="space-y-4 lg:col-span-8" aria-label="지원자 목록">
              <div className="flex items-center justify-between text-sm text-[#67717C]">
                <span>지원한 매니저 <strong className="tabular-nums text-[#14243A]">{applicants.length}</strong>명</span>
              </div>

              {applicants.length === 0 ? (
                <div className="rounded-2xl border border-[#E1E5E8] bg-white p-12 text-center text-[#67717C]">
                  <p className="text-base font-medium text-[#14243A]">현재 지원자가 없습니다.</p>
                  <p className="mt-1 text-sm">지원자가 등록되면 이곳에서 확인할 수 있습니다.</p>
                </div>
              ) : applicants.map((applicant) => {
                const profile = applicant.profile;
                const decisionLocked = !canChoose || pendingId !== null;
                const isPending = applicant.status === 'PENDING';
                return (
                  <article key={applicant.applicationId} className="space-y-4 rounded-2xl border border-[#E1E5E8] bg-white p-6 shadow-xs transition-colors hover:border-[#426B93]">
                    <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
                      <div className="flex min-w-0 items-center gap-3.5">
                        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-[#14243A] text-base font-bold text-white" aria-hidden="true">
                          {applicant.name.charAt(0)}
                        </div>
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-2">
                            <h2 className="text-base font-semibold text-[#14243A]">{applicant.name} 매니저</h2>
                            {profile?.grade && <span className="inline-flex items-center gap-1">
                              <ManagerGradeCharacter grade={profile.grade} bare className="h-7 w-7 shrink-0" />
                              <span className={`text-sm font-semibold ${gradeTextColors[profile.grade]}`}>{gradeNames[profile.grade]}</span>
                            </span>}
                            {!isPending && <span className="rounded bg-[#EDF0F2] px-2 py-0.5 text-xs font-semibold text-[#67717C]">{stateLabels[applicant.status]}</span>}
                          </div>
                          {profile ? (
                            <div className="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-[#67717C]">
                              <span>동행 완료 <strong className="tabular-nums text-[#14243A]">{profile.completedCount}건</strong></span>
                              <span>·</span>
                              {profile.ratingCount > 0 ? (
                                <span className="flex items-center gap-0.5 font-semibold text-[#9A6B12]">
                                  <Star className="h-3.5 w-3.5 fill-[#9A6B12]" aria-hidden="true" />
                                  {profile.rating.toFixed(1)} <span className="font-normal text-[#67717C]">(리뷰 {profile.ratingCount}건)</span>
                                </span>
                              ) : <span>평가 없음</span>}
                            </div>
                          ) : <p className="mt-1 text-xs text-[#67717C]">프로필 정보를 불러올 수 없습니다.</p>}
                        </div>
                      </div>
                      <div className="shrink-0 sm:text-right">
                        <span className="block text-xs text-[#67717C]">공고 시급</span>
                        <span className="font-bold tabular-nums text-[#14243A]">{post.hourlyPay.toLocaleString()}원</span>
                      </div>
                    </div>

                    {profile && (
                      <div className="flex flex-wrap items-center gap-1.5 pt-1 text-xs">
                        <span className="rounded bg-[#EDF0F2] px-2.5 py-1 font-medium text-[#202832]">
                          {profile.verified ? '✓ 본인 인증 완료' : '본인 인증 미완료'}
                        </span>
                        <span className="rounded bg-[#EDF0F2] px-2.5 py-1 font-medium text-[#202832]">노쇼 {profile.noShowCount}회</span>
                      </div>
                    )}

                    <div className="rounded-xl border border-[#E1E5E8] bg-[#F7F8F8] p-3.5 text-xs leading-relaxed text-[#202832]">
                      {profile?.intro?.trim() ? `“${profile.intro}”` : '작성된 자기소개가 없습니다.'}
                    </div>

                    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[#E1E5E8] pt-4">
                      <Link href={`/client/posts/${postId}/applicants/${applicant.applicationId}/profile`} className="text-xs font-semibold text-[#426B93] transition-colors hover:text-[#345778] hover:underline sc-focus">
                        상세 프로필 보기
                      </Link>
                      {isPending ? (
                        <div className="flex items-center gap-2">
                          <button type="button" disabled={decisionLocked} onClick={() => { setDecisionError(undefined); setDialog({ action: 'reject', applicant }); }} className={`${BUTTON_BASE} border border-[#B4413C]/30 bg-white text-[#B4413C] hover:bg-[#B4413C]/5`}>
                            거절
                          </button>
                          <button type="button" disabled={decisionLocked} onClick={() => { setDecisionError(undefined); setDialog({ action: 'approve', applicant }); }} className={`${BUTTON_BASE} bg-[#14243A] px-5 text-white hover:bg-[#1f3757]`}>
                            승인하기
                          </button>
                        </div>
                      ) : <span className="rounded-lg bg-[#EDF0F2] px-4 py-2 text-xs font-semibold text-[#67717C]">{stateLabels[applicant.status]}</span>}
                    </div>
                  </article>
                );
              })}
              {!canChoose && applicants.some((applicant) => applicant.status === 'PENDING') && (
                <p className="text-xs text-[#67717C]">이미 매칭되었거나 모집이 종료된 공고는 지원자를 새로 승인·거절할 수 없습니다.</p>
              )}
            </section>

            <aside className="space-y-6 lg:sticky lg:top-28 lg:col-span-4" aria-label="동행 매니저 선택 가이드">
              <div className="space-y-4 rounded-2xl border border-[#E1E5E8] bg-white p-6 shadow-xs">
                <div className="flex items-center gap-2 border-b border-[#E1E5E8] pb-3">
                  <ShieldCheck className="h-5 w-5 text-[#426B93]" aria-hidden="true" />
                  <h2 className="text-base font-semibold text-[#14243A]">동행 매니저 선택 가이드</h2>
                </div>
                <div className="space-y-3.5 text-xs leading-relaxed text-[#67717C]">
                  <div>
                    <h3 className="mb-1 font-semibold text-[#14243A]">1. 매니저 등급 확인 (씨앗~가지)</h3>
                    <p>완료 동행 횟수에 따라 씨앗(0~4회), 새싹(5~9회), 꽃(10~29회), 가지(30회 이상)로 구분되며 노쇼 페널티가 반영될 수 있습니다.</p>
                  </div>
                  <div>
                    <h3 className="mb-1 font-semibold text-[#14243A]">2. 인증 여부 확인</h3>
                    <p>프로필에 표시된 본인 인증 여부를 확인하고, 추가 정보가 필요하면 상세 프로필을 살펴보세요.</p>
                  </div>
                  <div>
                    <h3 className="mb-1 font-semibold text-[#14243A]">3. 실제 이용자 평가</h3>
                    <p>평점과 리뷰 개수, 완료 동행 횟수를 함께 참고해보세요. 아직 리뷰가 없는 매니저도 있습니다.</p>
                  </div>
                  <div>
                    <h3 className="mb-1 font-semibold text-[#14243A]">4. 노쇼 이력 확인</h3>
                    <p>노쇼 횟수도 프로필에서 함께 확인할 수 있습니다. 매칭 확정 후 취소할 때는 서비스 정책이 적용됩니다.</p>
                  </div>
                </div>
              </div>
            </aside>
          </div>

          {toastMessage && (
            <div role="status" className="fixed bottom-24 right-6 z-50 flex max-w-sm items-center gap-2 rounded-xl border border-white/20 bg-[#14243A] px-5 py-3.5 text-sm font-medium text-white shadow-lg">
              <CheckCircle2 className="h-5 w-5 shrink-0 text-[#89C5A9]" aria-hidden="true" />
              <span>{toastMessage}</span>
            </div>
          )}

          {dialog && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" role="presentation">
              <div role="dialog" aria-modal="true" aria-labelledby="applicant-decision-title" className="w-full max-w-sm space-y-4 rounded-2xl bg-white p-6 text-center shadow-xl">
                <div className={`mx-auto flex h-12 w-12 items-center justify-center rounded-full ${dialog.action === 'reject' ? 'bg-[#B4413C]/10 text-[#B4413C]' : 'bg-[#2F7D5B]/10 text-[#2F7D5B]'}`}>
                  {dialog.action === 'reject' ? <XCircle className="h-6 w-6" aria-hidden="true" /> : <CheckCircle2 className="h-6 w-6" aria-hidden="true" />}
                </div>
                <h2 id="applicant-decision-title" className="text-lg font-semibold text-[#14243A]">
                  {dialog.action === 'reject' ? '이 지원자를 거절하시겠습니까?' : '이 지원자를 승인하시겠습니까?'}
                </h2>
                <p className="text-xs leading-relaxed text-[#67717C]">
                  {dialog.action === 'reject'
                    ? `${dialog.applicant.name} 매니저의 지원을 거절 처리합니다. 다른 매니저의 지원은 계속 확인할 수 있습니다.`
                    : `${dialog.applicant.name} 매니저의 지원을 승인하면 같은 공고의 다른 대기 중인 지원은 자동으로 거절됩니다.`}
                </p>
                {decisionError && <p role="alert" className="text-xs text-[#B4413C]">{decisionError}</p>}
                <div className="flex gap-2 pt-2">
                  <button type="button" disabled={pendingId !== null} onClick={() => setDialog(null)} className="h-[44px] flex-1 rounded-lg border border-[#E1E5E8] bg-white text-xs font-semibold text-[#67717C] hover:bg-[#F7F8F8]">취소</button>
                  <button type="button" disabled={pendingId !== null} onClick={decide} className={`h-[44px] flex-1 rounded-lg text-xs font-semibold text-white disabled:opacity-50 ${dialog.action === 'reject' ? 'bg-[#B4413C] hover:bg-[#9a3530]' : 'bg-[#14243A] hover:bg-[#1f3757]'}`}>
                    {pendingId !== null ? '처리 중…' : dialog.action === 'reject' ? '거절 확인' : '승인 확인'}
                  </button>
                </div>
              </div>
            </div>
          )}


        </div>
      </section>
    </AppShell>
  );
}
