'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useState, type ReactNode } from 'react';
import { LayoutDashboard, LogOut, Users, UserX, ShieldCheck, CreditCard, Wallet } from 'lucide-react';
import { AppShell } from '@/components/layout';
import { useAuth } from '@/features/auth/lib/AuthProvider';

type AdminMenuItem = {
  label: string;
  icon: typeof LayoutDashboard;
  href: string;
};

const NAV_TABS: AdminMenuItem[] = [
  { label: '대시보드', icon: LayoutDashboard, href: '/admin' },
  { label: '회원 관리', icon: Users, href: '/admin/members' },
  { label: '결제 관리', icon: CreditCard, href: '/admin/payments' },
  { label: '정산 관리', icon: Wallet, href: '/admin/settlements' },
  { label: '블랙리스트', icon: UserX, href: '/admin/blacklist' },
  { label: '자격 검증', icon: ShieldCheck, href: '/admin/verification' },
];

export default function AdminShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { signOut } = useAuth();
  const [loggingOut, setLoggingOut] = useState(false);

  const handleLogout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await signOut();
      router.replace('/');
    } finally {
      setLoggingOut(false);
    }
  };

  const activeHref = pathname.startsWith('/admin/members') ? '/admin/members' : pathname.startsWith('/admin/payments') ? '/admin/payments' : pathname.startsWith('/admin/settlements') ? '/admin/settlements' : pathname.startsWith('/admin/blacklist') ? '/admin/blacklist' : pathname.startsWith('/admin/verification') ? '/admin/verification' : '/admin';

  const renderMenu = (mobile: boolean) => NAV_TABS.map(({ label, icon: Icon, href }) => {
    const isActive = href === activeHref;
    const base = mobile
      ? 'flex shrink-0 items-center gap-2 rounded-lg px-3 py-2 text-xs font-semibold whitespace-nowrap transition-colors'
      : 'flex w-full items-center gap-2.5 rounded-lg px-3 py-2.5 text-left text-sm font-medium transition-colors';
    const colors = isActive
      ? 'bg-[#14243A] text-white shadow-xs'
      : 'text-[#67717C] hover:bg-[#F7F8F8] hover:text-[#14243A]';
    const icon = <Icon aria-hidden="true" className="size-4 shrink-0" />;

    return (
      <Link key={label} href={href} aria-current={isActive ? 'page' : undefined} className={`${base} ${colors}`}>
        {icon}
        <span>{label}</span>
      </Link>
    );
  });

  return (
    <AppShell>
      <div className="min-h-[calc(100vh-140px)] w-full bg-[#F7F8F8] text-[#14243A]">
        <div className="flex items-center justify-between border-b border-[#202832] bg-[#14243A] px-5 py-2 text-xs text-white sm:px-8 lg:px-16">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-bold tracking-wide">GAJI 관리자 콘솔</span>
            <span className="text-white/40">|</span>
            <span className="text-white/80">운영 및 회원 제재 관리 시스템</span>
          </div>
        </div>

        <div className="mx-auto max-w-[1400px] px-4 py-6 sm:px-6 lg:px-8">
          <nav aria-label="관리자 메뉴" className="mb-6 flex items-center gap-1 overflow-x-auto rounded-xl border border-[#E1E5E8] bg-white p-1.5 shadow-xs lg:hidden">
            {renderMenu(true)}
          </nav>
          <div className="items-start lg:grid lg:grid-cols-[240px_1fr] lg:gap-8">
            <aside className="sticky top-[120px] hidden rounded-xl border border-[#E1E5E8] bg-white p-3 shadow-xs lg:block">
              <div className="mb-2 border-b border-[#E1E5E8] px-3 py-2.5">
                <span className="block text-xs font-bold uppercase tracking-wider text-[#67717C]">관리 메뉴</span>
              </div>
              <nav aria-label="관리자 사이드 메뉴" className="space-y-1">
                {renderMenu(false)}
                <div className="mt-3 border-t border-[#E1E5E8] pt-3">
                  <button
                    type="button"
                    onClick={handleLogout}
                    disabled={loggingOut}
                    className="flex w-full items-center gap-2.5 rounded-lg px-3 py-2.5 text-left text-sm font-medium text-[#B4413C] transition-colors hover:bg-[#F7F8F8] disabled:cursor-wait disabled:opacity-60"
                  >
                    <LogOut aria-hidden="true" className="size-4 shrink-0" />
                    <span>{loggingOut ? '로그아웃 중...' : '로그아웃'}</span>
                  </button>
                </div>
              </nav>
            </aside>
            <div className="min-w-0">{children}</div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
