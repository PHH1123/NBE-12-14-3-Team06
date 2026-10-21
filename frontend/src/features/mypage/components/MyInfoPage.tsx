'use client';

import { AppShell } from '@/components/layout';
import PageFeedback from '@/components/ui/PageFeedback';
import { useRequireAuth, type CurrentUser } from '@/features/auth';
import type { MyPageRole } from '../types';
import ClientInfoView from './ClientInfoView';
import EscortInfoView from './EscortInfoView';

const REQUIRED_ROLE: Record<MyPageRole, CurrentUser['role']> = {
  escort: 'ESCORT',
  client: 'CLIENT',
};

export default function MyInfoPage({ role = 'escort' }: { role?: MyPageRole }) {
  const { loading, user } = useRequireAuth(REQUIRED_ROLE[role]);

  if (loading) {
    return <AppShell><PageFeedback kind="loading" title="내 정보를 불러오는 중입니다." /></AppShell>;
  }
  if (!user) return null;

  return <AppShell>{role === 'escort' ? <EscortInfoView /> : <ClientInfoView />}</AppShell>;
}
