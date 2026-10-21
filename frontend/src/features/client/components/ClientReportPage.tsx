'use client';

import Link from 'next/link';
import { useParams, useSearchParams } from 'next/navigation';
import { useEffect, useState, type ReactNode } from 'react';
import {
  CalendarDays,
  ChevronLeft,
  ClipboardList,
  FileText,
  MessageSquare,
  Printer,
  Sparkles,
  Star,
  UserRound,
} from 'lucide-react';
import { AppShell } from '@/components/layout';
import PageFeedback from '@/components/ui/PageFeedback';
import ManagerGradeCharacter from '@/components/ui/ManagerGradeCharacter';
import { useRequireAuth } from '@/features/auth';
import { useChatWidget } from '@/features/chat';
import { aiSummaryItems, fetchReport, isReportNotFoundError, parseAiSummary, type ReportDto } from '@/features/report';
import { fetchClientEscortCase } from '../api';
import type { ClientEscortCase } from '../types';

const SURFACE = 'rounded-2xl border border-[#E1E5E8] bg-white shadow-[0_1px_3px_rgba(25,33,61,0.04)]';
const SUB_SURFACE = 'rounded-xl border border-[#E1E5E8] bg-[#F7F8F8]';
const SECONDARY_BUTTON = 'inline-flex h-[44px] items-center justify-center gap-2 rounded-[10px] border border-[#E1E5E8] bg-white px-4 text-xs font-semibold text-[#14243A] transition-colors hover:bg-[#F7F8F8]';
const PRIMARY_BUTTON = 'inline-flex h-[46px] items-center justify-center gap-2 rounded-xl bg-[#14243A] px-4 text-xs font-semibold text-white transition-colors hover:bg-[#1F3757]';
const GRADE_LABELS = { SEED: '씨앗', SPROUT: '새싹', FLOWER: '꽃', EGGPLANT: '가지' } as const;
const GRADE_TEXT_COLORS = {
  SEED: 'text-[#476A50]',
  SPROUT: 'text-[#486D51]',
  FLOWER: 'text-[#5E4E70]',
  EGGPLANT: 'text-[#3E3452]',
} as const;

type ReportResult =
  | { status: 'notFound' }
  | { status: 'error'; message: string }
  | { status: 'ready'; report: ReportDto };

function formatDate(value: string | null | undefined) {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

function InfoBox({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className={`${SUB_SURFACE} p-4`}>
      <dt className="mb-1 text-xs font-semibold text-[#67717C]">{title}</dt>
      <dd className="whitespace-pre-wrap break-words text-sm font-semibold leading-relaxed text-[#14243A]">{children}</dd>
    </div>
  );
}

function Notice({ title, message, href, action, loading = false }: { title: string; message?: string; href: string; action: string; loading?: boolean }) {
  return <AppShell><PageFeedback kind={loading ? 'loading' : message ? 'error' : 'empty'}
    title={title} description={message} href={loading ? undefined : href}
    action={action} /></AppShell>;
}

/** 기존 실제 보고서·동행 API와 권한 검증은 유지*/
export default function ClientReportPage() {
  const { loading: authLoading, user } = useRequireAuth('CLIENT');
  const { openChatWith } = useChatWidget();
  const { applicationId: applicationIdString } = useParams<{ applicationId: string }>();
  const searchParams = useSearchParams();
  const applicationId = Number(applicationIdString);
  const postIdParam = searchParams.get('postId');
  const postId = postIdParam ? Number(postIdParam) : undefined;

  const [live, setLive] = useState<{ key?: number; escort?: ClientEscortCase }>({});
  const [result, setResult] = useState<{ key?: number; data?: ReportResult }>({});

  useEffect(() => {
    if (!Number.isFinite(applicationId) || !Number.isFinite(postId)) return;
    let ignore = false;
    fetchClientEscortCase(postId!, applicationId)
      .then((escort) => { if (!ignore) setLive({ key: postId, escort }); })
      .catch(() => { if (!ignore) setLive({ key: postId }); });
    return () => { ignore = true; };
  }, [postId, applicationId]);

  useEffect(() => {
    if (!Number.isFinite(applicationId)) return;
    let ignore = false;
    fetchReport(applicationId)
      .then((report) => { if (!ignore) setResult({ key: applicationId, data: { status: 'ready', report } }); })
      .catch((error: unknown) => {
        if (ignore) return;
        setResult({
          key: applicationId,
          data: isReportNotFoundError(error)
            ? { status: 'notFound' }
            : { status: 'error', message: error instanceof Error ? error.message : '보고서를 불러오지 못했습니다.' },
        });
      });
    return () => { ignore = true; };
  }, [applicationId]);

  if (authLoading) return <Notice loading title="인증 정보를 확인하고 있습니다." href="/client/posts" action="작성한 공고로" />;
  if (!user) return null;

  if (postId === undefined || !Number.isFinite(postId) || !Number.isFinite(applicationId)) {
    return <Notice title="동행 정보를 찾을 수 없습니다." message="올바른 공고에서 진료 보고서를 열어주세요." href="/client/posts" action="작성한 공고로" />;
  }

  if (live.key !== postId) return <Notice loading title="동행 정보를 불러오는 중입니다." href="/client/posts" action="작성한 공고로" />;
  const escort = live.escort;
  if (!escort) return <Notice title="동행 정보를 찾을 수 없습니다." href="/client/posts" action="작성한 공고로" />;

  const escortHref = `/client/escort/${escort.applicationId}?postId=${escort.postId}`;
  if (result.key !== applicationId || !result.data) return <Notice loading title="진료 보고서를 불러오는 중입니다." href={escortHref} action="동행 현황으로" />;

  const state = result.data;
  if (state.status === 'notFound') return <Notice title="아직 진료 보고서가 작성되지 않았습니다." href={escortHref} action="동행 현황으로" />;
  if (state.status === 'error') return <Notice title="진료 보고서를 불러오지 못했습니다." message={state.message} href={escortHref} action="동행 현황으로" />;

  const report = state.report;
  const aiSummary = parseAiSummary(report.aiSummary);
  const aiItems = aiSummary ? aiSummaryItems(aiSummary) : [];
  const manager = escort.manager;

  return (
    <AppShell>
      <main className="w-full py-12 lg:py-16">
        <div className="mx-auto w-full max-w-[1240px] space-y-8 px-5 sm:px-8 lg:px-16">
          <Link href="/client/posts" className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] transition-colors hover:text-[#14243A]">
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            목록으로 돌아가기
          </Link>

          <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
            <div>
              <h1 className="text-3xl font-semibold tracking-tight text-[#14243A] sm:text-4xl">진료 보고서</h1>
              <p className="mt-1 text-sm text-[#67717C]">동행이 완료된 후 작성된 진료 내용을 확인할 수 있어요.</p>
            </div>
            <button type="button" onClick={() => window.print()} className={`${SECONDARY_BUTTON} self-start print:hidden`}>
              <Printer className="h-4 w-4" aria-hidden="true" /> 보고서 인쇄
            </button>
          </header>

          <div className="grid grid-cols-1 items-start gap-8 lg:grid-cols-12">
            <div className="min-w-0 space-y-8 lg:col-span-8">
              <section className="space-y-4 rounded-2xl border border-[#E1E5E8] border-l-4 border-l-[#426B93] bg-[#EDF0F2] p-6 shadow-[0_1px_3px_rgba(25,33,61,0.04)] sm:p-8">
                <div className="flex items-center gap-2">
                  <Sparkles className="h-4 w-4 text-[#426B93]" aria-hidden="true" />
                  <h2 className="text-xs font-bold uppercase tracking-wider text-[#426B93]">AI 요약</h2>
                </div>
                {aiItems.length ? (
                  <ul className="space-y-3">
                    {aiItems.map((item) => (
                      <li key={item.label} className="flex items-start gap-2.5 text-sm leading-relaxed text-[#202832]">
                        <span className="mt-0.5 shrink-0 font-bold text-[#426B93]" aria-hidden="true">•</span>
                        <div><span className="font-semibold text-[#14243A]">{item.label}</span><span className="mx-1 text-[#67717C]">·</span><span className="whitespace-pre-wrap break-words">{item.value}</span></div>
                      </li>
                    ))}
                  </ul>
                ) : <p className="text-sm text-[#67717C]">현재 제공된 AI 요약이 없습니다.</p>}
                <p className="border-t border-[#E1E5E8] pt-3 text-xs leading-5 text-[#67717C]">AI가 정리한 정보입니다. 정확한 내용은 아래 원문 진료 기록을 확인해 주세요.</p>
              </section>

              <section className={`${SURFACE} space-y-6 p-6 sm:p-8`}>
                <div className="flex flex-wrap items-start justify-between gap-4 border-b border-[#E1E5E8] pb-4">
                  <div>
                    <span className="text-xs font-semibold text-[#67717C]">의료기관</span>
                    <h2 className="mt-0.5 text-xl font-semibold text-[#14243A]">{escort.hospitalName}</h2>
                  </div>
                  <div className="text-right">
                    <span className="text-xs text-[#67717C]">보고서 작성일</span>
                    <div className="mt-0.5 flex items-center justify-end gap-1.5 text-sm font-semibold tabular-nums text-[#14243A]">
                      <CalendarDays className="h-3.5 w-3.5 text-[#67717C]" aria-hidden="true" />{formatDate(report.createdAt)}
                    </div>
                  </div>
                </div>
                <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <InfoBox title="진료 과목">{report.department}</InfoBox>
                  <InfoBox title="진료 목적">{report.purpose}</InfoBox>
                </dl>
                <div className="space-y-2">
                  <h3 className="flex items-center gap-2 text-sm font-semibold text-[#14243A]"><FileText className="h-4 w-4 text-[#426B93]" aria-hidden="true" /> 진료 내용</h3>
                  <div className={`${SUB_SURFACE} whitespace-pre-wrap break-words p-5 text-sm leading-relaxed text-[#202832]`}>{report.originContent}</div>
                </div>
                {aiSummary?.prescription && (
                  <div className="space-y-2">
                    <h3 className="flex items-center gap-2 text-sm font-semibold text-[#14243A]"><ClipboardList className="h-4 w-4 text-[#426B93]" aria-hidden="true" /> 처방 내역 (AI 요약)</h3>
                    <p className={`${SUB_SURFACE} whitespace-pre-wrap break-words p-4 text-sm leading-relaxed text-[#202832]`}>{aiSummary.prescription}</p>
                  </div>
                )}
                {aiSummary?.nextVisit && (
                  <div className="flex flex-col gap-2 rounded-xl border border-[#E1E5E8] bg-[#EDF0F2] p-4 sm:flex-row sm:items-center sm:justify-between">
                    <span className="text-xs font-semibold text-[#67717C]">다음 방문 일정 (AI 요약)</span>
                    <span className="whitespace-pre-wrap text-sm font-semibold text-[#14243A]">{aiSummary.nextVisit}</span>
                  </div>
                )}
              </section>

              {report.notes && (
                <section className={`${SURFACE} space-y-3 p-6 sm:p-8`}>
                  <h2 className="text-xl font-semibold text-[#14243A]">특이사항</h2>
                  <p className={`${SUB_SURFACE} whitespace-pre-wrap break-words p-4 text-sm leading-relaxed text-[#202832]`}>{report.notes}</p>
                </section>
              )}
            </div>

            <aside className="min-w-0 space-y-6 lg:sticky lg:top-28 lg:col-span-4 print:static">
              <section className={`${SURFACE} space-y-5 p-6`}>
                <h2 className="text-xs font-semibold uppercase tracking-wider text-[#67717C]">동행 매니저 정보</h2>
                <div className="flex items-center gap-4">
                  <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-[#14243A] text-lg font-bold text-white" aria-hidden="true">
                    {manager.name.charAt(0) || <UserRound className="h-5 w-5" />}
                  </div>
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="text-lg font-semibold text-[#14243A]">{manager.name}</h3>
                      {manager.grade && (
                        <span className="inline-flex items-center gap-1">
                          <ManagerGradeCharacter grade={manager.grade} bare className="h-7 w-7 shrink-0" />
                          <span className={`text-sm font-semibold ${GRADE_TEXT_COLORS[manager.grade]}`}>
                            {GRADE_LABELS[manager.grade]}
                          </span>
                        </span>
                      )}
                    </div>
                    <p className="mt-0.5 text-xs text-[#67717C]">보고서 작성 매니저</p>
                  </div>
                </div>
                <dl className="space-y-2 border-t border-[#E1E5E8] pt-3 text-xs">
                  <div className="flex items-center justify-between gap-3">
                    <dt className="text-[#67717C]">보고서 상태</dt>
                    <dd className="font-semibold text-[#2F7D5B]">제출 완료</dd>
                  </div>
                  <div className="flex items-center justify-between gap-3">
                    <dt className="text-[#67717C]">제출일</dt>
                    <dd className="text-right font-medium tabular-nums text-[#202832]">{formatDate(report.createdAt)}</dd>
                  </div>
                </dl>
              </section>
              <section className={`${SURFACE} space-y-3 p-6 print:hidden`}>
                <h2 className="border-b border-[#E1E5E8] pb-3 text-sm font-semibold text-[#14243A]">관련 메뉴</h2>
                <Link href={`/client/escort/${escort.applicationId}/review?postId=${escort.postId}`} className={`${PRIMARY_BUTTON} w-full`}>
                  <Star className="h-4 w-4 text-[#D7BC80]" aria-hidden="true" /> 동행 매니저 리뷰 작성하기
                </Link>
                <button type="button" onClick={() => openChatWith(manager.userId)} className={`${SECONDARY_BUTTON} w-full`}>
                  <MessageSquare className="h-4 w-4 text-[#426B93]" aria-hidden="true" /> 동행매니저에게 메시지 보내기
                </button>
                <Link href="/client/posts" className={`${SECONDARY_BUTTON} w-full`}>
                  <ClipboardList className="h-4 w-4 text-[#426B93]" aria-hidden="true" /> 내가 작성한 공고
                </Link>
              </section>
            </aside>
          </div>
        </div>
      </main>
    </AppShell>
  );
}
