'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
  Phone,
  CheckCircle2,
  Check,
  ChevronLeft,
  FileText,
  ArrowRight,
  RefreshCw,
} from 'lucide-react';
import { AppShell } from '@/components/layout';
import PageFeedback from '@/components/ui/PageFeedback';
import { PROGRESS_ORDER, advanceProgress, fetchProgress } from '@/features/application';
import { useRequireAuth } from '@/features/auth';
import { useChatWidget } from '@/features/chat';
import { LiveMap, useShareLocation } from '@/features/location';
import { fetchReport } from '@/features/report';
import { fetchTrackingCase } from '../api';
import { TIMELINE_BASE } from '../model/stage';
import type { TrackingCase } from '../types';

type CaseResult =
  | { status: 'notFound' }
  | { status: 'error'; message: string }
  | { status: 'ready'; escort: TrackingCase };

const STAGES = [
  { title: '동행 시작 전', description: '매니저와 배정 완료 후 사전 통화가 연결되었습니다.' },
  { title: '출발', description: '매니저가 출발했습니다.' },
  { title: '병원 이동 중', description: '동행 매니저와 안전하게 병원으로 이동 중입니다.' },
  { title: '병원 도착', description: '병원에 도착하여 접수를 도와주고 있습니다.' },
  { title: '귀가 중', description: '진료를 마치고 안전하게 귀가를 돕고 있습니다.' },
  { title: '귀가 완료', description: '동행이 완료되었습니다.' },
] as const;

export default function TrackingPage() {
  const { loading: authLoading, user } = useRequireAuth('ESCORT');
  const { openChatWith } = useChatWidget();
  const params = useParams<{ applicationId: string }>();
  const applicationId = Number(params.applicationId);

  const [result, setResult] = useState<{ key?: number; data?: CaseResult }>({});
  const [doneCount, setDoneCount] = useState<number>();
  const [advancing, setAdvancing] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [progressError, setProgressError] = useState<string>();
  const [hasReport, setHasReport] = useState(false);
  const [mapFailed, setMapFailed] = useState(false);
  const [isLocationSharing, setIsLocationSharing] = useState(true);
  const [showAdvanceConfirm, setShowAdvanceConfirm] = useState(false);
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const authorized = !!user;

  useEffect(() => {
    if (!Number.isSafeInteger(applicationId) || applicationId <= 0 || !authorized) return;
    let cancelled = false;
    fetchTrackingCase(applicationId)
      .then((escort) => {
        if (cancelled) return;
        setResult({ key: applicationId, data: escort ? { status: 'ready', escort } : { status: 'notFound' } });
        if (escort) setDoneCount(escort.doneCount);
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setResult({
          key: applicationId,
          data: { status: 'error', message: error instanceof Error ? error.message : '동행 정보를 불러오지 못했습니다.' },
        });
      });
    fetchReport(applicationId)
      .then(() => { if (!cancelled) setHasReport(true); })
      .catch(() => { if (!cancelled) setHasReport(false); });
    return () => { cancelled = true; };
  }, [applicationId, authorized]);

  useEffect(() => {
    if (!toastMsg) return;
    const timer = window.setTimeout(() => setToastMsg(null), 3000);
    return () => window.clearTimeout(timer);
  }, [toastMsg]);

  // 위치가 병원·집 반경에 들어오면 서버가 진행 단계를 자동으로 바꿉니다. 화면이 알 수 있도록 주기적으로 다시 읽습니다.
  // 버튼으로 바꾸는 중이거나 새로고침 중일 때, 화면이 가려져 있을 때는 건너뜁니다.
  useEffect(() => {
    if (!authorized || doneCount === undefined || doneCount >= PROGRESS_ORDER.length) return;
    if (advancing || refreshing) return;
    let cancelled = false;
    let requesting = false;
    const poll = async () => {
      if (cancelled || requesting || document.visibilityState === 'hidden') return;
      requesting = true;
      try {
        const latest = await fetchProgress(applicationId);
        const index = PROGRESS_ORDER.indexOf(latest.progress);
        if (!cancelled && index >= 0) setDoneCount((prev) => (prev === index + 1 ? prev : index + 1));
      } catch {
        // 일시적인 조회 실패는 현재 화면을 유지합니다. 다음 주기에 다시 시도합니다.
      } finally { requesting = false; }
    };
    const timer = window.setInterval(poll, 10000);
    return () => { cancelled = true; window.clearInterval(timer); };
  }, [applicationId, authorized, doneCount, advancing, refreshing]);

  const state = result.key === applicationId ? result.data : undefined;
  const sharingLocation =
    state?.status === 'ready' &&
    doneCount !== undefined && doneCount >= 2 && doneCount < PROGRESS_ORDER.length && isLocationSharing;
  const share = useShareLocation(applicationId, sharingLocation);

  if (authLoading || (!state && user)) {
    return (
      <AppShell>
        <PageFeedback kind="loading" title="동행 정보를 불러오는 중입니다." />
      </AppShell>
    );
  }
  if (!user) return null;

  if (state?.status !== 'ready' || doneCount === undefined) {
    return (
      <AppShell>
        <PageFeedback
          kind={state?.status === 'error' ? 'error' : 'empty'}
          title={state?.status === 'error' ? '동행 현황을 불러오지 못했습니다.' : '동행 정보를 찾을 수 없습니다.'}
          description={state?.status === 'error' ? state.message : undefined}
          href="/mypage/applications"
          action="신청 목록으로 돌아가기"
        />
      </AppShell>
    );
  }

  const escort = state.escort;
  const currentIndex = Math.min(Math.max(doneCount - 1, 0), STAGES.length - 1);
  const currentStageInfo = STAGES[currentIndex];
  const nextStageIndex = currentIndex < STAGES.length - 1 ? currentIndex + 1 : null;
  const nextStageInfo = nextStageIndex !== null ? STAGES[nextStageIndex] : null;
  const finished = doneCount >= PROGRESS_ORDER.length;
  const arrivedAtHospital = doneCount > PROGRESS_ORDER.indexOf('AT_HOSPITAL');
  const sharingAvailable = doneCount >= 2 && !finished;
  const contactPhone = escort.emergencyContactPhone.trim();
  const canCall = /^[+\d\-()\s]+$/.test(contactPhone) && /\d/.test(contactPhone);

  const handleRefreshProgress = async () => {
    if (refreshing || advancing) return;
    setRefreshing(true);
    setProgressError(undefined);
    try {
      const latest = await fetchProgress(escort.applicationId);
      const index = PROGRESS_ORDER.indexOf(latest.progress);
      if (index < 0) throw new Error('진행 상태를 확인할 수 없습니다.');
      setDoneCount(index + 1);
    } catch (error) {
      setProgressError(error instanceof Error ? error.message : '진행 상태를 새로고침하지 못했습니다.');
    } finally {
      setRefreshing(false);
    }
  };

  const handleConfirmAdvance = async () => {
    if (finished || !nextStageInfo || advancing) return;
    setAdvancing(true);
    setProgressError(undefined);
    try {
      await advanceProgress(escort.applicationId, PROGRESS_ORDER[currentIndex]);
      // 단계 변경은 서버가 승인한 경우에만 반영합니다.
      const next = await fetchProgress(escort.applicationId).catch(() => null);
      const newDoneCount = next ? PROGRESS_ORDER.indexOf(next.progress) + 1 : doneCount + 1;
      setDoneCount(newDoneCount);
      setToastMsg(`진행 상태가 '${STAGES[newDoneCount - 1]?.title ?? nextStageInfo.title}'(으)로 변경되었습니다.`);
    } catch (error) {
      setProgressError(error instanceof Error ? error.message : '진행 상태 변경에 실패했습니다.');
    } finally {
      setAdvancing(false);
      setShowAdvanceConfirm(false);
    }
  };

  return (
    <AppShell>
      <div className="w-full bg-[#F7F8F8] py-12 lg:py-16">
        <div className="max-w-[1240px] mx-auto px-5 sm:px-8 lg:px-16 space-y-8">
          <Link
            href="/mypage/applications"
            className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] hover:text-[#14243A] sc-focus"
          >
            <ChevronLeft className="w-4 h-4" />
            <span>신청 목록으로 돌아가기</span>
          </Link>

          {toastMsg && (
            <div role="status" className="fixed bottom-6 right-6 z-50 bg-[#14243A] text-white px-5 py-3.5 rounded-xl shadow-lg border border-white/20 text-sm font-medium flex items-center gap-2">
              <CheckCircle2 className="w-5 h-5 text-[#2F7D5B]" />
              <span>{toastMsg}</span>
            </div>
          )}

          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h1 className="text-3xl font-semibold tracking-tight text-[#14243A] sm:text-4xl">동행 현황</h1>
              <p className="mt-2 text-sm font-medium text-[#67717C] sm:text-base">{currentStageInfo.description}</p>
            </div>
            <button
              type="button"
              onClick={() => void handleRefreshProgress()}
              disabled={refreshing || advancing}
              className="inline-flex items-center gap-1.5 rounded-lg px-2 py-2 text-xs font-semibold text-[#67717C] transition-colors hover:bg-[#EDF0F2] hover:text-[#14243A] disabled:opacity-60"
            >
              <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" /> 새로고침
            </button>
          </div>

          <section aria-label="동행 진행 단계" className="rounded-2xl border border-[#E1E5E8] bg-white p-6 shadow-sm overflow-x-auto">
            <div className="relative min-w-[650px]">
              <div aria-hidden="true" className="absolute inset-x-14 top-4 h-[2px] bg-[#E1E5E8]" />
              <div aria-hidden="true" className="absolute inset-x-14 top-4 h-[2px] origin-left bg-[#426B93] transition-transform duration-300" style={{ transform: `scaleX(${currentIndex / Math.max(STAGES.length - 1, 1)})` }} />
              <ol className="relative flex items-start justify-between">
                {STAGES.map((st, index) => {
                  const past = index < currentIndex;
                  const active = index === currentIndex;
                  return (
                    <li key={st.title} aria-current={active ? 'step' : undefined} className="relative z-10 flex w-[112px] flex-col items-center text-center">
                      <span className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold ${past ? 'bg-[#426B93] text-white' : active ? 'bg-[#1E2739] text-white ring-4 ring-[#C7DAEC]' : 'bg-[#EDF0F2] text-[#67717C]'}`}>
                        {past ? <Check className="h-4 w-4" aria-hidden="true" /> : index + 1}
                      </span>
                      <span className={`mt-3 whitespace-nowrap text-xs ${active ? 'font-bold text-[#14243A]' : past ? 'font-semibold text-[#202832]' : 'font-medium text-[#67717C]'}`}>
                        {st.title}
                      </span>
                    </li>
                  );
                })}
              </ol>
            </div>
          </section>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            <div className="lg:col-span-7 space-y-6">
              <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs space-y-6">
                <div className="flex items-center justify-between pb-4 border-b border-[#E1E5E8]">
                  <div>
                    <span className="text-xs font-semibold text-[#67717C] block">현재 진행 단계</span>
                    <h2 className="text-2xl font-bold text-[#14243A] mt-1">{currentStageInfo.title}</h2>
                  </div>
                  <div className="text-right">
                    <button
                      type="button"
                      disabled={!sharingAvailable}
                      onClick={() => setIsLocationSharing((prev) => !prev)}
                      className={`px-3 py-1 rounded-full text-xs font-semibold border transition-colors disabled:cursor-not-allowed ${
                        sharingLocation && share.status === 'sharing'
                          ? 'bg-[#2F7D5B]/10 text-[#2F7D5B] border-[#2F7D5B]/20'
                          : 'bg-[#EDF0F2] text-[#67717C] border-[#E1E5E8]'
                      }`}
                    >
                      {!sharingAvailable
                        ? finished ? '○ 공유 종료' : '○ 공유 대기'
                        : !isLocationSharing
                          ? '○ 일시 정지됨'
                          : share.status === 'sharing' ? '● 위치 공유 중' : '○ 위치 확인 중'}
                    </button>
                  </div>
                </div>

                {sharingLocation && share.message && (
                  <p role="alert" className="text-xs text-[#B34035]">{share.message}</p>
                )}

                <div className="bg-[#EDF0F2] p-4 rounded-xl border border-[#E1E5E8] text-sm text-[#202832]">
                  <span className="font-semibold text-[#14243A] block mb-0.5">현재 단계 안내:</span>
                  &quot;{currentStageInfo.description}&quot;
                </div>

                {progressError && <p role="alert" className="text-sm font-medium text-[#B34035]">{progressError}</p>}
                <div className="space-y-3">
                  {nextStageInfo ? (
                    <button
                      type="button"
                      onClick={() => setShowAdvanceConfirm(true)}
                      disabled={advancing}
                      className="group w-full h-[56px] rounded-xl bg-[#14243A] text-white text-base font-semibold hover:bg-[#1f3757] transition-colors sc-focus shadow-md flex items-center justify-center gap-2 disabled:opacity-60"
                    >
                      <span>다음 단계로 변경: &apos;{nextStageInfo.title}&apos;</span>
                      <ArrowRight className="w-5 h-5 transition-transform duration-200 group-hover:translate-x-1 group-focus-visible:translate-x-1" aria-hidden="true" />
                    </button>
                  ) : (
                    <div className="space-y-3">
                      <div className="w-full h-[56px] rounded-xl bg-[#2F7D5B]/10 border border-[#2F7D5B]/20 text-[#2F7D5B] font-semibold flex items-center justify-center gap-2">
                        <CheckCircle2 className="w-5 h-5" />
                        <span>귀가 완료되었습니다. 수고하셨습니다!</span>
                      </div>
                    </div>
                  )}
                  {arrivedAtHospital && (
                    <Link
                      href={`/escort/${escort.applicationId}/report${hasReport ? '' : '/new'}`}
                      className="w-full h-[52px] rounded-xl bg-[#426B93] text-white text-sm font-semibold hover:bg-[#345778] transition-colors flex items-center justify-center gap-2"
                    >
                      <FileText className="w-4 h-4" />
                      <span>{hasReport ? '진료 보고서 조회하기' : '진료 보고서 작성하기'}</span>
                    </Link>
                  )}
                  {!finished && (
                    <button
                      type="button"
                      onClick={() => openChatWith(escort.clientId)}
                      className="w-full h-[44px] rounded-lg border border-[#E1E5E8] text-xs font-semibold text-[#14243A] hover:bg-[#F7F8F8] transition-colors"
                    >
                      의뢰인 연락하기
                    </button>
                  )}
                </div>
              </div>

              <div className="bg-[#F7F8F8] rounded-2xl border border-[#E1E5E8] p-6 text-xs space-y-3">
                <span className="font-semibold text-[#14243A] block">동행 목적지 정보</span>
                <div className="flex justify-between gap-4">
                  <span className="text-[#67717C] shrink-0">목적지 병원</span>
                  <span className="font-semibold text-[#14243A] text-right">{escort.hospitalName}</span>
                </div>
                <div className="flex justify-between gap-4">
                  <span className="text-[#67717C] shrink-0">병원 주소</span>
                  <span className="text-[#202832] text-right">{escort.hospitalAddress}</span>
                </div>
                <div className="flex justify-between gap-4">
                  <span className="text-[#67717C] shrink-0">이동 교통수단</span>
                  <span className="text-[#202832] text-right">{escort.transport}</span>
                </div>
                <div className="flex justify-between gap-4">
                  <span className="text-[#67717C] shrink-0">동행 일시</span>
                  <span className="text-[#202832] text-right">{escort.startAt}</span>
                </div>
              </div>

              <section className="bg-white rounded-2xl border border-[#E1E5E8] p-6 shadow-xs space-y-4">
                <div className="flex items-center justify-between gap-3">
                  <h3 className="text-base font-semibold text-[#14243A]">실시간 위치</h3>
                  <span className="text-xs text-[#67717C]">
                    {!sharingAvailable ? (finished ? '위치 공유 종료' : '위치 공유 대기 중')
                      : !isLocationSharing ? '위치 공유 일시정지'
                      : share.status === 'sharing' ? '위치 공유 중' : '위치 확인 중'}
                  </span>
                </div>
                {sharingLocation && share.position && !mapFailed ? (
                  <div className="relative h-[280px] overflow-hidden rounded-xl bg-[#EDF0F2]">
                    <LiveMap position={share.position} trail={[]} onError={() => setMapFailed(true)} />
                  </div>
                ) : (
                  <div className="flex min-h-[160px] items-center justify-center rounded-xl bg-[#EDF0F2] px-6 text-center text-xs leading-5 text-[#67717C]">
                    {sharingAvailable && !isLocationSharing
                      ? '위치 전송이 일시정지되었습니다.'
                      : sharingLocation && share.message
                        ? share.message
                        : mapFailed ? '지도를 불러오지 못했습니다.'
                        : sharingAvailable ? '위치 권한을 허용하면 현재 위치가 표시됩니다.'
                          : finished ? '동행이 완료되어 위치 공유가 종료되었습니다.' : '출발 후 위치 공유가 시작됩니다.'}
                  </div>
                )}
                {share.lastSentLabel && sharingLocation && (
                  <p className="text-xs text-[#67717C]">최근 위치 전송: {share.lastSentLabel}</p>
                )}
              </section>
            </div>

            <div className="lg:col-span-5 space-y-6 lg:sticky lg:top-28">
              <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 shadow-xs space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-[#E1E5E8]">
                  <h3 className="text-base font-semibold text-[#14243A]">의뢰인 정보</h3>
                  <span className="text-xs px-2 py-0.5 rounded bg-[#2F7D5B]/10 text-[#2F7D5B] font-semibold">매칭 승인됨</span>
                </div>
                <div className="space-y-3 text-xs">
                  <div>
                    <span className="text-[#67717C] block mb-0.5">의뢰인</span>
                    <span className="font-semibold text-sm text-[#14243A]">{escort.clientName}</span>
                  </div>
                  <div>
                    <span className="text-[#67717C] block mb-0.5">의뢰인 연락처</span>
                    <span className="font-semibold text-sm text-[#14243A]">{escort.clientPhone}</span>
                  </div>
                  <div>
                    <span className="text-[#67717C] block mb-0.5">보호자</span>
                    <span className="font-semibold text-sm text-[#14243A]">{escort.emergencyContactName}</span>
                  </div>
                  <div>
                    <span className="text-[#67717C] block mb-0.5">보호자 직통 비상 연락처</span>
                    <div className="flex items-center justify-between gap-3">
                      <span className="font-semibold text-sm text-[#14243A]">{escort.emergencyContactPhone}</span>
                      {canCall && (
                        <a
                          href={`tel:${contactPhone}`}
                          className="h-[32px] px-3 rounded-lg bg-[#426B93] text-white text-xs font-semibold hover:bg-[#345778] transition-colors flex items-center gap-1"
                        >
                          <Phone className="w-3.5 h-3.5" />
                          <span>통화하기</span>
                        </a>
                      )}
                    </div>
                  </div>
                  <div className="pt-4">
                    <div className="border-t border-[#E1E5E8] pt-6 pb-1">
                      <span className="text-[#14243A] font-semibold block mb-2">의뢰인 특이사항 및 주의사항</span>
                      <p className="text-[#67717C] leading-relaxed bg-[#F7F8F8] p-3 rounded-lg border border-[#E1E5E8]">{escort.note}</p>
                    </div>
                  </div>
                </div>
              </div>

              <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 shadow-xs space-y-3 text-xs text-[#67717C]">
                <h3 className="font-semibold text-[#14243A] text-sm">동행 매니저 수칙 안내</h3>
                <ul className="space-y-2 leading-relaxed">
                  <li className="flex items-start gap-1.5"><span className="text-[#426B93] font-bold">•</span><span>어르신 보행 속도에 맞추어 보폭을 조절하고 낙상 위험 구역을 사전 점검하세요.</span></li>
                  <li className="flex items-start gap-1.5"><span className="text-[#426B93] font-bold">•</span><span>진료실 내에서는 주치의 설명과 복약 지도 내용을 메모장에 충실히 기록합니다.</span></li>
                  <li className="flex items-start gap-1.5"><span className="text-[#426B93] font-bold">•</span><span>귀가 완료 후 진료 보고서를 등록해 주세요.</span></li>
                </ul>
              </div>

              <section className="bg-white rounded-2xl border border-[#E1E5E8] p-6 shadow-xs space-y-4">
                <h3 className="font-semibold text-[#14243A] text-sm">동행 타임라인</h3>
                <ol className="space-y-0">
                  {TIMELINE_BASE.map((step, index) => {
                    const last = index === TIMELINE_BASE.length - 1;
                    const completed = index < doneCount;
                    const active = index === currentIndex;
                    return (
                      <li key={step.label} aria-current={active ? 'step' : undefined} className="relative flex gap-3 pb-4 last:pb-0">
                        {!last && <span aria-hidden="true" className={`absolute left-[6px] top-4 bottom-0 w-[2px] ${index + 1 < doneCount ? 'bg-[#426B93]' : 'bg-[#E1E5E8]'}`} />}
                        <span className={`relative z-10 mt-1 h-3.5 w-3.5 shrink-0 rounded-full border-2 ${active ? 'border-[#14243A] bg-[#14243A] ring-2 ring-[#426B93]/20' : completed ? 'border-[#426B93] bg-[#426B93]' : 'border-[#E1E5E8] bg-white'}`} />
                        <div className="flex flex-1 gap-3 text-xs leading-5">
                          <span className={`w-[90px] shrink-0 font-semibold ${active ? 'text-[#14243A]' : 'text-[#67717C]'}`}>{step.label}</span>
                          <span className="text-[#67717C]">{step.description}</span>
                        </div>
                      </li>
                    );
                  })}
                </ol>
              </section>
            </div>
          </div>

          {showAdvanceConfirm && nextStageInfo && (
            <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !advancing) setShowAdvanceConfirm(false); }}>
              <div role="dialog" aria-modal="true" aria-labelledby="advance-modal-title" className="bg-white rounded-2xl max-w-sm w-full p-6 text-center space-y-4 shadow-xl">
                <h3 id="advance-modal-title" className="text-lg font-semibold text-[#14243A]">진행 상태를 변경하시겠습니까?</h3>
                <p className="text-xs leading-[18px] text-[#67717C]">
                  <span className="block">&apos;{nextStageInfo.title}&apos;(으)로 진행 상태를 변경하시겠습니까?</span>
                  <span className="mt-0.5 block">변경된 단계는 서버에 기록되고 진행 알림이 전송됩니다.</span>
                </p>
                <div className="flex gap-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setShowAdvanceConfirm(false)}
                    disabled={advancing}
                    className="flex-1 h-[44px] rounded-lg border border-[#E1E5E8] text-xs font-semibold text-[#67717C] disabled:opacity-60"
                  >취소</button>
                  <button
                    type="button"
                    onClick={() => void handleConfirmAdvance()}
                    disabled={advancing}
                    className="flex-1 h-[44px] rounded-lg bg-[#14243A] text-white text-xs font-semibold hover:bg-[#1f3757] transition-colors disabled:opacity-60"
                  >{advancing ? '처리 중…' : '변경 확인'}</button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </AppShell>
  );
}
