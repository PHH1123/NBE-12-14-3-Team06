import type { ComponentProps } from 'react';

/** "중복 확인" 버튼 (Figma 564:17809) */
export default function CheckButton(props: ComponentProps<'button'>) {
  return (
    <button
      type="button"
      {...props}
      className="h-12 shrink-0 rounded-xl border border-[#14243A] bg-white px-4 text-xs font-semibold text-[#14243A] transition-colors hover:bg-[#F7F8F8] disabled:opacity-50"
    >
      중복 확인
    </button>
  );
}
