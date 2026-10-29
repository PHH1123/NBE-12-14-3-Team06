'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { CheckCircle2, ArrowRight } from 'lucide-react';
import { AppShell } from '@/components/layout';
import { useCurrentUser } from '@/features/auth/lib/useCurrentUser';
import { useSignupRole } from '../hooks/useSignupRole';
import { useSignup } from '../state/SignupContext';
import SignupStepper from './SignupStepper';

type CompletedSignup = { role: 'CLIENT' | 'ESCORT'; name: string; username: string };

const ROLE_LABEL = { CLIENT: '의뢰인', ESCORT: '동행 매니저' } as const;

const NEXT_STEP = {
  CLIENT: { href: '/client/posts/new', label: '공고 작성하러 가기' },
  ESCORT: { href: '/escort/posts', label: '공고 찾아보기' },
} as const;

export default function SignupCompletePage() {
  const role = useSignupRole();
  const { values } = useSignup();
  const { user } = useCurrentUser();
  const [completed, setCompleted] = useState<CompletedSignup | null>(null);

  useEffect(() => {
    try {
      const stored = sessionStorage.getItem('gaji:signup:completed');
      if (!stored) return;
      const parsed: unknown = JSON.parse(stored);
      if (
        typeof parsed === 'object' && parsed !== null &&
        'role' in parsed && parsed.role === role &&
        'name' in parsed && typeof parsed.name === 'string' &&
        'username' in parsed && typeof parsed.username === 'string'
      ) {
        setCompleted(parsed as CompletedSignup);
      }
    } catch {
      // 브라우저 저장소에 접근할 수 없다면 현재 가입 상태로 안내합니다.
    }
  }, [role]);

  const signedInUser = user?.role === role ? user : null;
  const displayName = completed?.name || values.name.trim() || signedInUser?.name || '';
  const displayUsername = completed?.username || values.username.trim() || signedInUser?.username || '';

  return (
    <AppShell>
      <section className="bg-[#F7F8F8] px-5 pb-24 pt-12 sm:px-8 sm:pt-16">
        <div className="mx-auto flex max-w-3xl flex-col items-center">
          <div className="mb-10 flex w-full justify-center sm:mb-12">
            <SignupStepper current={4} />
          </div>

          <div className="w-full max-w-md space-y-6 rounded-2xl border border-[#E1E5E8] bg-white p-6 text-center shadow-[0_3px_12px_rgba(20,36,58,0.035)] sm:p-8">
            <div className="mx-auto flex size-16 items-center justify-center rounded-full bg-[#2F7D5B]/10 text-[#2F7D5B]">
              <CheckCircle2 className="size-8" strokeWidth={1.8} aria-hidden="true" />
            </div>

            <div className="space-y-2">
              <h1 className="text-2xl font-semibold tracking-tight text-[#14243A]">
                회원가입이 완료되었습니다
              </h1>
              <p className="text-sm leading-6 text-[#67717C]">
                {displayName ? `${displayName} 님, ` : ''}GAJI에 오신 것을 진심으로 환영합니다.
              </p>
            </div>

            <dl className="space-y-3 rounded-xl border border-[#E1E5E8] bg-[#F7F8F8] p-4 text-left text-sm">
              <div className="flex items-start gap-4">
                <dt className="w-20 shrink-0 text-[#67717C]">가입 역할</dt>
                <dd className="min-w-0 break-words font-semibold text-[#14243A]">{ROLE_LABEL[role]}</dd>
              </div>
              <div className="flex items-start gap-4">
                <dt className="w-20 shrink-0 text-[#67717C]">계정 아이디</dt>
                <dd className="min-w-0 break-all font-semibold text-[#14243A]">{displayUsername || '-'}</dd>
              </div>
            </dl>

            <div className="space-y-3">
              <Link
                href={NEXT_STEP[role].href}
                className="group flex h-[52px] w-full items-center justify-center gap-2 rounded-[10px] bg-[#14243A] px-6 text-sm font-semibold text-white transition-colors hover:bg-[#1F3757] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
              >
                {NEXT_STEP[role].label}
                <ArrowRight className="size-4 transition-transform duration-200 group-hover:translate-x-1" aria-hidden="true" />
              </Link>
              <Link
                href="/"
                className="flex h-12 w-full items-center justify-center rounded-[10px] border border-[#E1E5E8] bg-white px-6 text-sm font-semibold text-[#67717C] transition-colors hover:bg-[#F7F8F8] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
              >
                메인 페이지로 가기
              </Link>
            </div>
          </div>
        </div>
      </section>
    </AppShell>
  );
}
