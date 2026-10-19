'use client';

import { GENDER_OPTIONS } from '../../model';
import type { SignupGender } from '../../types';

type Props = {
  value: SignupGender | '';
  onChange: (value: SignupGender) => void;
  /** 이 묶음을 설명하는 라벨(FormField)의 id */
  labelId: string;
};

/** 성별 라디오 (Figma 564:17830 — 백엔드 Gender enum 에 맞춰 남성 / 여성만 둡니다) */
export default function GenderRadioGroup({ value, onChange, labelId }: Props) {
  return (
    <div role="radiogroup" aria-labelledby={labelId} className="flex flex-wrap gap-2">
      {GENDER_OPTIONS.map((option) => {
        const checked = value === option.value;
        return (
          <label key={option.value} className={`inline-flex min-h-12 flex-1 cursor-pointer items-center justify-center rounded-xl border px-4 text-sm font-medium transition-colors ${checked ? 'border-[#14243A] bg-[#14243A] text-white' : 'border-[#E1E5E8] bg-white text-[#67717C] hover:bg-[#F7F8F8]'}`}>
            <input type="radio" name="gender" value={option.value} checked={checked} onChange={() => onChange(option.value)} required className="sr-only" />
            {option.label}
          </label>
        );
      })}
    </div>
  );
}
