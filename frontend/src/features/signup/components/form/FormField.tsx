import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

type Props = {
  label: string;
  /** 입력창 id. 없으면 라디오 묶음처럼 <span> 라벨을 씁니다. */
  htmlFor?: string;
  /** htmlFor 가 없을 때, 묶음이 aria-labelledby 로 참조할 라벨 id */
  labelId?: string;
  className?: string;
  children: ReactNode;
};

/** 입력 항목의 라벨과 필드 영역 */
export default function FormField({ label, htmlFor, labelId, className, children }: Props) {
  const labelClass = 'text-xs font-semibold text-[#67717C]';
  const labelContent = label.endsWith('*') ? <>{label.slice(0, -1)} <span className="text-[#B54747]">*</span></> : label;
  return (
    <div className={cn('flex min-w-0 flex-col gap-2', className)}>
      {htmlFor ? (
        <label htmlFor={htmlFor} className={labelClass}>
          {labelContent}
        </label>
      ) : (
        <span id={labelId} className={labelClass}>
          {labelContent}
        </span>
      )}
      {children}
    </div>
  );
}
