'use client';

import Link from 'next/link';
import { useParams, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
  Car,
  Check,
  CheckCircle2,
  ChevronLeft,
  MapPin,
  MessageSquare,
  RefreshCw,
} from 'lucide-react';
import { AppShell } from '@/components/layout';
import ManagerGradeCharacter from '@/components/ui/ManagerGradeCharacter';
import { useActionDialog } from '@/components/ui/useActionDialog';
import { PROGRESS_ORDER, fetchProgress } from '@/features/application';
import { useRequireAuth } from '@/features/auth';
import { useChatWidget } from '@/features/chat';
import { LiveMap, useEscortLocation } from '@/features/location';
import { fetchPendingPayment, type PaymentDto } from '@/features/payment';
import { buildKakaoTCallUrl } from '@/lib/kakaoT';
import { completeEscort, fetchClientEscortCase } from '../api';
import type { ClientEscortCase } from '../types';

const CARD = 'rounded-2xl border border-[#E1E5E8] bg-white shadow-sm';
const MAIN_BUTTON = 'flex h-[48px] w-full items-center justify-center gap-2 rounded-[10px] bg-[#14243A] px-4 text-sm font-semibold text-white transition-colors hover:bg-[#1F3757] disabled:cursor-not-allowed disabled:bg-[#EDF0F2] disabled:text-[#67717C]';
const OUTLINE_BUTTON = 'flex h-[45px] w-full items-center justify-center gap-2 rounded-[10px] border border-[#E1E5E8] bg-white px-4 text-sm font-semibold text-[#14243A] transition-colors hover:bg-[#F7F8F8]';
const GRADE_NAMES = { SEED: '씨앗', SPROUT: '새싹', FLOWER: '꽃', EGGPLANT: '가지' } as const;
const GRADE_COLORS = { SEED: 'text-[#476A50]', SPROUT: 'text-[#486D51]', FLOWER: 'text-[#5E4E70]', EGGPLANT: 'text-[#3E3452]' } as const;

const STAGE_SENTENCES = [
  '동행 매니저와의 매칭이 완료되었습니다.',
  '동행 매니저가 출발했습니다.',
  '동행 매니저가 병원으로 이동 중입니다.',
  '동행 매니저가 병원에 도착했습니다.',
  '진료를 마치고 귀가 중입니다.',
  '동행 매니저가 귀가 완료를 기록했습니다.',
];

function currentProgressIndex(escort: ClientEscortCase) {
  return escort.timeline.reduce((current, step, index) => (step.done ? index : current), 0);
}

function StageProgress({ escort }: { escort: ClientEscortCase }) {
  const current = currentProgressIndex(escort);
  return (
    <section aria-label="동행 진행 단계" className={`${CARD} overflow-x-auto p-6`}>
      <div className="relative min-w-[650px]">
        <div aria-hidden="true" className="absolute inset-x-14 top-4 h-[2px] bg-[#E1E5E8]" />
        <div aria-hidden="true" className="absolute inset-x-14 top-4 h-[2px] origin-left bg-[#426B93] transition-transform duration-300" style={{ transform: `scaleX(${current / Math.max(escort.timeline.length - 1, 1)})` }} />
        <ol className="relative flex items-start justify-between">
          {escort.timeline.map((step, index) => {
            const past = index < current;
            const active = index === current;
            return (
              <li key={step.label} aria-current={active ? 'step' : undefined} className="relative z-10 flex w-[112px] flex-col items-center text-center">
                <span className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold ${past ? 'bg-[#426B93] text-white' : active ? 'bg-[#1E2739] text-white ring-4 ring-[#C7DAEC]' : 'bg-[#EDF0F2] text-[#67717C]'}`}>
                  {past ? <Check className="h-4 w-4" aria-hidden="true" /> : index + 1}
                </span>
                <span className={`mt-3 whitespace-nowrap text-xs ${active ? 'font-bold text-[#14243A]' : past ? 'font-semibold text-[#202832]' : 'font-medium text-[#67717C]'}`}>
                  {step.label}
                </span>
              </li>
            );
          })}
        </ol>
      </div>
    </section>
  );
}

function ProgressTimeline({ escort }: { escort: ClientEscortCase }) {
  const current = currentProgressIndex(escort);
  return (
    <section className={`${CARD} p-6`}>
      <h2 className="mb-5 text-base font-semibold text-[#14243A]">동행 타임라인</h2>
      <ol className="ml-2">
        {escort.timeline.map((step, index) => (
          <li key={step.label} aria-current={index === current ? 'step' : undefined} className="relative pb-5 pl-8 last:pb-0">
            {index < escort.timeline.length - 1 && (
              <span aria-hidden="true" className={`absolute -bottom-[10px] left-[5px] top-[10px] w-[2px] ${escort.timeline[index + 1].done ? 'bg-[#426B93]' : 'bg-[#E1E5E8]'}`} />
            )}
            <span aria-hidden="true" className={`absolute left-0 top-1 h-3 w-3 rounded-full border-2 ${index === current ? 'border-[#1E2739] bg-[#1E2739] ring-[3px] ring-[#C7DAEC]' : step.done ? 'border-[#426B93] bg-[#426B93]' : 'border-[#E1E5E8] bg-white'}`} />
            <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:gap-3">
              <span className={`min-w-[94px] text-xs font-semibold ${index === current ? 'font-bold text-[#1E2739]' : step.done ? 'text-[#14243A]' : 'text-[#67717C]'}`}>{step.label}</span>
              <span className={`min-w-0 flex-1 text-xs leading-5 ${step.done ? 'text-[#202832]' : 'text-[#67717C]'}`}>{step.description}</span>
              {step.time && <time className="shrink-0 text-xs text-[#67717C]">{step.time}</time>}
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}

function LiveLocationCard({ escort, applicationId }: { escort: ClientEscortCase; applicationId: number }) {
  const ongoing = !escort.postCompleted && (escort.stage === 'ongoing' || escort.stage === 'arrived' || escort.stage === 'finishing');
  const { location, trail, error } = useEscortLocation(applicationId, ongoing);
  const [mapFailed, setMapFailed] = useState(false);
  const showMap = ongoing && !!location && !mapFailed && !error;
  const statusText = !ongoing
    ? escort.stage === 'ready' ? '위치 공유 대기 중' : '위치 공유 종료'
    : error ? '위치 확인 필요' : location ? '실시간 위치 공유 중' : '위치 확인 중';
  return (
    <section className={`${CARD} overflow-hidden`}>
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#E1E5E8] bg-[#F7F8F8] px-6 py-3.5 text-xs">
        <span className="flex items-center gap-2 font-semibold text-[#202832]">
          <span aria-hidden="true" className={`h-2 w-2 rounded-full ${showMap ? 'bg-[#2F7D5B]' : 'bg-[#67717C]'}`} />
          {statusText}
        </span>
        {location?.updatedAtLabel && <span className="text-[#67717C]">최근 업데이트 {location.updatedAtLabel}</span>}
      </div>
      <div className={`relative flex items-center justify-center overflow-hidden ${showMap ? 'aspect-[16/10] bg-[#F2F5F7]' : 'min-h-[230px] bg-[#F2F5F7] sm:min-h-[260px]'}`}>
        {showMap ? (
          <LiveMap position={location.position} trail={trail} onError={() => setMapFailed(true)} />
        ) : (
          <div className="flex max-w-sm flex-col items-center gap-3 px-6 py-8 text-center">
            <MapPin className="h-8 w-8 text-[#8197AC]" aria-hidden="true" />
            <p className="text-sm font-semibold text-[#14243A]">{ongoing ? error || mapFailed ? '위치 정보를 표시할 수 없습니다.' : '매니저 위치를 기다리고 있습니다.' : statusText}</p>
            <p className="text-xs leading-5 text-[#67717C]">
              {ongoing
                ? error || (mapFailed ? '지도를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.' : '매니저의 위치가 전송되면 실제 지도가 표시됩니다.')
                : escort.stage === 'ready' ? '동행이 시작되면 매니저가 공유한 위치를 이곳에서 확인할 수 있습니다.' : '동행이 종료되어 위치 공유가 중지되었습니다.'}
            </p>
          </div>
        )}
      </div>
      <div className="flex flex-col gap-1 px-5 py-4 text-xs text-[#67717C] sm:flex-row sm:items-center sm:justify-between">
        <span>출발지: {escort.meetingPlace}</span>
        <span className="font-semibold text-[#14243A]">목적지: {escort.hospitalName}</span>
      </div>
    </section>
  );
}

function SummaryCard({ escort }: { escort: ClientEscortCase }) {
  const status = escort.postCompleted ? '동행 완료' : escort.stage === 'ready' ? '매칭 완료' : escort.stage === 'done' ? '완료 확인 대기' : '진행 중';
  const rows = [
    { label: '예상 시작 시간', value: escort.startAt },
    { label: '예상 종료 시간', value: escort.endAt },
    { label: '예상 소요 시간', value: escort.durationLabel },
    { label: '이동수단', value: escort.transport },
    ...(escort.stage === 'ready' ? [{ label: '만날 장소', value: escort.meetingPlace }] : []),
    ...(escort.confirmedEndAt ? [{ label: '종료 확정 시간', value: escort.confirmedEndAt }] : []),
  ];
  return (
    <section className={`${CARD} space-y-4 p-6`}>
      <div className="flex items-center justify-between gap-3 border-b border-[#E1E5E8] pb-3">
        <h2 className="text-base font-semibold text-[#14243A]">진행 요약</h2>
        <span className="rounded-md bg-[#EDF0F2] px-2.5 py-1 text-xs font-semibold text-[#426B93]">{status}</span>
      </div>
      <dl className="space-y-3 text-xs">
        {rows.map((row) => (
          <div key={row.label} className="flex justify-between gap-4">
            <dt className="shrink-0 text-[#67717C]">{row.label}</dt>
            <dd className="text-right font-semibold leading-5 text-[#14243A]">{row.value}</dd>
          </div>
        ))}
      </dl>
      {escort.postCompleted && (
        <p className="border-t border-[#E1E5E8] pt-3 text-xs leading-5 text-[#67717C]">동행이 정상적으로 완료되었습니다.{!escort.reviewed && ' 매니저에 대한 리뷰를 작성할 수 있습니다.'}</p>
      )}
    </section>
  );
}

function MatchedManagerCard({ escort }: { escort: ClientEscortCase }) {
  const { openChatWith } = useChatWidget();
  const { manager } = escort;
  return (
    <section className={`${CARD} space-y-4 p-6`}>
      <h2 className="text-xs font-semibold tracking-wide text-[#67717C]">배정된 동행 매니저</h2>
      <div className="flex items-center gap-3.5">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-[#14243A] text-base font-bold text-white" aria-hidden="true">{manager.name.slice(0, 1)}</div>
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-semibold text-[#14243A]">{manager.name} 매니저</h3>
            {manager.grade && (
              <span className="inline-flex items-center gap-1">
                <ManagerGradeCharacter grade={manager.grade} bare className="h-6 w-6 shrink-0" />
                <span className={`text-xs font-semibold ${GRADE_COLORS[manager.grade]}`}>{GRADE_NAMES[manager.grade]}</span>
              </span>
            )}
          </div>
          <p className="mt-1 text-xs text-[#67717C]">완료 동행 {manager.completedCount}건 · {manager.ratingCount ? `평점 ★ ${manager.rating.toFixed(1)} (${manager.ratingCount}건)` : '평가 없음'}</p>
        </div>
      </div>
      {manager.intro.some((item) => item.trim()) && <p className="rounded-lg bg-[#F7F8F8] px-3 py-3 text-xs leading-5 text-[#67717C]">{manager.intro.join(' ')}</p>}
      <button type="button" onClick={() => openChatWith(manager.userId)} className={OUTLINE_BUTTON}>
        <MessageSquare className="h-4 w-4 text-[#426B93]" aria-hidden="true" />
        안심 메시지
      </button>
    </section>
  );
}

export default function ClientTrackingPage() {
  const { confirm, dialog } = useActionDialog();
  const { loading: authLoading, user } = useRequireAuth('CLIENT');
  const params = useParams<{ applicationId: string }>();
  const searchParams = useSearchParams();
  const applicationId = Number(params.applicationId);
  const postIdValue = searchParams.get('postId');
  const postId = postIdValue && Number.isFinite(Number(postIdValue)) && Number(postIdValue) > 0 ? Number(postIdValue) : undefined;

  const [result, setResult] = useState<{ key?: string; escort?: ClientEscortCase; error?: string }>({});
  const [reloadKey, setReloadKey] = useState(0);
  const [completing, setCompleting] = useState(false);
  const [completeError, setCompleteError] = useState<string>();
  const [paymentResult, setPaymentResult] = useState<{ key?: number; data: PaymentDto | null }>({ data: null });

  const resultKey = `${postId ?? 'missing'}:${applicationId}`;
  useEffect(() => {
    if (postId === undefined || !Number.isInteger(applicationId) || applicationId < 1 || !user) return;
    let cancelled = false;
    fetchClientEscortCase(postId, applicationId)
      .then((escort) => { if (!cancelled) setResult({ key: resultKey, escort }); })
      .catch((cause: unknown) => {
        if (!cancelled) setResult({ key: resultKey, error: cause instanceof Error ? cause.message : '동행 현황을 불러오지 못했습니다.' });
      });
    return () => { cancelled = true; };
  }, [postId, applicationId, resultKey, reloadKey, user]);

  const escort = result.key === resultKey ? result.escort : undefined;
  const completed = !!escort?.postCompleted;
  useEffect(() => {
    if (postId === undefined || !completed) return;
    let cancelled = false;
    fetchPendingPayment(postId)
      .then((payment) => { if (!cancelled) setPaymentResult({ key: postId, data: payment }); })
      .catch(() => { if (!cancelled) setPaymentResult({ key: postId, data: null }); });
    return () => { cancelled = true; };
  }, [postId, completed, reloadKey]);

  // 진행 단계는 읽기 전용입니다. 매니저가 서버에 기록한 단계가 바뀌면 최신 현황을 다시 불러옵니다.
  const progressIndex = escort ? currentProgressIndex(escort) : -1;
  useEffect(() => {
    if (!escort || escort.postCompleted) return;
    let cancelled = false;
    let requesting = false;
    const poll = async () => {
      if (cancelled || requesting || document.visibilityState === 'hidden') return;
      requesting = true;
      try {
        const next = await fetchProgress(applicationId);
        if (!cancelled && PROGRESS_ORDER.indexOf(next.progress) !== progressIndex) {
          setReloadKey((key) => key + 1);
        }
      } catch {
        // 일시적인 진행 상태 조회 실패 시 현재 정보를 유지합니다. 새로고침으로 재시도할 수 있습니다.
      } finally { requesting = false; }
    };
    const timer = window.setInterval(poll, 15000);
    return () => { cancelled = true; window.clearInterval(timer); };
  }, [escort, applicationId, progressIndex]);

  const pendingPayment = completed && paymentResult.key === postId ? paymentResult.data : null;
  const reload = () => { setCompleteError(undefined); setResult({}); setReloadKey((key) => key + 1); };
  const handleComplete = async () => {
    if (postId === undefined || !escort || escort.stage !== 'done' || escort.postCompleted) return;
    if (!await confirm({ title: '동행을 완료 처리하시겠습니까?', description: '실제 동행 시간을 기준으로 정산·결제가 진행됩니다.', confirmLabel: '완료 처리하기' })) return;
    setCompleting(true);
    setCompleteError(undefined);
    try { await completeEscort(postId); reload(); }
    catch (cause: unknown) { setCompleteError(cause instanceof Error ? cause.message : '동행 완료 처리에 실패했습니다.'); }
    finally { setCompleting(false); }
  };

  let content;
  if (authLoading) {
    content = <p className="py-24 text-center text-sm font-medium text-[#67717C]">로그인 상태를 확인하는 중입니다.</p>;
  } else if (!user) {
    return null;
  } else if (result.key !== resultKey && postId !== undefined && Number.isInteger(applicationId) && applicationId > 0) {
    content = <p className="py-24 text-center text-sm font-medium text-[#67717C]">동행 현황을 불러오는 중입니다.</p>;
  } else if (result.key === resultKey && result.error) {
    content = <div role="alert" className="py-20 text-center"><p className="mb-4 text-sm text-[#99433E]">{result.error}</p><button onClick={reload} className={OUTLINE_BUTTON}>다시 시도</button></div>;
  } else if (!escort || !postId) {
    content = <p className="py-24 text-center text-sm font-medium text-[#67717C]">동행 정보를 찾을 수 없습니다. 내 공고 목록에서 다시 선택해 주세요.</p>;
  } else {
    const extraPaymentHref = pendingPayment ? `/client/posts/new/payment?${new URLSearchParams({ postId: String(escort.postId), paymentId: String(pendingPayment.id), pay: String(pendingPayment.hourlyPaySnapshot), flow: 'extra' })}` : '';
    const goingHome = escort.stage === 'arrived' || escort.stage === 'finishing';
    const [from, to] = goingHome ? [escort.hospitalPoint, escort.pickupPoint] : [escort.pickupPoint, escort.hospitalPoint];
    const rideSelect = escort.rideSelect?.[goingHome ? 'TO_HOME' : 'TO_HOSPITAL'] ?? null;
    const kakaoT = !escort.postCompleted && escort.stage !== 'done' && from && to
      ? { href: buildKakaoTCallUrl(from, to), disabled: rideSelect !== null && rideSelect !== 'TAXI' }
      : undefined;
    content = (
      <>
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-3xl font-semibold tracking-tight text-[#14243A] sm:text-4xl">동행 현황</h1>
            <p className="mt-2 text-sm font-medium text-[#67717C] sm:text-base">{STAGE_SENTENCES[currentProgressIndex(escort)]}</p>
          </div>
          <button type="button" onClick={reload} className="inline-flex items-center gap-1.5 rounded-lg px-2 py-2 text-xs font-semibold text-[#67717C] transition-colors hover:bg-[#EDF0F2] hover:text-[#14243A]">
            <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" /> 새로고침
          </button>
        </div>

        <StageProgress escort={escort} />
        <div className="grid grid-cols-1 items-start gap-8 lg:grid-cols-12">
          <div className="space-y-6 lg:col-span-7">
            <LiveLocationCard escort={escort} applicationId={applicationId} />
            <ProgressTimeline escort={escort} />
          </div>
          <div className="space-y-6 lg:sticky lg:top-28 lg:col-span-5">
            <SummaryCard escort={escort} />
            <MatchedManagerCard escort={escort} />
            <div className="space-y-3">
              {kakaoT && (kakaoT.disabled ? (
                <button type="button" disabled className={`${OUTLINE_BUTTON} cursor-not-allowed opacity-60`}><Car className="h-4 w-4" />카카오 T 호출 · 택시 이동이 아닙니다</button>
              ) : (
                <a href={kakaoT.href} target="_blank" rel="noreferrer" className={OUTLINE_BUTTON}><Car className="h-4 w-4 text-[#9A6B12]" />카카오 T 호출</a>
              ))}
              {!escort.postCompleted && (
                <>
                  <button type="button" onClick={handleComplete} disabled={completing || escort.stage !== 'done'} className={MAIN_BUTTON}>
                    <CheckCircle2 className="h-4 w-4" aria-hidden="true" />{completing ? '처리 중...' : '동행 완료 확인'}
                  </button>
                  {escort.stage !== 'done' && <p className="text-center text-[11px] text-[#67717C]">매니저가 귀가 완료를 기록한 후 최종 확인할 수 있습니다.</p>}
                </>
              )}
              {completeError && <p role="alert" className="text-sm text-[#99433E]">{completeError}</p>}
              {pendingPayment && (
                <Link href={extraPaymentHref} className={MAIN_BUTTON}>{pendingPayment.amount.toLocaleString()}원 추가 결제</Link>
              )}
              {escort.postCompleted && (
                <>
                  {escort.reviewed ? (
                    <Link href={`/client/escort/${escort.applicationId}/review?postId=${escort.postId}`} className={OUTLINE_BUTTON}>리뷰 보기</Link>
                  ) : (
                    <Link href={`/client/escort/${escort.applicationId}/review?postId=${escort.postId}`} className={MAIN_BUTTON}>리뷰 작성</Link>
                  )}
                  <Link href={`/client/escort/${escort.applicationId}/report?postId=${escort.postId}`} className={OUTLINE_BUTTON}>보고서 조회</Link>
                </>
              )}
            </div>
          </div>
        </div>
      </>
    );
  }

  return (
    <AppShell>
      <section className="w-full bg-[#F7F8F8] py-12 lg:py-16">
        <div className="mx-auto max-w-[1240px] space-y-8 px-5 sm:px-8 lg:px-16">
          <Link href="/client/posts" className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] transition-colors hover:text-[#14243A]">
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />내 공고 목록으로 돌아가기
          </Link>
          {content}
        </div>
      </section>
      {dialog}
    </AppShell>
  );
}
