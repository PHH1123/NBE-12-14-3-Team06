import type { ComponentProps } from 'react';
import { cn } from '@/lib/cn';

/** Figma "Text Area" — 높이 92, 안쪽 여백 16, drop-shadow */
export default function TextArea({ className, ...props }: ComponentProps<'textarea'>) {
  return (
    <textarea
      {...props}
      className={cn(
        'min-h-[104px] w-full resize-y rounded-xl border border-[#E1E5E8] bg-white px-3.5 py-3 text-sm leading-6 text-[#14243A] shadow-none outline-none transition-colors placeholder:text-[#98A1AD] focus:border-[#426B93] focus:ring-2 focus:ring-[#426B93]/10',
        className,
      )}
    />
  );
}
