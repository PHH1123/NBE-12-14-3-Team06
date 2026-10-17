'use client';

import Link from 'next/link';
import { AppShell } from '@/components/layout';
import { ROLE_OPTIONS } from '../model';
import RoleCard from './RoleCard';

export default function SignupSelectPage() {
  return (
    <AppShell>
      <div className="w-full bg-[#F7F8F8] py-16 lg:py-24">
        <div className="mx-auto max-w-[1240px] px-5 sm:px-8 lg:px-16">
          <div className="mx-auto mb-8 flex max-w-md items-center justify-center">
            <nav aria-label="계정 화면" className="flex w-full gap-1 rounded-xl border border-[#E1E5E8] bg-[#EDF0F2] p-1">
              <Link href="/login" className="flex-1 rounded-[9px] py-2.5 text-center text-[15px] font-semibold text-[#67717C] transition-colors hover:text-[#14243A]">로그인</Link>
              <span aria-current="page" className="flex-1 rounded-[9px] bg-white py-2.5 text-center text-[15px] font-semibold text-[#14243A] shadow-xs">회원가입</span>
            </nav>
          </div>
          <div className="mx-auto max-w-3xl space-y-8">
            <div className="space-y-8">
              <div className="space-y-2 text-center">
                <span className="text-xs font-semibold uppercase tracking-wider text-[#426B93]">Sign Up Step 1</span>
                <h1 className="text-2xl font-semibold text-[#14243A] sm:text-3xl">어떤 목적으로 이용하시나요?</h1>
                <p className="text-sm text-[#67717C]">회원 유형에 맞춘 맞춤형 서비스와 혜택을 제공해 드립니다.</p>
              </div>
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                {ROLE_OPTIONS.map((option) => <RoleCard key={option.role} option={option} />)}
              </div>
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
