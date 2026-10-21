'use client';

import Link from 'next/link';
import { ArrowRight, Home, LifeBuoy } from 'lucide-react';
import AppShell from '@/components/layout/AppShell';
import { openGajiSupport } from '@/lib/gajiSupport';

/** GAJI — 찾을 수 없는 페이지 안내 */
export default function NotFoundPage() {
  return (
    <AppShell>
      <section className="flex min-h-[calc(100svh-72px)] items-center bg-[#F7F8F8] px-5 py-14 sm:px-8 lg:py-20">
        <div className="mx-auto w-full max-w-[1240px] lg:px-16">
          <div className="mx-auto w-full max-w-[780px] rounded-2xl border border-[#E1E5E8] bg-white px-6 py-12 text-center shadow-[0_4px_20px_rgba(30,39,57,0.045)] sm:px-12 sm:py-16">
            <span className="inline-flex items-center rounded-md bg-[#EDF0F2] px-3 py-1.5 text-xs font-semibold tracking-[0.08em] text-[#426B93]">
              PAGE NOT FOUND
            </span>

            <p aria-hidden="true" className="mt-5 text-[100px] font-semibold leading-none tracking-[-0.065em] text-[#14243A] sm:text-[132px]">
              404
            </p>

            <h1 className="mt-6 text-2xl font-semibold tracking-tight text-[#14243A] sm:text-[30px]">
              페이지를 찾을 수 없어요
            </h1>
            <p className="mx-auto mt-4 max-w-[490px] text-sm leading-7 text-[#67717C] sm:text-base">
              요청하신 주소가 변경되었거나 페이지가 삭제되었을 수 있어요.
              <br className="hidden sm:block" />
              주소를 다시 확인하거나 홈페이지로 이동해 주세요.
            </p>

            <div className="mt-9 flex flex-col items-stretch justify-center gap-3 sm:flex-row sm:items-center">
              <Link
                href="/"
                className="group inline-flex h-12 items-center justify-center gap-2 rounded-[10px] bg-[#14243A] px-6 text-sm font-semibold text-white transition-colors duration-200 hover:bg-[#426B93] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
              >
                <Home className="h-[17px] w-[17px]" aria-hidden="true" />
                홈페이지로 돌아가기
                <ArrowRight className="h-4 w-4 transition-transform duration-200 ease-out group-hover:translate-x-1 motion-reduce:transform-none" aria-hidden="true" />
              </Link>
              <button
                type="button"
                onClick={openGajiSupport}
                className="inline-flex h-12 items-center justify-center gap-2 rounded-[10px] border border-[#DDE3EA] bg-white px-6 text-sm font-semibold text-[#14243A] transition-colors duration-200 hover:border-[#A5B7CA] hover:bg-[#F7F8F8] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
              >
                <LifeBuoy className="h-[17px] w-[17px] text-[#426B93]" aria-hidden="true" />
                고객센터 안내
              </button>
            </div>
          </div>
        </div>
      </section>
    </AppShell>
  );
}
