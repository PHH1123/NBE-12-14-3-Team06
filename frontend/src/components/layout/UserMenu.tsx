'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  Activity, ChevronDown, CreditCard, FileText, LayoutDashboard,
  LogOut, PlusCircle, ShieldCheck, Star, UserCheck, Video,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '@/lib/cn';

export type UserMenuItem = { label: string; href: string };

type Props = {
  name: string;
  href: string;
  items?: UserMenuItem[];
  role?: 'CLIENT' | 'ESCORT' | 'ADMIN';
  transparent?: boolean;
  onLogout?: () => void;
};

const iconFor = (label: string): LucideIcon => {
  if (label.includes('정산')) return CreditCard;
  if (label.includes('리뷰')) return Star;
  if (label.includes('교육')) return Video;
  if (label.includes('지원') || label.includes('신청')) return FileText;
  if (label.includes('공고 등록') || label.includes('공고 작성')) return PlusCircle;
  if (label.includes('공고') || label.includes('회원 관리')) return UserCheck;
  if (label.includes('블랙리스트') || label.includes('검증')) return ShieldCheck;
  if (label.includes('대시보드')) return LayoutDashboard;
  if (label.includes('현황') || label.includes('동행 진행')) return Activity;
  return UserCheck;
};

export default function UserMenu({ name, href, items, role, transparent = false, onLogout }: Props) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => setOpen(false), [pathname]);

  useEffect(() => {
    if (!open) return;
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      setOpen(false);
      buttonRef.current?.focus();
    };
    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  const navigation = (items ?? []).filter((item) => item.href !== '#');
  const available = navigation.some((item) => item.href === href) ? navigation : [{ label: '내 정보', href }, ...navigation];
  const roleItems = role === 'CLIENT'
    ? [...available, ...(available.some((item) => item.href === '/client/posts/new') ? [] : [{ label: '새 동행 공고 등록', href: '/client/posts/new' }])]
    : role === 'ADMIN'
      ? [
          { label: '관리자 대시보드', href: '/admin' },
          { label: '회원 관리', href: '/admin/members' },
        ]
      : available;
  const roleLabel = role === 'CLIENT' ? '의뢰인' : role === 'ESCORT' ? '동행 매니저' : role === 'ADMIN' ? '관리자' : '';

  return (
    <div ref={rootRef} className="relative shrink-0">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-expanded={open}
        aria-haspopup="menu"
        aria-label={`${name} 계정 메뉴 ${open ? '닫기' : '열기'}`}
        className={cn(
          'flex h-[44px] items-center gap-1.5 whitespace-nowrap rounded-[10px] border px-3.5 text-[15px] font-medium transition-colors duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]',
          transparent
            ? 'border-white/30 text-white hover:bg-white/10'
            : 'border-[#E1E5E8] bg-white text-[#14243A] hover:bg-[#F7F8F8]',
        )}
      >
        <span>{name}</span>
        <ChevronDown className={cn('h-4 w-4 opacity-70 transition-transform duration-200', open && 'rotate-180')} />
      </button>

      {open && (
        <div role="menu" aria-label="계정 메뉴" className="absolute right-0 z-50 mt-2 w-60 overflow-hidden rounded-xl border border-[#E1E5E8] bg-white py-2 text-[#202832] shadow-lg">
          {roleLabel && (
            <div className="border-b border-[#E1E5E8] px-4 py-2 text-xs text-[#67717C]">
              역할: <span className="font-semibold text-[#14243A]">{roleLabel}</span>
            </div>
          )}
          {roleItems.map((item) => {
            const Icon = iconFor(item.label);
            return (
              <Link
                key={`${item.href}-${item.label}`}
                role="menuitem"
                href={item.href}
                onClick={() => setOpen(false)}
                className={cn(
                  'flex w-full items-center gap-2 px-4 py-2.5 text-left text-xs font-medium transition-colors hover:bg-[#F7F8F8]',
                  pathname === item.href ? 'font-semibold text-[#14243A]' : 'text-[#202832]',
                )}
              >
                <Icon className="h-4 w-4 shrink-0 text-[#426B93]" />
                <span>{item.label}</span>
              </Link>
            );
          })}
          {onLogout && (
            <>
              <div className="my-1 border-t border-[#E1E5E8]" />
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setOpen(false);
                  onLogout();
                }}
                className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-xs font-medium text-[#B4413C] transition-colors hover:bg-[#F7F8F8]"
              >
                <LogOut className="h-4 w-4" />
                로그아웃
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
