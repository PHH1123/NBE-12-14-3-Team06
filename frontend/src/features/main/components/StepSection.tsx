'use client';

import { useRef, useState, type KeyboardEvent } from 'react';
import { motion, AnimatePresence, useReducedMotion } from 'motion/react';

type Audience = 'client' | 'manager';

type Step = {
  number: string;
  tag: string;
  title: string;
  description: string;
};

/** Google AI Studio 확정 HomeView의 이용 방법 문구·배치·색상 기준 */
const STEPS: Record<Audience, Step[]> = {
  client: [
    { number: '01', tag: '공고 등록', title: '동행 요청하기', description: '원하는 날짜, 방문할 병원, 진료과, 어르신의 보행 컨디션과 주의사항을 간단히 올려보세요.' },
    { number: '02', tag: '매니저 승인', title: '매칭되기', description: '지원한 매니저들의 등급(씨앗~가지), 보유 자격, 실제 이용 리뷰를 비교하고 직접 선택해 연결돼요.' },
    { number: '03', tag: '안심 동행', title: '함께 병원 가지', description: '약속된 시간에 만나 안전하게 병원에 가고, 귀가 후 의사 설명과 처방이 담긴 AI 진료 보고서를 받아요.' },
  ],
  manager: [
    { number: '01', tag: '일정 탐색', title: '동행 지원하기', description: '원하는 날짜에 거주지와 가까운 병원의 공고를 선택하고 지원 동기와 강점을 작성해 보세요.' },
    { number: '02', tag: '매칭 확정', title: '매칭되기', description: '보호자의 선택으로 매칭이 확정되면 어르신의 상세 주의사항과 이동 동선 정보를 열람합니다.' },
    { number: '03', tag: '보고서 & 정산', title: '함께 병원 가지', description: '안전하게 동행을 마치고 진료 보고서를 등록하면 정산 규정에 따라 시급이 정산됩니다.' },
  ],
};

const TABS: { id: Audience; label: string }[] = [
  { id: 'client', label: '요청자 (의뢰인)' },
  { id: 'manager', label: '동행 매니저' },
];

export default function StepSection() {
  const [audience, setAudience] = useState<Audience>('client');
  const reduceMotion = useReducedMotion();
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const activeIndex = audience === 'client' ? 0 : 1;

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    event.preventDefault();
    const next = (index + (event.key === 'ArrowRight' ? 1 : -1) + TABS.length) % TABS.length;
    setAudience(TABS[next].id);
    tabRefs.current[next]?.focus();
  };

  return (
    <section id="steps" aria-labelledby="steps-heading" className="scroll-mt-[72px] bg-[#F7F8F8] py-20 lg:py-28">
      <div className="mx-auto w-full max-w-[1240px] px-5 sm:px-8 lg:px-16">
        <motion.div initial={reduceMotion ? false : { opacity: 0, y: 14 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.2 }} transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }} className="mb-12 flex flex-col justify-between gap-6 md:flex-row md:items-end">
          <div>
            <h2 id="steps-heading" className="text-[28px] font-semibold leading-[1.3] tracking-tight text-[#14243A] sm:text-[34px]">이용 방법</h2>
            <p className="mt-2 text-sm text-[#67717C]">신청부터 매칭, 진료실 동행 및 귀가까지의 3단계 이용 절차를 확인하세요.</p>
          </div>
          <div role="tablist" aria-label="이용 방법 대상 선택" className="relative inline-flex shrink-0 items-center gap-1 self-start rounded-xl border border-[#E1E5E8] bg-[#EDF0F2] p-1 md:self-auto">
            {TABS.map((tab, index) => (
              <button
                key={tab.id}
                ref={(el) => { tabRefs.current[index] = el; }}
                type="button"
                role="tab"
                id={`steps-tab-${tab.id}`}
                aria-controls="steps-panel"
                aria-selected={audience === tab.id}
                tabIndex={audience === tab.id ? 0 : -1}
                onClick={() => setAudience(tab.id)}
                onKeyDown={(event) => handleKeyDown(event, index)}
                className="relative flex items-center justify-center rounded-[9px] px-4 py-2 text-[14px] font-semibold select-none outline-none focus-visible:ring-2 focus-visible:ring-[#426B93] focus-visible:ring-offset-1 sm:px-5 sm:py-2.5 sm:text-[15px]"
              >
                {audience === tab.id && (
                  <motion.span layoutId="stepsTabActivePill" aria-hidden="true" className="absolute inset-0 rounded-[9px] border border-[#E1E5E8] bg-white shadow-[0_1px_2px_rgba(20,36,58,0.06)]" transition={reduceMotion ? { duration: 0 } : { duration: 0.22, ease: [0.25, 0.1, 0.25, 1] }} />
                )}
                <span className={`relative z-10 transition-colors duration-200 ${audience === tab.id ? 'text-[#14243A]' : 'text-[#67717C] hover:text-[#14243A]'}`}>{tab.label}</span>
              </button>
            ))}
          </div>
        </motion.div>
        <AnimatePresence mode="wait">
        <motion.div key={audience} initial={reduceMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={reduceMotion ? undefined : { opacity: 0, y: -6 }} transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }} id="steps-panel" role="tabpanel" aria-labelledby={`steps-tab-${audience}`} className="grid grid-cols-1 gap-6 md:grid-cols-3">
          {STEPS[audience].map((step, index) => (
            <motion.article key={step.number} initial={reduceMotion ? false : { opacity: 0, y: 14 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.2 }} transition={{ duration: 0.45, delay: reduceMotion ? 0 : index * 0.08, ease: [0.22, 1, 0.36, 1] }} className="flex h-full cursor-default flex-col justify-between rounded-xl border border-[#E1E5E8] bg-white p-6 transition-all duration-200 ease-out hover:-translate-y-0.5 hover:border-[#98A2B3] hover:shadow-sm motion-reduce:hover:translate-y-0 sm:p-7">
              <div>
                <div className="mb-4 flex items-center justify-between">
                  <span className="font-mono text-2xl leading-none font-semibold text-[#426B93]">{step.number}</span>
                  <span className="rounded-md bg-[#EDF0F2] px-2.5 py-0.5 text-xs font-semibold text-[#14243A]">{step.tag}</span>
                </div>
                <h3 className="mb-2 text-lg font-semibold text-[#14243A]">{step.title}</h3>
                <p className="text-[14px] leading-[1.6] text-[#67717C] sm:text-[15px]">{step.description}</p>
              </div>
            </motion.article>
          ))}
        </motion.div>
        </AnimatePresence>
      </div>
    </section>
  );
}
