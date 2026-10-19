'use client';

import Link from 'next/link';
import { AppShell } from '@/components/layout';
import LoginForm from './LoginForm';

export default function LoginPage() {
  return (
    <AppShell>
      <div className="w-full py-16 lg:py-24 bg-[#F7F8F8]">
        <div className="max-w-[1240px] mx-auto px-5 sm:px-8 lg:px-16">
          <div className="max-w-md mx-auto mb-8 flex items-center justify-center">
            <nav aria-label="계정 화면" className="p-1 bg-[#EDF0F2] rounded-xl border border-[#E1E5E8] flex gap-1 w-full">
              <span aria-current="page" className="flex-1 py-2.5 rounded-[9px] text-[15px] font-semibold bg-white text-[#14243A] shadow-xs text-center">로그인</span>
              <Link href="/signup" className="flex-1 py-2.5 rounded-[9px] text-[15px] font-semibold text-[#67717C] hover:text-[#14243A] transition-colors text-center">회원가입</Link>
            </nav>
          </div>
          <div className="max-w-md mx-auto bg-white rounded-2xl border border-[#E1E5E8] p-8 shadow-xs space-y-6">
            <LoginForm />
          </div>
        </div>
      </div>
    </AppShell>
  );
}
