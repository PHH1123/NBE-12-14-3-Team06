'use client';

import Link from 'next/link';
import { ArrowRight, Lock } from 'lucide-react';

type Props = {
  nextPath?: string;
};

export default function PostGuestNotice({ nextPath = '/posts' }: Props) {
  return (
    <section className="w-full bg-[#F7F8F8] py-20 lg:py-28">
      <div className="mx-auto max-w-[680px] px-5 text-center">
        <div className="mx-auto mb-6 flex size-16 items-center justify-center rounded-2xl bg-[#14243A]/10 text-[#426B93]">
          <Lock aria-hidden="true" className="size-8" />
        </div>
        <span className="mb-2 block text-xs font-semibold uppercase tracking-wider text-[#426B93]">
          회원 전용 서비스
        </span>
        <h1 className="mb-4 text-2xl font-semibold tracking-tight text-[#14243A] sm:text-[28px] md:whitespace-nowrap">
          동행 공고는 로그인 후 확인하실 수 있습니다
        </h1>
        <p className="mb-8 text-[15px] leading-relaxed text-[#67717C]">
          <span className="block">의뢰인의 진료 일정과 개인정보를 보호하기 위해</span>
          <span className="block">가지 로그인 회원에게만 동행 공고 목록이 공개됩니다.</span>
        </p>
        <div className="flex flex-col items-stretch justify-center gap-3 sm:flex-row sm:items-center">
          <Link
            href={`/login?next=${encodeURIComponent(nextPath)}`}
            className="group flex h-12 items-center justify-center gap-2 rounded-[10px] bg-[#14243A] px-6 text-sm font-semibold text-white transition-colors duration-200 ease-out hover:bg-[#1F3757] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93] motion-reduce:transition-none"
          >
            로그인하기
            <ArrowRight aria-hidden="true" className="size-4 transition-transform duration-200 group-hover:translate-x-0.5 motion-reduce:transform-none" />
          </Link>
          <Link
            href="/signup"
            className="flex h-12 items-center justify-center rounded-[10px] border border-[#E1E5E8] bg-white px-6 text-sm font-semibold text-[#14243A] transition-colors duration-200 ease-out hover:border-[#B9C6D2] hover:bg-[#EDF0F2] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93] motion-reduce:transition-none"
          >
            회원가입
          </Link>
        </div>
      </div>
    </section>
  );
}
