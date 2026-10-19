'use client';

import { useEffect, useRef, type ReactNode } from 'react';
import { CalendarDays, ClipboardList, FileText, Sparkles, X } from 'lucide-react';
import ManagerGradeCharacter from '@/components/ui/ManagerGradeCharacter';
import { sample } from '../data/sampleReport';

const card = 'rounded-2xl border border-[#E1E5E8] bg-white shadow-[0_1px_3px_rgba(25,33,61,0.04)]';
const subCard = 'rounded-xl border border-[#E1E5E8] bg-[#F7F8F8]';

function InfoBox({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className={`${subCard} p-4`}>
      <dt className="mb-1 text-xs font-semibold text-[#67717C]">{title}</dt>
      <dd className="whitespace-pre-wrap break-words text-sm font-semibold leading-relaxed text-[#14243A]">{children}</dd>
    </div>
  );
}

export default function SampleReportModal({ onClose }: { onClose: () => void }) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeRef.current?.focus();

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
      }
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not(:disabled),a[href],input:not(:disabled),textarea:not(:disabled),[tabindex]:not([tabindex="-1"])'));
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus();
    };
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-[120] flex items-center justify-center bg-black/70 p-3 sm:p-6"
      onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="sample-report-modal-title"
        aria-describedby="sample-report-modal-description"
        className="relative flex max-h-[92dvh] w-full max-w-[1120px] flex-col overflow-hidden rounded-2xl border border-[#E1E5E8] bg-[#F7F8F8] shadow-[0_24px_80px_rgba(0,0,0,0.28)] sm:max-h-[90dvh]"
      >
        {/* 제목 바 없이 보고서 위에 떠 있는 닫기 버튼. 본문을 스크롤해도 자리는 유지합니다. */}
        <button
          ref={closeRef}
          type="button"
          aria-label="예시 진료 보고서 닫기"
          onClick={onClose}
          className="absolute right-4 top-4 z-10 flex h-10 w-10 cursor-pointer items-center justify-center rounded-lg text-[#67717C] transition-colors hover:bg-black/[0.06] hover:text-[#14243A] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93] sm:right-6 sm:top-6"
        >
          <X className="h-6 w-6" aria-hidden="true" />
        </button>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 pb-6 pt-16 sm:px-8 sm:pb-8 sm:pt-12">
          <div className="space-y-7">
            <header className="pr-9 sm:pr-12">
              <div className="flex flex-wrap items-center gap-3">
                <h2 id="sample-report-modal-title" className="text-3xl font-semibold tracking-tight text-[#14243A] sm:text-4xl">진료 보고서</h2>
                <span className="inline-flex shrink-0 items-center rounded-md border border-[#DCE3EC] bg-[#EDF0F2] px-2.5 py-1 text-xs font-semibold text-[#426B93]">예시</span>
              </div>
              <p id="sample-report-modal-description" className="mt-1 text-sm text-[#67717C]">동행이 완료된 후 작성된 진료 내용을 확인할 수 있어요.</p>
            </header>

            <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-12 lg:gap-8">
              <div className="min-w-0 space-y-6 lg:col-span-8">
                <section className="space-y-4 rounded-2xl border border-[#E1E5E8] border-l-4 border-l-[#426B93] bg-[#EDF0F2] p-6 shadow-[0_1px_3px_rgba(25,33,61,0.04)] sm:p-8">
                  <div className="flex items-center gap-2">
                    <Sparkles className="h-4 w-4 text-[#426B93]" aria-hidden="true" />
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[#426B93]">AI 요약</h3>
                  </div>
                  <ul className="space-y-3">
                    {sample.aiSummary.keyPoints.map((point, index) => (
                      <li key={point} className="flex items-start gap-2.5 text-sm leading-relaxed text-[#202832]">
                        <span aria-hidden="true" className="mt-0.5 shrink-0 font-bold text-[#426B93]">•</span>
                        <div>
                          <span className="font-semibold text-[#14243A]">{['진료 경과', '복약 안내', '생활 시 주의'][index]}</span>
                          <span className="mx-1 text-[#67717C]">·</span>
                          <span>{point}</span>
                        </div>
                      </li>
                    ))}
                  </ul>
                  <p className="border-t border-[#E1E5E8] pt-3 text-xs leading-5 text-[#67717C]">AI가 정리한 정보입니다. 정확한 내용은 아래 원문 진료 기록을 확인해 주세요.</p>
                </section>

                <section className={`${card} space-y-6 p-6 sm:p-8`}>
                  <div className="flex flex-wrap items-start justify-between gap-4 border-b border-[#E1E5E8] pb-4">
                    <div>
                      <span className="text-xs font-semibold text-[#67717C]">의료기관</span>
                      <h3 className="mt-0.5 text-xl font-semibold text-[#14243A]">{sample.hospitalName}</h3>
                    </div>
                    <div className="text-right">
                      <span className="text-xs text-[#67717C]">보고서 작성일</span>
                      <div className="mt-0.5 flex items-center justify-end gap-1.5 text-sm font-semibold tabular-nums text-[#14243A]">
                        <CalendarDays className="h-3.5 w-3.5 text-[#67717C]" aria-hidden="true" />
                        {sample.consultationDate}
                      </div>
                    </div>
                  </div>

                  <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <InfoBox title="진료 과목">{sample.department}</InfoBox>
                    <InfoBox title="진료 목적">{sample.purpose}</InfoBox>
                  </dl>

                  <div className="space-y-2">
                    <h4 className="flex items-center gap-2 text-sm font-semibold text-[#14243A]"><FileText className="h-4 w-4 text-[#426B93]" aria-hidden="true" />진료 내용</h4>
                    <div className={`${subCard} whitespace-pre-wrap break-words p-5 text-sm leading-relaxed text-[#202832]`}>{sample.doctorDirections}</div>
                  </div>

                  <div className="space-y-2">
                    <h4 className="flex items-center gap-2 text-sm font-semibold text-[#14243A]"><ClipboardList className="h-4 w-4 text-[#426B93]" aria-hidden="true" />처방 내역 (AI 요약)</h4>
                    <p className={`${subCard} whitespace-pre-wrap break-words p-4 text-sm leading-relaxed text-[#202832]`}>
                      {sample.prescriptionDetails.map((item) => `${item.name} · ${item.dosage} · ${item.timing}`).join('\n')}
                    </p>
                  </div>

                  <div className="flex flex-col gap-2 rounded-xl border border-[#E1E5E8] bg-[#EDF0F2] p-4 sm:flex-row sm:items-center sm:justify-between">
                    <span className="text-xs font-semibold text-[#67717C]">다음 방문 일정 (AI 요약)</span>
                    <span className="whitespace-pre-wrap text-sm font-semibold text-[#14243A]">{sample.nextAppointmentDate}</span>
                  </div>
                </section>

                <section className={`${card} space-y-3 p-6 sm:p-8`}>
                  <h3 className="text-xl font-semibold text-[#14243A]">특이사항</h3>
                  <p className={`${subCard} whitespace-pre-wrap break-words p-4 text-sm leading-relaxed text-[#202832]`}>{sample.managerNotes}</p>
                </section>
              </div>

              <aside className="min-w-0 space-y-6 lg:col-span-4">
                <section className={`${card} space-y-5 p-6`}>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-[#67717C]">동행 매니저 정보</h3>
                  <div className="flex items-center gap-4">
                    <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-[#14243A] text-lg font-bold text-white" aria-hidden="true">{sample.managerName.charAt(0)}</div>
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <h4 className="text-lg font-semibold text-[#14243A]">{sample.managerName}</h4>
                        <span className="inline-flex items-center gap-1">
                          <ManagerGradeCharacter grade="EGGPLANT" bare className="h-7 w-7 shrink-0" />
                          <span className="text-sm font-semibold text-[#3E3452]">{sample.managerGrade}</span>
                        </span>
                      </div>
                      <p className="mt-0.5 text-xs text-[#67717C]">보고서 작성 매니저</p>
                    </div>
                  </div>
                  <dl className="space-y-2 border-t border-[#E1E5E8] pt-3 text-xs">
                    <div className="flex items-center justify-between gap-3">
                      <dt className="text-[#67717C]">보고서 상태</dt>
                      <dd className="font-semibold text-[#2F7D5B]">제출 완료 (예시)</dd>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <dt className="text-[#67717C]">제출일</dt>
                      <dd className="text-right font-medium tabular-nums text-[#202832]">{sample.consultationDate}</dd>
                    </div>
                  </dl>
                </section>
                <p className="rounded-xl border border-[#E1E5E8] bg-white p-4 text-xs leading-relaxed text-[#67717C]">
                  이 화면은 서비스 체험을 위한 예시입니다. 실제 진료 보고서는 동행 완료 후 담당 매니저가 작성한 내용으로 제공됩니다.
                </p>
              </aside>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
