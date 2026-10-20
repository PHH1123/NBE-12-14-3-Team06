'use client';

import { ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react';
import type { ReactNode } from 'react';

export const money = (amount: number | null | undefined) => typeof amount === 'number' ? `${amount.toLocaleString('ko-KR')}원` : '—';
export const dateTime = (value: string | null | undefined) => value ? value.replace('T', ' ').slice(0, 16) : '—';

export function StatusChip({ children, tone = 'gray' }: { children: ReactNode; tone?: 'blue' | 'green' | 'red' | 'yellow' | 'gray' }) {
  const tones = {
    blue: 'border-[#426B93]/30 bg-[#E5EEF7] text-[#305879]',
    green: 'border-[#2F7D5B]/30 bg-[#E5F1EA] text-[#286849]',
    red: 'border-[#B4413C]/30 bg-[#F7ECEB] text-[#99433E]',
    yellow: 'border-[#9A6B12]/30 bg-[#F7F0DE] text-[#84601B]',
    gray: 'border-[#67717C]/25 bg-[#EEF0F2] text-[#526072]',
  };
  return <span className={`inline-flex whitespace-nowrap rounded-md border px-2.5 py-1 text-xs font-semibold ${tones[tone]}`}>{children}</span>;
}

export function AdminManageHeader({ title, description, onRefresh, busy }: { title: string; description: string; onRefresh: () => void; busy: boolean }) {
  return <div className="flex flex-col gap-4 rounded-xl border border-[#E1E5E8] bg-white p-6 shadow-xs sm:flex-row sm:items-center sm:justify-between">
    <div><h1 className="text-2xl font-bold tracking-tight text-[#14243A]">{title}</h1><p className="mt-1 text-sm text-[#67717C]">{description}</p></div>
    <button type="button" disabled={busy} onClick={onRefresh} className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-[#E1E5E8] bg-white px-4 text-sm font-semibold text-[#14243A] transition-colors hover:bg-[#F7F8F8] disabled:opacity-50"><RefreshCw className={`size-4 ${busy ? 'animate-spin' : ''}`} />새로고침</button>
  </div>;
}

export function AdminDateFilter({ start, end, setStart, setEnd, apply, disabled }: { start: string; end: string; setStart: (v: string) => void; setEnd: (v: string) => void; apply: () => void; disabled: boolean }) {
  return <form onSubmit={(event) => { event.preventDefault(); apply(); }} className="flex flex-wrap items-end gap-3 rounded-xl border border-[#E1E5E8] bg-white p-4 shadow-xs">
    <label className="space-y-1 text-xs font-semibold text-[#67717C]">시작일 <input type="date" required value={start} max={end || undefined} onChange={e => setStart(e.target.value)} className="block h-10 w-[190px] max-w-full min-w-0 rounded-lg border border-[#E1E5E8] bg-white px-3 text-sm font-medium tabular-nums text-[#14243A] [color-scheme:light] focus-visible:border-[#426B93] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#426B93]/20" /></label>
    <label className="space-y-1 text-xs font-semibold text-[#67717C]">종료일 <input type="date" required value={end} min={start || undefined} onChange={e => setEnd(e.target.value)} className="block h-10 w-[190px] max-w-full min-w-0 rounded-lg border border-[#E1E5E8] bg-white px-3 text-sm font-medium tabular-nums text-[#14243A] [color-scheme:light] focus-visible:border-[#426B93] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#426B93]/20" /></label>
    <button type="submit" disabled={disabled || !start || !end || start > end} className="h-10 rounded-lg bg-[#14243A] px-5 text-sm font-semibold text-white transition-colors hover:bg-[#1F3757] disabled:opacity-50">기간 조회</button>
  </form>;
}

export function AdminPager({ page, pages, total, onChange, disabled }: { page: number; pages: number; total: number; onChange: (value: number) => void; disabled: boolean }) {
  return <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[#E1E5E8] px-5 py-4 text-xs text-[#67717C]">
    <span>총 {total.toLocaleString()}건 · {Math.min(page + 1, Math.max(pages, 1))} / {Math.max(pages, 1)} 페이지</span>
    <div className="flex items-center gap-2">
      <button type="button" disabled={disabled || page === 0} onClick={() => onChange(page - 1)} className="inline-flex items-center gap-1 rounded-lg border border-[#E1E5E8] px-3 py-2 font-semibold text-[#14243A] hover:bg-[#F7F8F8] disabled:opacity-40"><ChevronLeft className="size-4" />이전</button>
      <button type="button" disabled={disabled || page + 1 >= pages} onClick={() => onChange(page + 1)} className="inline-flex items-center gap-1 rounded-lg border border-[#E1E5E8] px-3 py-2 font-semibold text-[#14243A] hover:bg-[#F7F8F8] disabled:opacity-40">다음<ChevronRight className="size-4" /></button>
    </div>
  </div>;
}
