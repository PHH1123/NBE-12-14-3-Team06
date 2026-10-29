'use client';

import { motion, useReducedMotion } from 'motion/react';

import Image from 'next/image';
import Link from 'next/link';
import { ArrowRight, CheckCircle2 } from 'lucide-react';
import { useCurrentUser } from '@/features/auth';
import { usePostCreationEligibility, postCreationUnavailableReason } from '@/features/payment';

/** GAJI 히어로. API/인증 구조를 유지하며 UI만 교체합니다. */
const easeOutCubic = [0.22, 1, 0.36, 1] as const;

export default function HeroSection() {
  const { user } = useCurrentUser();
  const shouldReduceMotion = useReducedMotion();
  const isEscort = user?.role === 'ESCORT';
  const availability = usePostCreationEligibility(user?.role === 'CLIENT');
  const creationBlocked = user?.role === 'CLIENT' && availability !== 'allowed';
  const requestHref = isEscort ? '/escort/posts' : user ? '/client/posts/new' : '/login';
  const browseHref = isEscort ? '/#report' : user ? '/posts' : '/login';

  return (
    <section id="hero" aria-labelledby="hero-title" className="relative isolate flex min-h-[640px] w-full items-center overflow-hidden bg-[#171C23] lg:min-h-[720px]">
      <Image
        src="/images/hero/hero_atrium.jpg"
        alt="햇살이 비치는 현대적인 병원 아트리움"
        fill
        priority
        sizes="100vw"
        className="object-cover object-center saturate-[0.85] brightness-[0.95]"
      />
      <div aria-hidden="true" className="absolute inset-0 bg-[linear-gradient(90deg,rgba(23,28,35,0.92)_0%,rgba(20,36,58,0.78)_55%,rgba(20,36,58,0.40)_100%)]" />
      <div className="relative z-10 mx-auto w-full max-w-[1240px] px-5 py-24 sm:px-8 lg:px-16 lg:py-32">
        <div className="max-w-2xl">
          <motion.span initial={shouldReduceMotion ? false : { opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.45, delay: 0.05, ease: easeOutCubic }} className="mb-4 block text-sm font-semibold tracking-wider text-[#EDF0F2]/80 md:text-base">병원 동행 서비스 GAJI</motion.span>
          <motion.h1 initial={shouldReduceMotion ? false : { opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.55, delay: 0.15, ease: easeOutCubic }} id="hero-title" className="mb-6 text-[38px] font-semibold leading-[1.15] tracking-tight text-white sm:text-[48px] lg:text-[64px]">
            오늘 병원,<br />누구랑 가지?
          </motion.h1>
          <motion.p initial={shouldReduceMotion ? false : { opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.55, delay: 0.25, ease: easeOutCubic }} className="mb-10 max-w-xl text-[17px] leading-[1.7] text-[#EDF0F2]/90 sm:text-[18px]">
            믿을 수 있는 동행 매니저와 함께, 병원 가는 길부터 진료까지 안전하게 동행합니다.
          </motion.p>
          <motion.div initial={shouldReduceMotion ? false : { opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5, delay: 0.35, ease: easeOutCubic }} className="flex flex-col items-stretch gap-4 sm:flex-row sm:items-center">
            {creationBlocked ? (
              <span aria-disabled="true" title={postCreationUnavailableReason(availability)} className="inline-flex h-[52px] cursor-not-allowed items-center justify-center gap-2 rounded-[10px] bg-[#426B93] px-8 text-base font-semibold text-white opacity-50">
                동행 요청하기 <ArrowRight aria-hidden="true" className="h-4 w-4" />
              </span>
            ) : (
              <Link href={requestHref} className="group inline-flex h-[52px] items-center justify-center gap-2 rounded-[10px] bg-[#426B93] px-8 text-base font-semibold text-white shadow-xs transition-all duration-200 hover:bg-[#345778] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white">
                <span>{isEscort ? '공고 찾아보기' : '동행 요청하기'}</span><ArrowRight aria-hidden="true" className="h-4 w-4 transition-transform duration-200 ease-out group-hover:translate-x-0.5" />
              </Link>
            )}
            <Link
              href={browseHref}
              className="inline-flex h-[52px] items-center justify-center rounded-[10px] border border-white/60 bg-transparent px-6 text-base font-semibold text-white transition-all duration-200 hover:border-white hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white"
            >
              {isEscort ? '보고서 작성 가이드' : '공고 찾아보기'}
            </Link>
          </motion.div>
          <motion.div
            initial={shouldReduceMotion ? false : { opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.5, delay: 0.45, ease: easeOutCubic }}
            className="mt-12 flex flex-wrap items-center gap-6 border-t border-white/15 pt-6 text-xs text-white/70"
          >
            <span className="inline-flex items-center gap-1.5"><CheckCircle2 aria-hidden="true" className="h-4 w-4 text-[#2F7D5B]" />신원·자격 검증 완료</span>
            <span aria-hidden="true">·</span>
            <span className="inline-flex items-center gap-1.5"><CheckCircle2 aria-hidden="true" className="h-4 w-4 text-[#2F7D5B]" />2026 최저시급 준수 (시급 10,320원 이상)</span>
            <span aria-hidden="true">·</span>
            <span className="inline-flex items-center gap-1.5"><CheckCircle2 aria-hidden="true" className="h-4 w-4 text-[#2F7D5B]" />AI 요약 진료 보고서 제공</span>
          </motion.div>
        </div>
      </div>
    </section>
  );
}
