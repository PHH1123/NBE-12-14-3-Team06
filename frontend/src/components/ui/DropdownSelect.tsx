'use client';

import { Check, ChevronDown } from 'lucide-react';
import { useEffect, useRef, useState, type KeyboardEvent } from 'react';

type Option = { value: string; label: string; disabled?: boolean };

type Props = {
  id: string;
  value: string;
  options: Option[];
  onValueChange: (value: string) => void;
  name?: string;
  label?: string;
  placeholder?: string;
  required?: boolean;
  disabled?: boolean;
  size?: 'filter' | 'form';
  className?: string;
};

export default function DropdownSelect({
  id, value, options, onValueChange, name, label, placeholder,
  required = false, disabled = false, size = 'form', className = '',
}: Props) {
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const optionRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const selectedIndex = options.findIndex((option) => option.value === value);
  const displayText = selectedIndex >= 0 ? options[selectedIndex].label : placeholder || '선택해 주세요.';

  useEffect(() => {
    if (!open) return;
    const closeOutside = (event: PointerEvent) => {
      if (!wrapperRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', closeOutside);
    return () => document.removeEventListener('pointerdown', closeOutside);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const index = Math.max(0, selectedIndex);
    optionRefs.current[index]?.focus({ preventScroll: true });
    if (listRef.current) listRef.current.scrollTop = Math.max(0, index * 40 - 80);
  }, [open, selectedIndex]);

  const pick = (next: string) => {
    onValueChange(next);
    setOpen(false);
    triggerRef.current?.focus({ preventScroll: true });
  };

  const handleOptionKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      setOpen(false);
      triggerRef.current?.focus({ preventScroll: true });
      return;
    }
    if (!['ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    let target = index;
    if (event.key === 'ArrowDown') target = Math.min(index + 1, options.length - 1);
    if (event.key === 'ArrowUp') target = Math.max(index - 1, 0);
    if (event.key === 'Home') target = 0;
    if (event.key === 'End') target = options.length - 1;
    while (target !== index && options[target]?.disabled) {
      target += target > index ? 1 : -1;
    }
    const button = optionRefs.current[target];
    const list = listRef.current;
    button?.focus({ preventScroll: true });
    if (button && list) {
      if (button.offsetTop < list.scrollTop) list.scrollTop = button.offsetTop;
      if (button.offsetTop + button.offsetHeight > list.scrollTop + list.clientHeight) {
        list.scrollTop = button.offsetTop + button.offsetHeight - list.clientHeight;
      }
    }
  };

  return (
    <div ref={wrapperRef} className={`relative min-w-0 ${open ? 'z-30' : 'z-0'} ${className}`}>
      {label && <span id={`${id}-label`} className="mb-1.5 block text-xs font-semibold text-[#67717C]">{label}</span>}
      {name && (
        <select
          name={name}
          value={value}
          onChange={(event) => onValueChange(event.target.value)}
          required={required}
          disabled={disabled}
          aria-hidden="true"
          tabIndex={-1}
          onInvalid={(event) => {
            event.preventDefault();
            triggerRef.current?.focus({ preventScroll: true });
            setOpen(true);
          }}
          className="pointer-events-none absolute h-px w-px opacity-0"
        >
          <option value="">{placeholder || '선택해 주세요.'}</option>
          {options.map((option) => <option key={option.value} value={option.value} disabled={option.disabled}>{option.label}</option>)}
        </select>
      )}
      <button
        ref={triggerRef}
        id={id}
        data-field-name={name}
        type="button"
        disabled={disabled}
        aria-label={label}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? `${id}-options` : undefined}
        onClick={() => setOpen((previous) => !previous)}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            setOpen(true);
          }
          if (event.key === 'Escape') setOpen(false);
        }}
        className={`flex w-full min-w-0 cursor-pointer items-center justify-between gap-2 border border-[#E1E5E8] bg-white text-left text-sm outline-none transition-[border-color,background-color,box-shadow] duration-200 hover:border-[#BCC7D2] focus-visible:border-[#426B93] focus-visible:ring-2 focus-visible:ring-[#426B93]/20 disabled:cursor-not-allowed disabled:bg-[#F5F6F8] ${size === 'filter' ? 'h-[46px] rounded-lg px-3 text-[#202832]' : 'h-12 rounded-xl px-3.5 text-[#14243A]'}`}
      >
        <span className={`min-w-0 truncate ${selectedIndex < 0 ? 'text-[#98A1AD]' : ''}`}>{displayText}</span>
        <ChevronDown aria-hidden="true" className={`size-4 shrink-0 text-[#8793A0] transition-transform duration-200 ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && !disabled && (
        <div
          ref={listRef}
          id={`${id}-options`}
          role="listbox"
          aria-label={label || placeholder || '선택 항목'}
          className="absolute top-full left-0 z-40 mt-1.5 max-h-60 w-full overflow-y-auto overscroll-contain rounded-lg border border-[#E1E5E8] bg-white p-1 shadow-lg"
        >
          {options.map((option, index) => (
            <button
              key={option.value}
              ref={(element) => { optionRefs.current[index] = element; }}
              type="button"
              role="option"
              disabled={option.disabled}
              aria-selected={option.value === value}
              onClick={() => pick(option.value)}
              onKeyDown={(event) => handleOptionKeyDown(event, index)}
              className={`flex min-h-10 w-full items-center justify-between gap-2 rounded-md px-3 py-2 text-left text-sm outline-none transition-colors duration-150 focus-visible:bg-[#EDF0F2] disabled:cursor-not-allowed disabled:opacity-40 ${option.value === value ? 'bg-[#EDF0F2] font-semibold text-[#14243A]' : 'text-[#202832] hover:bg-[#F7F8F8]'}`}
            >
              <span>{option.label}</span>
              {option.value === value && <Check aria-hidden="true" className="size-4 shrink-0 text-[#426B93]" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
