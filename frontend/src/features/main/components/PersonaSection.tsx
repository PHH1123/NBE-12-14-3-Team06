'use client';

import { useRef, useState, type KeyboardEvent } from 'react';
import Image from 'next/image';
import { motion, AnimatePresence, useReducedMotion } from 'motion/react';

const AUDIENCES = [
  {
    id: 'senior', label: '어르신', eyebrow: '어르신 본인 이용',
    heading: '혼자 병원 가기 어려운 어르신,\n동행 매니저가 함께 이동합니다.',
    description: '복잡한 대학병원 원내 길 찾기, 무인 접수기 키오스크 수납, 채혈실과 엑스레이실 이동까지 곁에서 친절하게 보조해 드립니다.',
    highlights: [
      '자택 문 앞부터 병원 진료실까지 전 과정 부축 및 동행',
      '휠체어 대여 및 승강기 우선 이동 보조',
      '약국 처방전 조제 대기 및 안전 귀가 확인',
    ],
    image: '/images/persona/senior.jpg', alt: '병원 라운지에서 앉아 계신 어르신',
  },
  {
    id: 'guardian', label: '보호자', eyebrow: '자녀 및 가족 보호자',
    heading: '멀리 있어 함께 가지 못해도 괜찮아요.\n동행부터 진료 결과까지 알려드립니다.',
    description: '직장 출근이나 타지역 거주로 부모님 정기 외래에 동행하기 어려우신가요? 매니저의 동행 진행 현황과 진료 내용 요약 보고서로 안심을 드립니다.',
    highlights: [
      '출발, 접수, 진료 시작 및 종료 단계별 진행 현황 확인',
      '의사 설명 핵심 내용과 다음 내원 일정이 담긴 진료 보고서',
      '바쁜 가족을 위한 3줄 핵심 AI 진료 요약 제공',
    ],
    image: '/images/persona/guardian.jpg', alt: '병원에서 어르신을 부축하는 동행 매니저',
  },
  {
    id: 'manager', label: '동행 매니저', eyebrow: '전문 동행 매니저',
    heading: '원하는 날짜와 지역의 공고를 골라 지원하세요.\n의뢰인의 선택을 거쳐 어르신과 매칭됩니다.',
    description: '동행 일정과 조건을 살펴보고 자신에게 맞는 공고에 지원할 수 있습니다. 성실하게 완료한 동행 횟수를 바탕으로 등급이 성장합니다.',
    highlights: [
      '내 주거지와 가까운 병원 및 원하는 진료 일정 선택 지원',
      '씨앗 → 새싹 → 꽃 → 가지 단계별 투명한 성장 등급 체계',
      '모바일에서 간편하게 진료 보고서 작성 지원',
    ],
    image: '/images/persona/manager.jpg', alt: '병원 앞에서 어르신과 함께 걷는 동행 매니저',
  },
] as const;

export default function PersonaSection() {
  const [activeIndex, setActiveIndex] = useState(0);
  const reduceMotion = useReducedMotion();
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const active = AUDIENCES[activeIndex];

  const onTabKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    let next = index;
    if (event.key === 'ArrowRight') next = (index + 1) % AUDIENCES.length;
    else if (event.key === 'ArrowLeft') next = (index + AUDIENCES.length - 1) % AUDIENCES.length;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = AUDIENCES.length - 1;
    else return;
    event.preventDefault();
    setActiveIndex(next);
    tabRefs.current[next]?.focus();
  };

  return (
    <section id="persona" aria-labelledby="persona-heading" className="scroll-mt-20 border-y border-[#E1E5E8] bg-white py-20 lg:py-28">
      <div className="mx-auto w-full max-w-[1240px] px-5 sm:px-8 lg:px-16">
        <div className="mb-8 flex flex-col justify-between gap-5 md:flex-row md:items-center">
          <h2 id="persona-heading" className="text-[28px] font-semibold leading-[1.3] tracking-tight text-[#14243A] sm:text-[32px] lg:text-[36px]">
            가지는 이런 분들께 힘이 됩니다
          </h2>
          <div role="tablist" aria-label="안내 대상 선택" className="inline-flex shrink-0 items-center gap-1 self-start rounded-xl border border-[#E1E5E8] bg-[#EDF0F2] p-1 md:self-auto">
            {AUDIENCES.map((item, index) => (
              <button
                key={item.id}
                ref={(node) => { tabRefs.current[index] = node; }}
                id={`persona-tab-${item.id}`}
                type="button"
                role="tab"
                aria-selected={index === activeIndex}
                aria-controls="persona-panel"
                tabIndex={index === activeIndex ? 0 : -1}
                onClick={() => setActiveIndex(index)}
                onKeyDown={(event) => onTabKeyDown(event, index)}
                className="relative flex cursor-pointer select-none items-center justify-center rounded-[9px] px-4 py-2 text-[14px] font-semibold outline-none transition-colors duration-200 focus-visible:ring-2 focus-visible:ring-[#426B93] focus-visible:ring-offset-1 focus-visible:ring-offset-[#EDF0F2] sm:px-5 sm:py-2.5 sm:text-[15px]"
              >
                {index === activeIndex && (
                  <motion.span layoutId="audienceTabActivePill" aria-hidden="true" className="absolute inset-0 rounded-[9px] border border-[#E1E5E8] bg-white shadow-[0_1px_2px_rgba(20,36,58,0.06)]" transition={reduceMotion ? { duration: 0 } : { duration: 0.22, ease: [0.25, 0.1, 0.25, 1] }} />
                )}
                <span className={`relative z-10 ${index === activeIndex ? 'text-[#14243A]' : 'text-[#67717C] hover:text-[#14243A]'}`}>{item.label}</span>
              </button>
            ))}
          </div>
        </div>

        <div id="persona-panel" role="tabpanel" aria-labelledby={`persona-tab-${active.id}`} tabIndex={0} className="flex min-h-[460px] flex-col justify-center overflow-hidden rounded-2xl border border-[#E1E5E8] bg-[#F7F8F8] p-6 sm:p-8 lg:p-12">
          <AnimatePresence mode="wait">
          <motion.div key={active.id} initial={reduceMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={reduceMotion ? undefined : { opacity: 0, y: -6 }} transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }} className="flex flex-col items-center gap-8 lg:flex-row lg:gap-12">
            <div className="flex w-full min-w-0 flex-col justify-center lg:w-[55%]">
              <div className="mb-4"><span className="inline-block rounded-md bg-[#EDF0F2] px-3 py-1 text-xs font-semibold text-[#426B93]">{active.eyebrow}</span></div>
              <h3 className="mb-4 whitespace-pre-line text-[22px] leading-[1.38] font-semibold tracking-tight text-[#14243A] sm:text-[26px] lg:text-[28px]">{active.heading}</h3>
              <p className="mb-6 text-[16px] leading-[1.7] text-[#67717C] sm:text-[17px]">{active.description}</p>
              <ul className="space-y-3 text-[15px] text-[#202832]">
                {active.highlights.map((point) => (
                  <li key={point} className="flex items-start gap-2.5"><span aria-hidden="true" className="mt-[9px] size-1.5 shrink-0 rounded-full bg-[#547196]" /><span>{point}</span></li>
                ))}
              </ul>
            </div>
            <div className="relative aspect-[4/3] w-full min-w-0 overflow-hidden rounded-xl border border-[#E1E5E8] shadow-xs lg:w-[45%]">
              <Image src={active.image} alt={active.alt} fill sizes="(max-width: 1024px) 100vw, 45vw" className="object-cover saturate-[0.88]" />
            </div>
          </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </section>
  );
}
