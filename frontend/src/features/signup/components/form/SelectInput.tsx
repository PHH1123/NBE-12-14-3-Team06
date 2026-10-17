import type { ComponentProps } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/cn';
import { FIELD_CLASS } from './fieldStyle';

export default function SelectInput({ className, children, style, ...props }: ComponentProps<'select'>) {
  return (
    <div className="relative w-full min-w-0">
      <select
        {...props}
        style={{ ...style, color: props.value ? '#14243A' : '#98A1AD' }}
        className={cn(FIELD_CLASS, 'appearance-none pr-11', className)}
      >
        {children}
      </select>
      <ChevronDown
        size={18}
        strokeWidth={1.8}
        className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-[#8793A0]"
        aria-hidden="true"
      />
    </div>
  );
}
