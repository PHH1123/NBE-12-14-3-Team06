'use client';

import { Check } from 'lucide-react';
import { cn } from '@/lib/cn';

// 가입 유형 선택 다음부터 약관 동의, 정보 입력, 가입 완료 순서로 표시합니다.
// 기존 페이지의 current 값(약관=2, 정보=3, 완료=4)을 유지합니다.
const STEPS = ['약관 동의', '정보 입력', '가입 완료'] as const;

type Props = { current: number };

export default function SignupStepper({ current }: Props) {
  const activeStep = Math.min(3, Math.max(1, current - 1));

  return (
    <ol
      aria-label="회원가입 진행 상황"
      className="flex w-full max-w-[420px] items-center justify-between gap-1.5 sm:gap-2"
    >
      {STEPS.map((title, index) => {
        const step = index + 1;
        const completed = step < activeStep;
        const active = step === activeStep;

        return (
          <li key={title} className="contents">
            {index > 0 && (
              <span
                aria-hidden="true"
                className="h-px min-w-2 flex-1 bg-[#DDE3EA] sm:max-w-9"
              />
            )}
            <span
              aria-current={active ? 'step' : undefined}
              className="flex shrink-0 items-center gap-1.5 sm:gap-2"
            >
              <span
                className={cn(
                  'grid size-7 shrink-0 place-items-center rounded-full text-xs font-semibold transition-colors sm:size-[30px] sm:text-sm',
                  active && 'bg-[#1E2739] text-white',
                  completed && 'bg-[#DCE7F1] text-[#55749B]',
                  !completed && !active && 'bg-[#E9ECEF] text-[#8792A2]'
                )}
              >
                {completed ? <Check size={16} strokeWidth={2.5} aria-label="완료" /> : step}
              </span>
              <span
                className={cn(
                  'whitespace-nowrap text-[11px] leading-5 sm:text-sm',
                  active ? 'font-semibold text-[#1E2739]' :
                  completed ? 'font-medium text-[#344055]' :
                  'font-medium text-[#748093]'
                )}
              >
                {title}
              </span>
            </span>
          </li>
        );
      })}
    </ol>
  );
}
