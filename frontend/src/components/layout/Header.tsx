'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { PlusCircle, Menu, X } from 'lucide-react';
import Container from '@/components/ui/Container';
import { NotificationBell } from '@/features/notification';
import { cn } from '@/lib/cn';
import { openGajiSupport } from '@/lib/gajiSupport';
import { usePostCreationEligibility, postCreationUnavailableReason } from '@/features/payment';
import UserMenu, { type UserMenuItem } from './UserMenu';

type MenuItem = { label: string; href: string };
const MENU: MenuItem[] = [
  { label: '서비스 소개', href: '/' },
  { label: '공고 찾기', href: '/posts' },
  { label: '이용 방법', href: '/#steps' },
  { label: '고객센터', href: '/support' },
];

type Props = {
  user?: { name: string; href?: string; menu?: UserMenuItem[] };
  role?: 'CLIENT' | 'ESCORT' | 'ADMIN';
  pending?: boolean;
  onLogout?: () => void;
  canRegisterPost?: boolean;
  isEscort?: boolean;
};

export default function Header({ user, role, pending, onLogout, canRegisterPost, isEscort }: Props) {
  const pathname = usePathname();
  const isHome = pathname === '/';
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    const update = () => setScrolled(window.scrollY > 24);
    update();
    window.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);
    return () => {
      window.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
    };
  }, [pathname]);

  useEffect(() => setMobileOpen(false), [pathname]);

  const creationAvailability = usePostCreationEligibility(role === 'CLIENT' && !!user);
  const creationBlocked = role === 'CLIENT' && creationAvailability !== 'allowed';
  const creationReason = postCreationUnavailableReason(creationAvailability);

  const transparent = isHome && !scrolled && !mobileOpen;
  const resolveHref = (item: MenuItem) =>
    item.label === '공고 찾기' && isEscort ? '/escort/posts' : item.href;
  const isActive = (item: MenuItem) => {
    if (item.label === '서비스 소개') return pathname === '/';
    if (item.label === '공고 찾기') return pathname === '/posts' || pathname.startsWith('/posts/') || pathname.startsWith('/escort/posts');
    if (item.label === '고객센터') return pathname === '/support';
    return false;
  };

  const menuLinks = (mobile = false) => MENU.map((item) => {
    const className = cn(
      'relative whitespace-nowrap py-1 text-[16px] font-medium transition-colors duration-200',
      mobile
        ? 'text-[#67717C] hover:text-[#14243A]'
        : transparent
          ? 'text-white/80 hover:text-white'
          : 'text-[#67717C] hover:text-[#14243A]',
      isActive(item) && (transparent && !mobile ? 'font-semibold text-white' : 'font-semibold text-[#14243A]'),
    );

    if (item.label === '고객센터') {
      return (
        <button
          key={item.label}
          type="button"
          className={className}
          onClick={() => { setMobileOpen(false); openGajiSupport(); }}
        >
          {item.label}
        </button>
      );
    }

    return (
      <Link
        key={item.label}
        href={resolveHref(item)}
        onClick={() => setMobileOpen(false)}
        aria-current={isActive(item) ? 'page' : undefined}
        className={className}
      >
        {item.label}
        {isActive(item) && !mobile && (
          <span className={cn('absolute inset-x-0 bottom-0 h-[2px] rounded-full', transparent ? 'bg-white' : 'bg-[#426B93]')} />
        )}
      </Link>
    );
  });

  const accountName = user && (
    role === 'CLIENT'
      ? `${user.name.replace(/\s*님$/, '')} 님`
      : role === 'ESCORT'
        ? `${user.name.replace(/\s*매니저$/, '')} 매니저`
        : user.name
  );

  const accountActions = pending ? (
    <div aria-hidden="true" className="h-11 w-28" />
  ) : user ? (
    <div className="flex items-center gap-2 sm:gap-3">
      <NotificationBell transparent={transparent} />
      <UserMenu
        name={accountName ?? user.name}
        href={user.href ?? '/mypage'}
        items={user.menu}
        role={role}
        transparent={transparent}
        onLogout={onLogout}
      />
    </div>
  ) : (
    <div className="flex items-center gap-2">
      <Link
        href="/login"
        className={cn(
          'inline-flex h-[44px] items-center rounded-[10px] border px-4 text-[15px] font-semibold transition-colors duration-200',
          transparent
            ? 'border-white/40 text-white hover:bg-white/10'
            : 'border-[#E1E5E8] bg-white text-[#14243A] hover:bg-[#F7F8F8]',
        )}
      >
        로그인
      </Link>
      <Link
        href="/signup"
        className="inline-flex h-[44px] items-center rounded-[10px] bg-[#14243A] px-4 text-[15px] font-semibold text-white transition-colors duration-200 hover:bg-[#1F3757]"
      >
        회원가입
      </Link>
    </div>
  );

  const registerButton = creationBlocked ? (
    <span aria-disabled="true" title={creationReason} className="inline-flex h-[44px] cursor-not-allowed items-center gap-1.5 rounded-[10px] bg-[#426B93] px-4 text-[15px] font-semibold text-white opacity-50">
      <PlusCircle className="h-4 w-4" /><span>공고등록</span>
    </span>
  ) : (
    <Link href="/client/posts/new" className="inline-flex h-[44px] items-center gap-1.5 rounded-[10px] bg-[#426B93] px-4 text-[15px] font-semibold text-white transition-colors duration-200 hover:bg-[#345778]">
      <PlusCircle className="h-4 w-4" /><span>공고등록</span>
    </Link>
  );

  return (
    <header className={cn(
      'top-0 z-40 w-full transition-[background-color,border-color,box-shadow,backdrop-filter] duration-300',
      isHome ? 'fixed' : 'sticky',
      transparent
        ? 'border-b border-white/15 bg-transparent text-white'
        : 'border-b border-[#E1E5E8] bg-white/95 text-[#14243A] shadow-xs backdrop-blur-md',
    )}>
      <Container className="flex h-[72px] items-center justify-between gap-4">
        <Link href="/" aria-label="GAJI 홈" className="flex shrink-0 items-center gap-2">
          <span className={cn('text-2xl font-semibold tracking-tight', transparent ? 'text-white' : 'text-[#14243A]')}>GAJI</span>
          <span className={cn('text-base font-bold tracking-tight', transparent ? 'text-white/90' : 'text-[#14243A]')}>가지</span>
          <span className={cn('h-1.5 w-1.5 rounded-full', transparent ? 'bg-white/80' : 'bg-[#14243A]')} />
          {(role === 'ADMIN' || pathname.startsWith('/admin')) && (
            <span className="ml-2 rounded border border-[#14243A] bg-[#14243A] px-2.5 py-0.5 text-xs font-bold text-white">관리자</span>
          )}
        </Link>

        <nav aria-label="주요 메뉴" className="hidden items-center gap-8 lg:flex">
          {menuLinks()}
        </nav>

        <div className="hidden shrink-0 items-center gap-3 lg:flex">
          {canRegisterPost && registerButton}
          {accountActions}
        </div>

        <button
          type="button"
          onClick={() => setMobileOpen((open) => !open)}
          aria-expanded={mobileOpen}
          aria-controls="gaji-mobile-menu"
          aria-label={mobileOpen ? '메뉴 닫기' : '메뉴 열기'}
          className={cn(
            'inline-flex h-[44px] w-[44px] items-center justify-center rounded-[10px] border transition-colors lg:hidden',
            transparent ? 'border-white/40 text-white' : 'border-[#E1E5E8] text-[#14243A]',
          )}
        >
          {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </Container>

      {mobileOpen && (
        <nav id="gaji-mobile-menu" aria-label="모바일 메뉴" className="border-t border-[#E1E5E8] bg-white px-5 py-5 text-[#14243A] lg:hidden">
          <div className="mx-auto flex max-w-[1240px] flex-col items-start gap-5">
            {menuLinks(true)}
            {canRegisterPost && registerButton}
            <div className="w-full border-t border-[#E1E5E8] pt-4">
              {accountActions}
            </div>
          </div>
        </nav>
      )}
    </header>
  );
}
