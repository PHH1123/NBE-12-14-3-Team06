'use client';

import Link from 'next/link';
import { useCurrentUser } from '@/features/auth';
import { usePostCreationEligibility, postCreationUnavailableReason } from '@/features/payment';

/** GAJI 원본 HomeView의 최종 CTA: 레이아웃/컬러/글로우 이식. */
export default function CtaSection() {
  const { user } = useCurrentUser();
  const availability = usePostCreationEligibility(user?.role === 'CLIENT');
  const creationBlocked = user?.role === 'CLIENT' && availability !== 'allowed';
  const postHref = !user ? '/login' : user.role === 'ESCORT' ? '/escort/posts' : '/client/posts/new';

  return (
    <section className="relative overflow-hidden bg-[#14243A] py-20 text-white lg:py-24">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0"
        style={{ background: 'radial-gradient(circle 540px at 50% 45%, rgba(255, 255, 255, 0.10) 0%, rgba(210, 225, 245, 0.045) 40%, rgba(20, 36, 58, 0) 75%)' }}
      />
      <div className="relative z-10 mx-auto max-w-[800px] px-5 text-center sm:px-8">
        <h2 className="mb-4 text-[28px] font-semibold leading-[1.25] tracking-tight text-white sm:text-[34px] lg:text-[40px]">
          가장 가까운 곳에서, 가장 차분하게.
        </h2>
        <p className="mx-auto mb-10 max-w-xl text-[16px] leading-[1.7] text-white/80 sm:text-[18px]">
          도움이 필요한 순간에도, 누군가에게 힘이 되는 순간에도 GAJI가 함께합니다.
        </p>
        <div className="flex flex-col items-stretch justify-center gap-4 sm:flex-row sm:items-center">
          {creationBlocked ? (
            <span aria-disabled="true" title={postCreationUnavailableReason(availability)} className="inline-flex h-[52px] cursor-not-allowed items-center justify-center gap-2 rounded-[10px] bg-[#426B93] px-8 text-[16px] font-semibold text-white opacity-50">
              동행 공고 올리기 <span aria-hidden="true">→</span>
            </span>
          ) : (
            <Link href={postHref} className="group/ctaPrimary inline-flex h-[52px] items-center justify-center gap-2 rounded-[10px] bg-[#426B93] px-8 text-[16px] font-semibold text-white shadow-sm transition-all duration-200 hover:bg-[#345778] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white">
              <span>동행 공고 올리기</span>
              <svg aria-hidden="true" className="h-4 w-4 transition-transform duration-200 ease-out group-hover/ctaPrimary:translate-x-1 motion-reduce:transition-none" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14m-6-6 6 6-6 6"/></svg>
            </Link>
          )}
          <Link href="/signup" className="group/ctaSecondary inline-flex h-[52px] items-center justify-center gap-2 rounded-[10px] border border-white/50 bg-transparent px-8 text-[16px] font-semibold text-white transition-all duration-200 hover:border-white/90 hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white">
            <span>동행 매니저 지원하기</span>
            <svg aria-hidden="true" className="h-4 w-4 transition-transform duration-200 ease-out group-hover/ctaSecondary:translate-x-1 motion-reduce:transition-none" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6"/></svg>
          </Link>
        </div>
      </div>
    </section>
  );
}
