'use client';

import { useState, type ChangeEvent, type FormEvent } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { login } from '../api';
import { useAuth } from '../lib/AuthProvider';
import { LOGIN_HOME_BY_ROLE } from '../model';
import type { LoginFormValues } from '../types';
import { UserRound, LockKeyhole, Eye, EyeOff, ArrowRight, AlertCircle } from 'lucide-react';

const FIELD_CLASS = 'h-[56px] w-full rounded-xl border border-[#DCE2E9] bg-white pl-12 pr-12 text-base text-[#1E2739] outline-none transition-colors placeholder:text-[#9AA3B2] focus:border-[#55749B] focus:ring-[3px] focus:ring-[#55749B]/10';

const ERROR_TEXT = 'px-4 text-sm leading-5 font-medium text-[#b91d1d]';

const INITIAL_VALUES: LoginFormValues = { username: '', password: '' };

/** 로그인 폼 카드 안쪽 (Figma 공통_로그인 564:17969) */
export default function LoginForm() {
  const router = useRouter();
  // 로그인이 필요해서 밀려온 경우 원래 가려던 주소가 ?next= 로 넘어옵니다.
  const next = useSearchParams().get('next');
  const { reload } = useAuth();

  const [values, setValues] = useState<LoginFormValues>(INITIAL_VALUES);
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [submitError, setSubmitError] = useState('');

  const handleChange =
    (key: 'username' | 'password') => (event: ChangeEvent<HTMLInputElement>) => {
      setValues((prev) => ({ ...prev, [key]: event.target.value }));
    };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    setSubmitting(true);
    setSubmitError('');
    try {
      await login(values);

      // 로그인 응답에는 역할이 없어서, 세션을 불러와 역할에 맞는 첫 화면으로 보냅니다.
      const user = await reload();
      const home = user ? LOGIN_HOME_BY_ROLE[user.role] : '/';

      // 뒤로 가기로 로그인 화면에 다시 돌아오지 않도록 replace 로 이동합니다.
      router.replace(next ?? home);
    } catch (error) {
      // 아이디·비밀번호 불일치(401) 등 실패 사유는 백엔드 문구를 그대로 보여 줍니다.
      setSubmitError(error instanceof Error ? error.message : '로그인에 실패했습니다.');
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="text-center space-y-1">
        <span className="text-xs font-semibold text-[#426B93] uppercase tracking-wider">GAJI</span>
        <h1 className="text-2xl font-semibold text-[#14243A]">서비스 로그인</h1>
        <p className="text-xs text-[#67717C]">GAJI와 함께 안심할 수 있는 병원 동행을 시작해 보세요.</p>
      </div>

      {submitError && (
        <div role="alert" className="p-3 bg-[#B4413C]/10 border border-[#B4413C]/20 text-[#B4413C] rounded-lg text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /><span>{submitError}</span>
        </div>
      )}
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="login-username" className="block text-xs font-semibold text-[#67717C] mb-1.5">아이디</label>
          <div className="relative">
            <UserRound aria-hidden="true" className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-[#747E8D] pointer-events-none" />
            <input id="login-username" name="username" autoComplete="username" required
              type="text" value={values.username} onChange={handleChange('username')}
              placeholder="아이디를 입력하세요"
              className="w-full h-[48px] pl-10 pr-3.5 bg-white border border-[#E1E5E8] rounded-xl text-sm text-[#14243A] placeholder:text-[#9AA3B2] focus:border-[#426B93] focus:outline-2 focus:outline-offset-1 focus:outline-[#426B93]/30" />
          </div>
        </div>
        <div>
          <label htmlFor="login-password" className="block text-xs font-semibold text-[#67717C] mb-1.5">비밀번호</label>
          <div className="relative">
            <LockKeyhole aria-hidden="true" className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-[#747E8D] pointer-events-none" />
            <input id="login-password" name="password" type={showPassword ? 'text' : 'password'}
              autoComplete="current-password" required value={values.password} onChange={handleChange('password')}
              placeholder="비밀번호를 입력하세요"
              className="w-full h-[48px] pl-10 pr-11 bg-white border border-[#E1E5E8] rounded-xl text-sm text-[#14243A] placeholder:text-[#9AA3B2] focus:border-[#426B93] focus:outline-2 focus:outline-offset-1 focus:outline-[#426B93]/30" />
            <button type="button" onClick={() => setShowPassword((v) => !v)} aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 표시'}
              aria-pressed={showPassword} className="absolute right-2 top-1/2 -translate-y-1/2 grid size-9 place-items-center rounded-lg text-[#747E8D] hover:text-[#14243A] focus-visible:outline-2 focus-visible:outline-[#426B93]">
              {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
            </button>
          </div>
        </div>
        <button type="submit" disabled={submitting}
          className="group w-full h-[52px] bg-[#14243A] text-white font-semibold text-base rounded-[10px] hover:bg-[#1f3757] transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93] shadow-xs mt-2 disabled:cursor-not-allowed disabled:opacity-60 flex items-center justify-center gap-2">
          {submitting ? '로그인 중...' : '로그인하기'}
          {!submitting && <ArrowRight aria-hidden="true" size={17} className="transition-transform duration-200 group-hover:translate-x-1.5" />}
        </button>
      </form>
    </div>
  );
}
