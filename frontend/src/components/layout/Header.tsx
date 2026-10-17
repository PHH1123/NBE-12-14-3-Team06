'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import Container from '@/components/ui/Container';
import { NotificationBell } from '@/features/notification';
import { cn } from '@/lib/cn';
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
  pending?: boolean;
  onLogout?: () => void;
  canRegisterPost?: boolean;
  isEscort?: boolean;
};

export default function Header({ user, pending, onLogout, canRegisterPost, isEscort }: Props) {
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

  // The home hero is behind the header at the top. All other pages use a light header.
  const transparent = isHome && !scrolled && !mobileOpen;
  const resolveHref = (item: MenuItem) =>
    item.label === '공고 찾기' && isEscort ? '/escort/posts' : item.href;

  const menuLinks = (mobile = false) => MENU.map((item) => (
    <Link
      key={item.label}
      href={resolveHref(item)}
      onClick={() => setMobileOpen(false)}
      className={cn(
        'whitespace-nowrap text-[15px] font-medium transition-colors',
        mobile ? 'text-[#344055] hover:text-[#14243A]' :
          transparent ? 'text-white/85 hover:text-white' : 'text-[#67717C] hover:text-[#14243A]',
        item.label === '고객센터' && pathname === '/support' && 'font-semibold',
      )}
    >
      {item.label}
    </Link>
  ));

  const actions = pending ? (
    <div aria-hidden="true" className="h-11 w-28" />
  ) : user ? (
    <div className="flex flex-wrap items-center gap-2 sm:gap-3">
      {/* Existing components use their own colors. A light surface guarantees contrast over the hero. */}
      <div className={cn('flex items-center gap-2 rounded-lg', transparent && 'bg-white/95 px-2 py-1')}>
        <NotificationBell />
        <UserMenu name={user.name} href={user.href ?? '/mypage'} items={user.menu} />
      </div>
      <button
        type="button"
        onClick={onLogout}
        className={cn(
          'h-11 rounded-[10px] border px-3 text-sm font-semibold transition-colors',
          transparent ? 'border-white/50 bg-transparent text-white hover:bg-white/15' :
            'border-[#E1E5E8] bg-white text-[#14243A] hover:bg-[#F7F8F8]',
        )}
      >로그아웃</button>
    </div>
  ) : (
    <div className="flex items-center gap-2">
      <Link href="/login" className={cn(
        'inline-flex h-11 items-center rounded-[10px] border px-4 text-sm font-semibold transition-colors',
        transparent ? 'border-white/60 text-white hover:bg-white/15' :
          'border-[#E1E5E8] bg-white text-[#14243A] hover:bg-[#F7F8F8]',
      )}>로그인</Link>
      <Link href="/signup" className="inline-flex h-11 items-center rounded-[10px] bg-[#14243A] px-4 text-sm font-semibold text-white transition-colors hover:bg-[#1F3757]">회원가입</Link>
    </div>
  );

  return (
    <header className={cn(
      'top-0 z-40 w-full transition-[background-color,border-color,box-shadow,backdrop-filter] duration-300',
      isHome ? 'fixed' : 'sticky',
      transparent ? 'border-b border-white/20 bg-transparent' :
        'border-b border-[#E1E5E8] bg-white/95 shadow-xs backdrop-blur-md',
    )}>
      <Container className="flex min-h-[72px] items-center justify-between gap-4">
        <Link href="/" aria-label="GAJI 홈" className={cn('flex shrink-0 items-center gap-2', transparent ? 'text-white' : 'text-[#14243A]')}>
          <span className="text-2xl font-semibold tracking-tight">GAJI</span>
          <span className="text-sm font-bold">가지</span>
          <span className={cn('h-1.5 w-1.5 rounded-full', transparent ? 'bg-white/65' : 'bg-[#426B93]')} />
          {pathname.startsWith('/admin') && <span className="ml-1 rounded bg-[#14243A] px-2 py-0.5 text-xs font-semibold text-white">관리자</span>}
        </Link>

        <nav aria-label="주요 메뉴" className="hidden items-center gap-7 lg:flex">
          {menuLinks()}
        </nav>

        <div className="hidden items-center gap-3 lg:flex">
          {canRegisterPost && (
            <Link href="/client/posts/new" className="inline-flex h-11 items-center rounded-[10px] bg-[#426B93] px-4 text-sm font-semibold text-white hover:bg-[#345778]">
              + 공고등록
            </Link>
          )}
          {actions}
        </div>

        <button
          type="button"
          className={cn('inline-flex h-11 items-center rounded-[10px] border px-3 text-sm font-semibold lg:hidden',
            transparent ? 'border-white/60 text-white' : 'border-[#E1E5E8] text-[#14243A]')}
          aria-expanded={mobileOpen}
          aria-controls="gaji-mobile-menu"
          onClick={() => setMobileOpen((open) => !open)}
        >{mobileOpen ? '닫기' : '메뉴'}</button>
      </Container>

      {mobileOpen && (
        <nav id="gaji-mobile-menu" aria-label="모바일 메뉴" className="border-t border-[#E1E5E8] bg-white px-5 py-5 lg:hidden">
          <div className="mx-auto flex max-w-[1240px] flex-col items-start gap-5">
            {menuLinks(true)}
            {canRegisterPost && (
              <Link href="/client/posts/new" onClick={() => setMobileOpen(false)} className="text-[15px] font-semibold text-[#426B93]">+ 공고등록</Link>
            )}
            <div className="w-full border-t border-[#E1E5E8] pt-4">{actions}</div>
          </div>
        </nav>
      )}
    </header>
  );
}
