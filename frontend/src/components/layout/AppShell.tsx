'use client';

import { useEffect, useState } from 'react';
import type { ComponentProps, ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/features/auth/lib/AuthProvider';
import { MYPAGE_BY_ROLE } from '@/features/auth/model/roleHome';
import { MYPAGE_MENUS } from '@/features/mypage/model/menu';
import type { GajiGuideTab } from '@/lib/gajiGuide';
import Header from './Header';
import Footer from './Footer';
import { ManagerGradeModal } from './ManagerGradeModal';

type Props = {
  children: ReactNode;
  user?: ComponentProps<typeof Header>['user'];
};

export default function AppShell({ children, user }: Props) {
  const router = useRouter();
  const { user: me, loading, signOut } = useAuth();
  const [guideTab, setGuideTab] = useState<GajiGuideTab>('grade');
  const [guideOpen, setGuideOpen] = useState(false);

  useEffect(() => {
    const handleOpen = (event: Event) => {
      const tab = (event as CustomEvent<GajiGuideTab>).detail;
      setGuideTab(tab === 'policy' ? 'policy' : 'grade');
      setGuideOpen(true);
    };
    window.addEventListener('gaji:open-guide', handleOpen);
    return () => window.removeEventListener('gaji:open-guide', handleOpen);
  }, []);

  const menuRole = me?.role === 'CLIENT' ? 'client' : me?.role === 'ESCORT' ? 'escort' : undefined;
  const session = me
    ? { name: me.name, href: MYPAGE_BY_ROLE[me.role], menu: menuRole && MYPAGE_MENUS[menuRole] }
    : undefined;
  const canRegisterPost = me?.role === 'CLIENT' || me?.role === 'ADMIN';
  const isEscort = me?.role === 'ESCORT';
  const handleLogout = async () => {
    await signOut();
    router.replace('/');
  };

  return (
    <>
      <Header
        user={user ?? session}
        pending={!user && loading}
        onLogout={handleLogout}
        canRegisterPost={canRegisterPost}
        isEscort={isEscort}
      />
      <main>{children}</main>
      <Footer />
      <ManagerGradeModal
        isOpen={guideOpen}
        onClose={() => setGuideOpen(false)}
        initialTab={guideTab}
      />
    </>
  );
}
