'use client';

import { motion, useReducedMotion } from 'motion/react';

import { useRef } from 'react';

/**
 * GAJI Signature Care — AI 진료 보고서 소개
 * Source: final (1)(1).zip / src/views/HomeView.tsx (#report)
 * 예시 데이터는 원본 src/data/mockData.ts의 MOCK_REPORT_SAMPLE을 발췌한 정적 샘플입니다.
 * 실데이터 및 인증 API는 사용하지 않습니다.
 */
const sample = {
  hospitalName: '경희대학교병원 재활의학과',
  aiSummary: {
    headline: '보행 안정성이 전월 대비 향상되었으며, 혈압약 복용 시간 변경이 권고되었습니다.',
    keyPoints: [
      '우측 다리 근력 지수 3.8등급으로 지난달(3.4) 대비 안정적 회복세 확인',
      '아침 혈압약은 기상 직후 30분 이내 식전 복용으로 처방 방식 변경',
      '낙상 방지를 위해 자택 문턱 제거 및 욕실 미끄럼 방지 매트 설치 권장',
    ],
  },
  doctorDirections: '환자분의 보행 밸런스가 눈에 띄게 좋아졌습니다. 다만 저녁 시간대 발목 부종이 약간 관찰되므로, 취침 전 다리를 베개 위에 15분간 올려놓는 거상 요법을 시행해주십시오. 다음 달 외래 전까지 매일 아침 수축기 혈압을 수첩에 기록해오시기 바랍니다.',
  nextAppointmentDate: '2026년 11월 11일 (수) 오후 03:30 (재활의학과 4진료실)',
};

function SparkleIcon({ className = '' }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m12 3 1.7 6.3L20 11l-6.3 1.7L12 19l-1.7-6.3L4 11l6.3-1.7L12 3ZM19 17l.7 2.3L22 20l-2.3.7L19 23l-.7-2.3L16 20l2.3-.7L19 17Z"/></svg>;
}
function FileIcon({ className = '' }: { className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6M8 13h8M8 17h8"/></svg>;
}

const easeOutCubic = [0.22, 1, 0.36, 1] as const;

export default function ReportSection() {
  const shouldReduceMotion = useReducedMotion();
  const previewRef = useRef<HTMLDivElement>(null);

  return (
    <section id="report" className="border-t border-[#E1E5E8] bg-[#EDF0F2] py-24 lg:py-32">
      <div className="mx-auto max-w-[1240px] px-5 sm:px-8 lg:px-16">
        <div className="grid grid-cols-1 items-center gap-12 lg:grid-cols-12 lg:gap-16">
          <motion.div initial={shouldReduceMotion ? false : { opacity: 0, y: 14 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.2 }} transition={{ duration: 0.5, ease: easeOutCubic }} className="space-y-6 lg:col-span-6">
            <h2 className="whitespace-pre-line text-[32px] leading-[1.3] font-semibold tracking-tight text-[#14243A] sm:text-[36px]">
              {'동행이 끝나면 진료 내용과\nAI 요약을 보호자에게 전해드려요'}
            </h2>
            <p className="text-[17px] leading-[1.7] text-[#67717C]">
              진료실에서 의사 선생님이 전한 핵심 지시사항, 변경된 처방약 복용법, 다음 재방문 날짜까지 매니저의 기록을 바탕으로 일목요연하게 전달됩니다.
            </p>
            <div className="space-y-4 pt-2">
              <div className="flex items-start gap-3 rounded-xl border border-[#E1E5E8] bg-white p-4">
                <SparkleIcon className="mt-0.5 h-5 w-5 shrink-0 text-[#426B93]" />
                <div>
                  <h4 className="text-sm font-semibold text-[#14243A]">보호자를 위한 3줄 AI 핵심 요약</h4>
                  <p className="mt-1 text-xs text-[#67717C]">복잡한 의학 용어를 보호자가 한눈에 이해할 수 있도록 명료하게 정리합니다.</p>
                </div>
              </div>
              <div className="flex items-start gap-3 rounded-xl border border-[#E1E5E8] bg-white p-4">
                <FileIcon className="mt-0.5 h-5 w-5 shrink-0 text-[#426B93]" />
                <div>
                  <h4 className="text-sm font-semibold text-[#14243A]">처방전 및 복약 스케줄 기록</h4>
                  <p className="mt-1 text-xs text-[#67717C]">식전·식후 복약법과 특이 주의사항을 약국 안내 그대로 기록하여 누락을 방지합니다.</p>
                </div>
              </div>
            </div>
            <div className="pt-2">
              <button
                type="button"
                onClick={() => previewRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })}
                className="group inline-flex h-[48px] cursor-pointer items-center gap-2 rounded-[10px] bg-[#14243A] px-6 text-[15px] font-semibold text-white shadow-xs transition-all duration-200 hover:bg-[#1f3757] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
              >
                <span>예시 보고서 열람하기</span>
                <svg className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg>
              </button>
            </div>
          </motion.div>
          <motion.div initial={shouldReduceMotion ? false : { opacity: 0, y: 16 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.2 }} transition={{ duration: 0.55, ease: easeOutCubic }} ref={previewRef} tabIndex={-1} className="space-y-6 rounded-2xl border border-[#E1E5E8] bg-white p-6 shadow-sm transition-all duration-200 hover:border-[#CBD5E1] hover:shadow-md focus:outline-none sm:p-8 lg:col-span-6">
            <div className="flex items-center justify-between border-b border-[#E1E5E8] pb-4">
              <div>
                <span className="text-xs font-semibold text-[#426B93]">GAJI 진료 보고서 (예시 보고서)</span>
                <h3 className="text-lg font-semibold text-[#14243A]">{sample.hospitalName}</h3>
              </div>
              <span className="rounded bg-[#2F7D5B]/10 px-2.5 py-1 text-xs font-semibold text-[#2F7D5B]">보고 완료</span>
            </div>
            <div className="rounded-xl border border-[#E1E5E8] bg-[#F7F8F8] p-4">
              <div className="mb-2 flex items-center gap-1.5 text-xs font-semibold text-[#426B93]">
                <SparkleIcon className="h-4 w-4" />
                <span>AI 안심 요약</span>
              </div>
              <p className="mb-2 text-sm font-semibold text-[#14243A]">&quot;{sample.aiSummary.headline}&quot;</p>
              <ul className="space-y-1.5 text-xs text-[#67717C]">
                {sample.aiSummary.keyPoints.map((point) => (
                  <li key={point} className="flex items-start gap-1.5"><span className="text-[#426B93]">•</span><span>{point}</span></li>
                ))}
              </ul>
            </div>
            <div className="space-y-1 text-xs">
              <span className="font-semibold text-[#14243A]">의사 소견 및 주의사항</span>
              <p className="line-clamp-2 leading-relaxed text-[#67717C]">{sample.doctorDirections}</p>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[#E1E5E8] pt-3 text-xs">
              <span className="text-[#67717C]">다음 예약 일정</span>
              <span className="font-semibold text-[#14243A]">{sample.nextAppointmentDate}</span>
            </div>
          </motion.div>
        </div>
      </div>
    </section>
  );
}
