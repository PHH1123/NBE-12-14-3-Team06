'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { ChevronLeft, FileText, MessageSquare, Pill, Printer, Sparkles } from 'lucide-react';
import { AppShell } from '@/components/layout';
import { useRequireAuth } from '@/features/auth';
import { useChatWidget } from '@/features/chat';
import { fetchReport, isReportNotFoundError, parseAiSummary, aiSummaryItems, type ReportDto } from '@/features/report';
import { fetchReportTarget } from '../api';
import { fetchMyEscortProfile } from '@/features/mypage/api';
import ManagerGradeCharacter from '@/components/ui/ManagerGradeCharacter';
import type { EscortGrade } from '@/features/application';
import { formatDateTime } from '../lib/date';
import type { ReportTarget } from '../types';

const gradeNames: Record<EscortGrade, string> = {
  SEED: '씨앗',
  SPROUT: '새싹',
  FLOWER: '꽃',
  EGGPLANT: '가지',
};

const gradeTextColors: Record<EscortGrade, string> = {
  SEED: 'text-[#476A50]',
  SPROUT: 'text-[#486D51]',
  FLOWER: 'text-[#5E4E70]',
  EGGPLANT: 'text-[#3E3452]',
};

type ReportResult =
  | { status: 'noEscort' }
  | { status: 'notFound' }
  | { status: 'error'; message: string }
  | { status: 'ready'; target: ReportTarget; report: ReportDto };

function Multiline({ value }: { value: string }) {
  return <span className="whitespace-pre-wrap break-words">{value}</span>;
}

export default function ReportDetailPage() {
  const { loading: authLoading, user } = useRequireAuth('ESCORT');
  const { openChatWith } = useChatWidget();
  const params = useParams<{ applicationId: string }>();
  const applicationId = Number(params.applicationId);
  const [result, setResult] = useState<{ key?: number; data?: ReportResult }>({});
  const [managerGrade, setManagerGrade] = useState<EscortGrade | null>(null);

  useEffect(() => {
    if (authLoading || !user || user.role !== 'ESCORT') return;
    let ignore = false;
    fetchMyEscortProfile()
      .then((profile) => { if (!ignore) setManagerGrade(profile.grade); })
      .catch(() => { if (!ignore) setManagerGrade(null); });
    return () => { ignore = true; };
  }, [authLoading, user]);

  useEffect(() => {
    if (authLoading || !user || user.role !== 'ESCORT' || !Number.isInteger(applicationId) || applicationId <= 0) return;
    let ignore = false;
    const reportOutcome = fetchReport(applicationId).then(
      (report) => ({ report }),
      (error: unknown) => ({ error }),
    );
    Promise.all([fetchReportTarget(applicationId), reportOutcome])
      .then(([target, outcome]) => {
        if (ignore) return;
        let data: ReportResult;
        if (!target) data = { status: 'noEscort' };
        else if ('error' in outcome) {
          data = isReportNotFoundError(outcome.error)
            ? { status: 'notFound' }
            : { status: 'error', message: outcome.error instanceof Error ? outcome.error.message : '보고서를 불러오지 못했습니다.' };
        } else data = { status: 'ready', target, report: outcome.report };
        setResult({ key: applicationId, data });
      })
      .catch((error: unknown) => {
        if (ignore) return;
        setResult({ key: applicationId, data: { status: 'error', message: error instanceof Error ? error.message : '보고서를 불러오지 못했습니다.' } });
      });
    return () => { ignore = true; };
  }, [applicationId, authLoading, user]);

  if (authLoading || (user && Number.isInteger(applicationId) && applicationId > 0 && result.key !== applicationId)) {
    return <AppShell><div className="w-full bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]">진료 보고서를 불러오는 중입니다.</div></AppShell>;
  }
  if (!user || user.role !== 'ESCORT') return null;
  if (!Number.isInteger(applicationId) || applicationId <= 0) {
    return <AppShell><div className="w-full bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]">잘못된 동행 번호입니다.</div></AppShell>;
  }

  const state = result.data;
  if (!state || state.status !== 'ready') {
    const message = state?.status === 'noEscort' ? '동행 정보를 찾을 수 없습니다.'
      : state?.status === 'notFound' ? '아직 보고서가 작성되지 않았습니다.'
        : state?.status === 'error' ? state.message : '진료 보고서를 불러올 수 없습니다.';
    return (
      <AppShell>
        <div className="w-full bg-[#F7F8F8] py-24 px-5 text-center">
          <p role={state?.status === 'error' ? 'alert' : undefined} className="text-sm text-[#67717C]">{message}</p>
          <Link href={state?.status === 'noEscort' ? '/mypage/applications' : `/escort/${applicationId}`} className="inline-flex items-center justify-center mt-6 px-5 h-[46px] rounded-xl bg-[#14243A] text-white text-xs font-semibold">돌아가기</Link>
        </div>
      </AppShell>
    );
  }

  const { target, report } = state;
  const aiSummary = parseAiSummary(report.aiSummary);
  const summaryItems = aiSummary ? aiSummaryItems(aiSummary) : [];

  return (
    <AppShell>
      <div className="w-full bg-[#F7F8F8] py-12 lg:py-16">
        <div className="max-w-[1240px] mx-auto px-5 sm:px-8 lg:px-16">
          <Link href="/mypage/applications" className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] hover:text-[#14243A] mb-8">
            <ChevronLeft className="w-4 h-4" />
            <span>목록으로 돌아가기</span>
          </Link>

          
          <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 mb-8">
            <div>
              <h1 className="text-3xl sm:text-4xl font-semibold text-[#14243A] tracking-tight">진료 보고서</h1>
              <p className="text-sm text-[#67717C] mt-1">동행이 완료된 후 작성된 진료 내용을 확인할 수 있어요.</p>
            </div>
            <div className="flex items-center gap-2">
              <button type="button" onClick={() => window.print()} className="h-[44px] px-4 rounded-[10px] border border-[#E1E5E8] bg-white text-xs font-semibold text-[#14243A] hover:bg-[#F7F8F8] flex items-center gap-1.5 transition-colors">
                <Printer className="w-4 h-4" />
                <span>보고서 인쇄</span>
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            <div className="lg:col-span-8 space-y-8 min-w-0">
              <div className="bg-[#EDF0F2] rounded-2xl p-6 sm:p-8 border border-[#E1E5E8] border-l-4 border-l-[#426B93] space-y-4 shadow-xs">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Sparkles className="w-4 h-4 text-[#426B93]" />
                    <span className="text-xs font-bold tracking-wider uppercase text-[#426B93]">AI 요약</span>
                  </div>
                  <span className="text-[11px] bg-white/70 px-2 py-0.5 rounded text-[#67717C] font-medium">요약 정리</span>
                </div>

                {aiSummary?.visitPurpose && (
                  <h2 className="text-lg sm:text-xl font-semibold text-[#14243A] leading-snug break-words">
                    {aiSummary.visitPurpose}
                  </h2>
                )}
                {summaryItems.some((item) => item.label !== '방문 목적') ? (
                  <ul className="space-y-2 pt-1 text-sm text-[#202832]">
                    {summaryItems.filter((item) => item.label !== '방문 목적').map((item) => (
                      <li key={item.label} className="flex items-start gap-2.5">
                        <span className="text-[#426B93] font-bold mt-0.5">•</span>
                        <span className="leading-relaxed break-words"><span className="font-semibold">{item.label}: </span><Multiline value={item.value} /></span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-[#67717C]">AI 요약을 준비 중입니다.</p>
                )}
                <div className="pt-2 border-t border-[#E1E5E8] text-xs text-[#67717C]">
                  * AI가 요약한 내용으로, 정확한 내용은 진료 내용을 확인하세요.
                </div>
              </div>

              <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs space-y-6">
                <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-[#E1E5E8]">
                  <div>
                    <span className="text-xs font-semibold text-[#67717C]">의료기관</span>
                    <h3 className="text-xl font-semibold text-[#14243A] mt-0.5">{target.hospitalName}</h3>
                  </div>
                  <div className="text-right text-xs text-[#67717C]">
                    <div>동행 일시</div>
                    <div className="font-semibold text-[#14243A] tabular-nums mt-0.5">{target.dateLabel} {target.workTime}</div>
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
                  <div className="bg-[#F7F8F8] p-4 rounded-xl border border-[#E1E5E8]">
                    <span className="text-xs font-semibold text-[#67717C] block mb-1">진료 과목</span>
                    <span className="font-semibold text-[#14243A] break-words">{report.department}</span>
                  </div>
                  <div className="bg-[#F7F8F8] p-4 rounded-xl border border-[#E1E5E8]">
                    <span className="text-xs font-semibold text-[#67717C] block mb-1">진료 목적</span>
                    <span className="font-semibold text-[#14243A] break-words">{report.purpose}</span>
                  </div>
                </div>

                <div className="space-y-2">
                  <h4 className="text-sm font-semibold text-[#14243A]">진료 내용</h4>
                  <div className="bg-[#F7F8F8] p-5 rounded-xl border border-[#E1E5E8] text-sm text-[#202832] leading-relaxed">
                    <Multiline value={report.originContent} />
                  </div>
                </div>
                {aiSummary?.prescription && (
                  <div className="space-y-3 pt-2">
                    <h4 className="text-sm font-semibold text-[#14243A] flex items-center gap-2">
                      <Pill className="w-4 h-4 text-[#426B93]" />
                      <span>처방약 및 복약 지도 안내 (AI 요약)</span>
                    </h4>
                    <div className="border border-[#E1E5E8] rounded-xl p-4 text-sm text-[#202832] leading-relaxed"><Multiline value={aiSummary.prescription} /></div>
                  </div>
                )}
                {aiSummary?.nextVisit && (
                  <div className="bg-[#EDF0F2] p-4 rounded-xl border border-[#E1E5E8] flex flex-wrap items-center justify-between gap-2 text-xs">
                    <span className="text-[#67717C] font-semibold">다음 방문 일정 (AI 요약)</span>
                    <span className="font-bold text-[#14243A] text-sm"><Multiline value={aiSummary.nextVisit} /></span>
                  </div>
                )}
              </div>

              {report.notes?.trim() && (
                <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs space-y-3">
                  <h3 className="text-xl font-semibold text-[#14243A]">특이사항</h3>
                  <p className="text-sm text-[#202832] leading-relaxed bg-[#F7F8F8] p-4 rounded-xl border border-[#E1E5E8]">
                    <Multiline value={report.notes} />
                  </p>
                </div>
              )}

              <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs space-y-3">
                <h3 className="text-xl font-semibold text-[#14243A]">보고서 정보</h3>
                <div className="bg-[#F7F8F8] p-4 rounded-xl border border-[#E1E5E8] text-sm text-[#202832] space-y-2">
                  <div className="flex justify-between gap-3"><span className="text-[#67717C]">공고</span><span className="text-right font-medium">{target.title}</span></div>
                  <div className="flex justify-between gap-3"><span className="text-[#67717C]">의뢰인</span><span className="font-medium">{report.clientName}</span></div>
                  <div className="flex justify-between gap-3"><span className="text-[#67717C]">제출일</span><span className="font-medium tabular-nums">{formatDateTime(report.createdAt)}</span></div>
                </div>
              </div>
            </div>

            <div className="lg:col-span-4 space-y-6 lg:sticky lg:top-28 min-w-0">
              <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 shadow-xs space-y-5">
                <span className="text-xs font-semibold text-[#67717C] uppercase tracking-wider block">동행 매니저 정보</span>
                <div className="flex items-center gap-4">
                  <div className="w-14 h-14 rounded-full bg-[#14243A] text-white flex items-center justify-center font-bold text-lg shrink-0">
                    {user.name.charAt(0)}
                  </div>
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="text-lg font-semibold text-[#14243A]">{user.name}</h3>
                      {managerGrade && (
                        <span className="inline-flex items-center gap-1">
                          <ManagerGradeCharacter grade={managerGrade} bare className="h-7 w-7 shrink-0" />
                          <span className={`text-sm font-semibold ${gradeTextColors[managerGrade]}`}>{gradeNames[managerGrade]}</span>
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-[#67717C] mt-0.5">보고서 작성 매니저</p>
                  </div>
                </div>
                <div className="pt-3 border-t border-[#E1E5E8] space-y-2 text-xs">
                  <div className="flex justify-between gap-2"><span className="text-[#67717C]">보고서 상태</span><span className="font-semibold text-[#2F7D5B]">제출 완료</span></div>
                  <div className="flex justify-between gap-2"><span className="text-[#67717C]">제출일</span><span className="font-medium text-[#202832] text-right">{formatDateTime(report.createdAt)}</span></div>
                </div>
              </div>

              <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 shadow-xs space-y-3">
                <h3 className="text-sm font-semibold text-[#14243A] pb-2 border-b border-[#E1E5E8]">관련 메뉴</h3>
                <Link href={`/escort/${target.applicationId}`} className="w-full h-[46px] rounded-xl bg-[#14243A] text-white text-xs font-semibold hover:bg-[#1f3757] flex items-center justify-center gap-2 shadow-xs transition-colors">
                  <FileText className="w-4 h-4" />
                  <span>동행 현황으로 돌아가기</span>
                </Link>
                <button type="button" onClick={() => openChatWith(target.clientId)} className="w-full h-[46px] rounded-xl border border-[#E1E5E8] bg-white text-xs font-semibold text-[#14243A] hover:bg-[#F7F8F8] flex items-center justify-center gap-2 transition-colors">
                  <MessageSquare className="w-4 h-4 text-[#426B93]" />
                  <span>의뢰인에게 메시지 보내기</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
