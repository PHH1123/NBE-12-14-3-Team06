'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AppShell } from '@/components/layout';
import PageFeedback from '@/components/ui/PageFeedback';
import { useRequireAuth } from '@/features/auth';
import { fetchMyEscortProfile } from '@/features/mypage/api';
import { Video, CheckCircle2, ChevronLeft, X, Clock, Info } from 'lucide-react';
import { fetchEducationVideo, fetchEducationVideos } from '../api';
import { watchPercent } from '../model/status';
import { formatDuration } from '../lib/time';
import type { EducationVideoDto } from '../types';
import EducationPlayer, { RULES } from './EducationPlayer';

type EducationState = { videos?: EducationVideoDto[]; verified?: boolean; error?: string };

export default function EducationListPage() {
  const router = useRouter();
  const { loading: authLoading, user } = useRequireAuth('ESCORT');
  const [state, setState] = useState<EducationState>();
  const [activeVideoId, setActiveVideoId] = useState<number | null>(null);
  const [activeVideo, setActiveVideo] = useState<EducationVideoDto | null>(null);
  const [modalError, setModalError] = useState<string | null>(null);
  const [showWatchGuide, setShowWatchGuide] = useState(false);

  useEffect(() => {
    if (authLoading || user?.role !== 'ESCORT') return;
    let canceled = false;
    Promise.all([fetchEducationVideos(), fetchMyEscortProfile()])
      .then(([videos, profile]) => { if (!canceled) setState({ videos, verified: profile.verified }); })
      .catch((error: unknown) => { if (!canceled) setState({ error: error instanceof Error ? error.message : '교육 정보를 불러오지 못했습니다.' }); });
    return () => { canceled = true; };
  }, [authLoading, user?.role, user?.username]);

  useEffect(() => {
    if (activeVideoId === null) return;
    let canceled = false;
    fetchEducationVideo(activeVideoId)
      .then((video) => { if (!canceled) setActiveVideo(video); })
      .catch((error: unknown) => { if (!canceled) setModalError(error instanceof Error ? error.message : '영상을 불러오지 못했습니다.'); });
    return () => { canceled = true; };
  }, [activeVideoId]);

  const openVideo = (id: number) => {
    setActiveVideo(null);
    setModalError(null);
    setShowWatchGuide(false);
    setActiveVideoId(id);
  };

  const closeVideo = () => {
    setActiveVideoId(null);
    setActiveVideo(null);
    setShowWatchGuide(false);
    fetchEducationVideos().then((videos) => setState((prev) => ({ ...prev, videos }))).catch(() => {});
    fetchMyEscortProfile().then((profile) => setState((prev) => ({ ...prev, verified: profile.verified }))).catch(() => {});
  };

  if (authLoading) return <AppShell><PageFeedback kind="loading" title="교육 정보를 불러오는 중입니다." /></AppShell>;
  if (!user) return null;
  if (!state?.videos) return <AppShell><PageFeedback kind={state?.error ? "error" : "loading"} title={state?.error ? "교육 정보를 불러오지 못했습니다." : "교육 정보를 불러오는 중입니다."} description={state?.error} /></AppShell>;

  const required = state.videos.filter((v) => v.required);
  const requiredDone = required.filter((v) => v.completed).length;
  const allRequiredCompleted = required.length > 0 && required.every((video) => video.completed);
  const verified = state.verified ?? false;
  const videos = state.videos.map((video) => ({
    id: video.videoId,
    title: video.title,
    isRequired: video.required,
    isCompleted: video.completed,
    progressPercent: watchPercent(video),
    duration: formatDuration(video.durationSec),
  }));
  return (
    <AppShell>
    <div className="w-full bg-[#F7F8F8] py-12 lg:py-16">
      <div className="max-w-[1240px] mx-auto px-5 sm:px-8 lg:px-16 space-y-8">
        <button
          onClick={() => router.push('/mypage')}
          className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] hover:text-[#14243A] sc-focus"
        >
          <ChevronLeft className="w-4 h-4" />
          <span>마이페이지로 돌아가기</span>
        </button>

        <div>
          <span className="text-xs font-semibold text-[#426B93] uppercase tracking-wider block mb-1">
            Mandatory Manager Training
          </span>
          <h1 className="text-3xl font-semibold text-[#14243A] tracking-tight">
            동행 매니저 교육 영상
          </h1>
          <p className="text-sm font-medium text-[#14243A] mt-1">
            {verified ? '교육 이수 인증이 완료되어 공고에 지원할 수 있습니다.' : '필수 영상을 모두 시청해야 공고에 지원할 수 있습니다.'}
          </p>
        </div>

        {/* COMPLETION BANNER */}
        {verified || allRequiredCompleted ? (
          <div className="bg-[#2F7D5B]/10 border border-[#2F7D5B]/30 rounded-2xl p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-center gap-3.5">
              <div className="w-12 h-12 rounded-xl bg-[#2F7D5B] text-white flex items-center justify-center shrink-0">
                <CheckCircle2 className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-[#2F7D5B]">
                  {allRequiredCompleted
                    ? '교육 이수가 완료되었습니다. 이제 공고에 지원할 수 있습니다.'
                    : '교육 이수 인증이 확인되었습니다. 이제 공고에 지원할 수 있습니다.'}
                </h3>
                <p className="text-xs text-[#202832] mt-0.5">
                  {allRequiredCompleted
                    ? `현재 등록된 필수 영상 시청 기록: ${requiredDone} / ${required.length}`
                    : `현재 등록된 필수 영상 시청 기록: ${requiredDone} / ${required.length} (인증 상태와 별도로 표시됩니다.)`}
                </p>
              </div>
            </div>

            <button
              onClick={() => router.push('/escort/posts')}
              className="h-[44px] px-6 rounded-xl bg-[#2F7D5B] text-white text-xs font-semibold hover:bg-[#256348] whitespace-nowrap shadow-xs"
            >
              공고 찾아보기
            </button>
          </div>
        ) : (
          <div className="bg-[#9A6B12]/10 border border-[#9A6B12]/20 rounded-2xl p-5 flex items-center gap-3 text-xs text-[#9A6B12]">
            <Clock className="w-5 h-5 shrink-0" />
            <span>
              필수 교육 영상 미이수 건이 남아있습니다. 영상을 100% 시청 완료 후 공고 지원 자격이 활성화됩니다.
            </span>
          </div>
        )}

        {/* LIST CONTAINER (Batch 1 list pattern: 1 white container with 1px dividers) */}
        <div className="bg-white rounded-2xl border border-[#E1E5E8] shadow-xs overflow-hidden">
          {/* Desktop Table Header */}
          <div className="hidden min-[1100px]:grid grid-cols-12 gap-4 px-6 py-3.5 bg-[#EDF0F2]/60 border-b border-[#E1E5E8] text-xs font-semibold text-[#67717C]">
            <div className="col-span-1">구분</div>
            <div className="col-span-5">교육 영상 제목</div>
            <div className="col-span-2">영상 길이</div>
            <div className="col-span-2">시청 진행률</div>
            <div className="col-span-1 text-center">상태</div>
            <div className="col-span-1 text-center">시청하기</div>
          </div>

          <div className="divide-y divide-[#E1E5E8]">
            {videos.map((vid) => {
              const isFinished = vid.isCompleted;
              const hasStarted = vid.progressPercent > 0 && vid.progressPercent < 100;
              const videoStatusLabel = isFinished ? '이수 완료' : verified ? (hasStarted ? '시청 중' : '미시청') : '미이수';
              return (
                <div key={vid.id} className="p-6 hover:bg-[#F7F8F8] transition-colors">
                  {/* Desktop Layout */}
                  <div className="hidden min-[1100px]:grid grid-cols-12 gap-4 items-center text-sm">
                    {/* 필수 / 선택 */}
                    <div className="col-span-1">
                      <span
                        className={`px-2 py-0.5 rounded text-xs font-semibold ${
                          vid.isRequired
                            ? 'bg-[#B4413C]/10 text-[#B4413C]'
                            : 'bg-[#EDF0F2] text-[#67717C]'
                        }`}
                      >
                        {vid.isRequired ? '필수' : '선택'}
                      </span>
                    </div>

                    {/* 제목 */}
                    <div className="col-span-5 pr-2">
                      <span className="font-semibold text-base text-[#14243A] block">
                        {vid.title}
                      </span>
                    </div>

                    {/* 영상 길이 */}
                    <div className="col-span-2 text-xs text-[#202832] tabular-nums font-medium">
                      {vid.duration}
                    </div>

                    {/* 시청 진행률 bar */}
                    <div className="col-span-2 pr-4 space-y-1">
                      <div className="flex justify-between text-[11px] text-[#67717C]">
                        <span>진척도</span>
                        <span className="tabular-nums font-semibold text-[#14243A]">
                          {vid.progressPercent}%
                        </span>
                      </div>
                      <div className="w-full h-2 bg-[#EDF0F2] rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all duration-300 ${
                            isFinished ? 'bg-[#2F7D5B]' : 'bg-[#426B93]'
                          }`}
                          style={{ width: `${vid.progressPercent}%` }}
                        />
                      </div>
                    </div>

                    {/* 이수 완료 / 미이수 */}
                    <div className="col-span-1 text-center">
                      <span
                        className={`px-2 py-0.5 rounded text-xs font-semibold ${
                          vid.isCompleted
                            ? 'bg-[#2F7D5B]/10 text-[#2F7D5B]'
                            : 'bg-[#EDF0F2] text-[#67717C]'
                        }`}
                      >
                        {videoStatusLabel}
                      </span>
                    </div>

                    {/* Action button: 시청하기 / 이어 보기 / 다시 보기 */}
                    <div className="col-span-1 text-center">
                      <button
                        onClick={() => openVideo(vid.id)}
                        className={`inline-flex items-center justify-center h-[36px] px-2.5 whitespace-nowrap rounded-lg text-xs font-semibold transition-colors ${
                          isFinished
                            ? 'border border-[#E1E5E8] bg-white text-[#14243A] hover:bg-[#F7F8F8]'
                            : hasStarted
                            ? 'bg-[#426B93] text-white hover:bg-[#345778]'
                            : 'bg-[#14243A] text-white hover:bg-[#1f3757]'
                        }`}
                      >
                        {isFinished ? '다시 보기' : hasStarted ? '이어 보기' : '시청하기'}
                      </button>
                    </div>
                  </div>

                  {/* Mobile Layout */}
                  <div className="min-[1100px]:hidden space-y-2">
                    <div className="flex items-center justify-between text-xs">
                      <span
                        className={`px-2 py-0.5 rounded font-semibold ${
                          vid.isRequired ? 'bg-[#B4413C]/10 text-[#B4413C]' : 'bg-[#EDF0F2] text-[#67717C]'
                        }`}
                      >
                        {vid.isRequired ? '필수' : '선택'}
                      </span>
                      <span
                        className={`px-2 py-0.5 rounded font-semibold ${
                          vid.isCompleted ? 'bg-[#2F7D5B]/10 text-[#2F7D5B]' : 'bg-[#EDF0F2] text-[#67717C]'
                        }`}
                      >
                        {videoStatusLabel}
                      </span>
                    </div>

                    <h3 className="font-semibold text-base text-[#14243A]">{vid.title}</h3>
                    <p className="text-xs text-[#67717C]">길이: {vid.duration} · 진행률 {vid.progressPercent}%</p>

                    <div className="pt-2 border-t border-[#E1E5E8] flex justify-end">
                      <button
                        onClick={() => openVideo(vid.id)}
                        className="h-[38px] px-4 whitespace-nowrap rounded-lg bg-[#14243A] text-white text-xs font-semibold"
                      >
                        {isFinished ? '다시 보기' : hasStarted ? '이어 보기' : '시청하기'}
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {activeVideoId !== null && (
          <div className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4" onMouseDown={(event) => { if (event.target === event.currentTarget) closeVideo(); }}>
            <div role="dialog" aria-modal="true" aria-label="온라인 직무 교육" className="bg-[#171C23] text-white rounded-2xl max-w-xl w-full p-6 space-y-4 shadow-2xl max-h-[90vh] overflow-y-auto">
              <div className="flex items-center justify-between pb-2 border-b border-white/10">
                <div className="flex items-center gap-2">
                  <Video className="w-5 h-5 text-[#426B93]" />
                  <span className="text-xs font-semibold text-white/70">온라인 직무 교육</span>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setShowWatchGuide((prev) => !prev)}
                    aria-label="시청 안내 보기"
                    title="시청 안내"
                    aria-controls="education-watch-guide"
                    aria-expanded={showWatchGuide}
                    className="rounded-full p-1 text-white/70 transition-colors hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#C7DAEC]"
                  >
                    <Info className="w-5 h-5" aria-hidden="true" />
                  </button>
                  <button type="button" onClick={closeVideo} className="p-1 text-white/70 hover:text-white" aria-label="영상 닫기">
                    <X className="w-5 h-5" />
                  </button>
                </div>
              </div>
              {showWatchGuide && (
                <section id="education-watch-guide" aria-label="시청 안내" className="rounded-lg border border-white/15 bg-white/[0.06] px-4 py-3 text-xs leading-5 text-white/80">
                  <p className="mb-2 font-semibold text-white">시청 안내</p>
                  <ul className="list-disc space-y-1.5 pl-4">
                    {RULES.map((rule) => <li key={rule}>{rule}</li>)}
                  </ul>
                </section>
              )}
              {activeVideo ? (
                <EducationPlayer key={activeVideo.videoId} video={activeVideo} verified={verified} compact />
              ) : (
                <p className="text-sm text-white/70">{modalError ?? '영상을 불러오는 중입니다.'}</p>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
    </AppShell>
  );
}
