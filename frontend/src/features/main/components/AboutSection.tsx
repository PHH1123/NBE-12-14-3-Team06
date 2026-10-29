'use client';

import { motion, useReducedMotion } from 'motion/react';

import Image from 'next/image';

/** 서비스 신뢰 기준 섹션. 기존 인증/API 구조와 독립적인 UI입니다. */
const trustPoints = [
  {
    number: '01',
    title: '신원 확인된 매니저와 안전하게 동행해요',
    description: '휴대폰 본인 인증과 기본 신원 확인, 안전 수칙 교육을 이수한 매니저가 함께합니다.',
  },
  {
    number: '02',
    title: '믿을 수 있는 동행 매니저가 함께해요',
    description: '실제 이용자의 리뷰와 평점, 동행 완료 횟수를 확인하고 안심하고 선택할 수 있습니다.',
  },
  {
    number: '03',
    title: '몇 번의 클릭만으로 쉽게 요청할 수 있어요',
    description: '방문할 병원과 날짜만 입력하면 인근 매니저들이 지원하며, 보호자는 프로필과 평점을 확인하고 직접 승인할 수 있습니다.',
  },
];

const easeOutCubic = [0.22, 1, 0.36, 1] as const;

export default function AboutSection() {
  const shouldReduceMotion = useReducedMotion();
  return (
    <section id="about" aria-labelledby="trust-title" className="bg-[#F7F8F8] py-24 lg:py-32 scroll-mt-[72px]">
      <div className="mx-auto max-w-[1240px] px-5 sm:px-8 lg:px-16">
        <div className="grid grid-cols-1 items-center gap-12 lg:grid-cols-12 lg:gap-16">
          <motion.div initial={shouldReduceMotion ? false : { opacity: 0, scale: 0.98 }} whileInView={{ opacity: 1, scale: 1 }} viewport={{ once: true, amount: 0.2 }} transition={{ duration: 0.55, ease: easeOutCubic }} className="relative aspect-[4/3] overflow-hidden rounded-2xl border border-[#E1E5E8] bg-[#14243A] shadow-sm lg:col-span-6">
            <Image
              src="/images/about/lounge_architecture.jpg"
              alt="단정한 원목과 부드러운 조명이 어우러진 의료 상담 라운지"
              fill
              sizes="(min-width: 1024px) 50vw, 100vw"
              className="object-cover saturate-[0.88]"
            />
          </motion.div>
          <div className="space-y-8 lg:col-span-6">
            <motion.h2 initial={shouldReduceMotion ? false : { opacity: 0, y: 14 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.2 }} transition={{ duration: 0.5, ease: easeOutCubic }} id="trust-title" className="whitespace-pre-line text-[32px] font-semibold leading-[1.3] tracking-tight text-[#14243A] sm:text-[36px]">
              {'안심할 수 있는 기준으로\n병원 동행을 돕습니다'}
            </motion.h2>
            <div className="divide-y divide-[#E1E5E8]">
              {trustPoints.map(({ number, title, description }, index) => (
                <motion.div key={number} initial={shouldReduceMotion ? false : { opacity: 0, y: 12 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.2 }} transition={{ duration: 0.45, delay: 0.08 * (index + 1), ease: easeOutCubic }} className="py-6 first:pt-0 last:pb-0">
                  <div className="mb-2 flex items-baseline gap-4">
                    <span className="font-mono text-xl font-semibold text-[#426B93]">{number}</span>
                    <h3 className="text-xl font-semibold text-[#14243A]">{title}</h3>
                  </div>
                  <p className="pl-9 text-[16px] leading-[1.65] text-[#67717C]">{description}</p>
                </motion.div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
