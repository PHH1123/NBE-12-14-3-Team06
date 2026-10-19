'use client';

import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowLeft, ArrowRight, Check, FileText, X, ExternalLink } from 'lucide-react';
import { AppShell } from '@/components/layout';
import { useSignupRole } from '../hooks/useSignupRole';
import { AGREEMENT_GROUPS } from '../model';
import { AGREEMENT_DETAILS } from '../model/agreementDetails';
import { useSignup } from '../state/SignupContext';
import SignupStepper from './SignupStepper';

const roleLabels = { CLIENT: '의뢰인', ESCORT: '동행 매니저' } as const;

export default function SignupTermsPage() {
  const router = useRouter();
  const role = useSignupRole();
  const { agreements, setAgreements } = useSignup();
  const [showError, setShowError] = useState(false);
  const [activeDetail, setActiveDetail] = useState<string | null>(null);
  const detailCloseRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!activeDetail) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    detailCloseRef.current?.focus();
    const onEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setActiveDetail(null);
    };
    window.addEventListener('keydown', onEscape);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', onEscape);
    };
  }, [activeDetail]);
  const group = AGREEMENT_GROUPS[role];
  const requiredComplete = group.required.every((item) => !!agreements[item.id]);
  const allItems = [...group.required, ...group.optional];
  const allComplete = allItems.every((item) => !!agreements[item.id]);

  const toggle = (id: string, checked: boolean) => {
    setAgreements((prev) => ({ ...prev, [id]: checked }));
    setShowError(false);
  };

  const toggleAll = (checked: boolean) => {
    setAgreements((prev) => ({
      ...prev,
      ...Object.fromEntries(allItems.map((item) => [item.id, checked])),
    }));
    setShowError(false);
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!requiredComplete) {
      setShowError(true);
      return;
    }
    router.push(`/signup/info?role=${role}`);
  };

  return (
    <AppShell>
      <main className="bg-[#F7F8F8] px-5 pb-24 pt-12 sm:px-8 sm:pt-16">
        <div className="mx-auto max-w-3xl">
          <div className="mb-10 flex justify-center sm:mb-12">
            <SignupStepper current={2} />
          </div>

          <header className="mb-9 space-y-2 text-center sm:mb-10">
            <span className="text-xs font-semibold uppercase tracking-wider text-[#426B93]">
              {roleLabels[role]} 회원가입
            </span>
            <h1 className="text-2xl font-semibold text-[#14243A] sm:text-3xl">
              서비스 이용약관에 동의해 주세요
            </h1>
            <p className="text-sm text-[#67717C]">
              필수 약관을 확인하고 동의하면 다음 단계로 진행할 수 있습니다.
            </p>
          </header>

          <form onSubmit={submit} noValidate className="rounded-2xl border border-[#E1E5EA] bg-white p-5 shadow-[0_3px_12px_rgba(30,39,57,0.035)] sm:p-8">
            <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-[#D8E0E9] bg-[#F5F7FA] px-4 py-5 sm:px-5">
              <input
                type="checkbox"
                checked={allComplete}
                onChange={(event) => toggleAll(event.target.checked)}
                className="peer sr-only"
                aria-label="모든 약관 동의"
              />
              <span aria-hidden="true" className={`mt-0.5 grid size-6 shrink-0 place-items-center rounded-md border transition-colors peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-[#55749B] ${allComplete ? 'border-[#55749B] bg-[#55749B] text-white' : 'border-[#A7B1BE] bg-white'}`}>
                {allComplete && <Check size={16} strokeWidth={2.5} />}
              </span>
              <span className="min-w-0">
                <span className="block text-base font-semibold text-[#1E2739] sm:text-lg">전체 약관에 동의합니다</span>
                <span className="mt-1 block text-sm leading-5 text-[#667085]">필수 및 선택 약관을 모두 포함합니다. 선택 항목은 동의하지 않아도 가입할 수 있습니다.</span>
              </span>
            </label>

            <div className="mt-7 space-y-3">
              {allItems.map((item) => {
                const checked = !!agreements[item.id];
                return (
                  <div key={item.id} className={`flex flex-wrap items-center gap-3 rounded-xl border px-4 py-4 transition-[border-color,box-shadow] duration-200 hover:border-[#A5B7CA] hover:shadow-[0_3px_10px_rgba(30,39,57,0.045)] sm:flex-nowrap sm:px-5 ${checked ? 'border-[#C7DAEC]' : 'border-[#E1E5EA]'}`}>
                    <label className="flex min-w-0 flex-1 cursor-pointer items-start gap-3">
                      <input
                        type="checkbox"
                        name={`agree-${item.id}`}
                        checked={checked}
                        onChange={(event) => toggle(item.id, event.target.checked)}
                        className="peer sr-only"
                        aria-label={item.title}
                      />
                      <span aria-hidden="true" className={`mt-0.5 grid size-6 shrink-0 place-items-center rounded-md border transition-colors peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-[#55749B] ${checked ? 'border-[#55749B] bg-[#55749B] text-white' : 'border-[#A7B1BE] bg-white'}`}>
                        {checked && <Check size={16} strokeWidth={2.5} />}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap items-center gap-2">
                          <span className={`inline-flex shrink-0 items-center rounded-md px-2 py-1 text-xs font-semibold leading-none ${item.required ? 'bg-[#FCEEEE] text-[#B54747]' : 'bg-[#F0F2F5] text-[#667085]'}`}>{item.required ? '필수' : '선택'}</span>
                          <span className="text-sm font-semibold text-[#1E2739] sm:text-base">{item.title}</span>
                        </span>
                        <span className="mt-1 block text-sm leading-6 text-[#667085]">{item.description}</span>
                      </span>
                    </label>
                    <button
                      type="button"
                      onClick={() => setActiveDetail(item.id)}
                      aria-label={`${item.title} 내용 보기`}
                      className="ml-9 inline-flex h-9 shrink-0 items-center justify-center gap-1 rounded-lg border border-[#DDE3EA] bg-white px-3 text-xs font-semibold text-[#426B93] transition-colors hover:border-[#A5B7CA] hover:bg-[#EDF3F8] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93] sm:ml-0"
                    >
                      <FileText size={14} aria-hidden="true" /> 내용 보기
                    </button>
                  </div>
                );
              })}
            </div>

            {showError && (
              <p role="alert" className="mt-4 text-sm font-medium text-[#B34747]">필수 약관에 모두 동의해 주세요.</p>
            )}

            <div className="mt-8 flex flex-col-reverse gap-3 border-t border-[#E9ECEF] pt-6 sm:flex-row sm:justify-between">
              <button
                type="button"
                onClick={() => router.push('/signup')}
                className="inline-flex h-12 items-center justify-center gap-2 rounded-xl border border-[#DDE3EA] bg-white px-6 text-sm font-semibold text-[#344055] transition-colors hover:bg-[#F5F6F8]"
              >
                <ArrowLeft size={17} /> 이전 단계
              </button>
              <button
                type="submit"
                aria-disabled={!requiredComplete}
                className={`group inline-flex h-12 items-center justify-center gap-2 rounded-xl px-6 text-sm font-semibold text-white transition-colors ${requiredComplete ? 'bg-[#1E2739] hover:bg-[#344055]' : 'bg-[#79879A] hover:bg-[#66758A]'}`}
              >
                동의하고 계속하기
                <ArrowRight size={17} className="transition-transform duration-200 group-hover:translate-x-1" />
              </button>
            </div>
          </form>
        </div>

        {activeDetail && AGREEMENT_DETAILS[activeDetail] && (
          <div
            className="fixed inset-0 z-[100] flex items-center justify-center bg-black/65 p-4 sm:p-6"
            onMouseDown={(event) => { if (event.target === event.currentTarget) setActiveDetail(null); }}
          >
            <section
              role="dialog"
              aria-modal="true"
              aria-labelledby="agreement-detail-title"
              aria-describedby="agreement-detail-summary"
              className="flex max-h-[min(86svh,760px)] w-full max-w-[700px] flex-col overflow-hidden rounded-2xl border border-[#E1E5E8] bg-white shadow-[0_24px_70px_rgba(14,23,39,0.22)]"
            >
              <header className="flex items-start justify-between gap-4 border-b border-[#E9ECEF] px-5 py-5 sm:px-7">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wider text-[#426B93]">GAJI AGREEMENTS</p>
                  <h2 id="agreement-detail-title" className="mt-1 text-xl font-semibold text-[#14243A]">
                    {AGREEMENT_DETAILS[activeDetail].title}
                  </h2>
                </div>
                <button
                  ref={detailCloseRef}
                  type="button"
                  aria-label="약관 내용 닫기"
                  onClick={() => setActiveDetail(null)}
                  className="grid h-9 w-9 shrink-0 place-items-center rounded-lg text-[#67717C] transition-colors hover:bg-[#EDF0F2] hover:text-[#14243A] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
                >
                  <X size={20} aria-hidden="true" />
                </button>
              </header>
              <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-5 py-6 sm:px-7">
                <p id="agreement-detail-summary" className="rounded-xl border border-[#E1E5E8] bg-[#F5F6F8] px-4 py-3 text-sm leading-6 text-[#596777]">
                  이 안내문은 서비스 정책 검토용 초안입니다. 실제 운영 주체·정보 제공 범위·보유 기간·시행일은 최종 확인이 필요합니다.
                </p>
                {AGREEMENT_DETAILS[activeDetail].sections.map((section) => (
                  <section key={section.title} className="space-y-3">
                    <h3 className="text-base font-semibold text-[#14243A]">{section.title}</h3>
                    <ul className="space-y-2 pl-5 text-sm leading-7 text-[#596777] [list-style:disc] marker:text-[#55749B]">
                      {section.items.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </section>
                ))}
                {AGREEMENT_DETAILS[activeDetail].privacyLink && (
                  <a
                    href="/privacy"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-2 text-sm font-semibold text-[#426B93] underline-offset-4 hover:underline"
                  >
                    개인정보 처리방침 전체 보기 <ExternalLink size={15} aria-hidden="true" />
                  </a>
                )}
              </div>
            </section>
          </div>
        )}
      </main>
    </AppShell>
  );
}
