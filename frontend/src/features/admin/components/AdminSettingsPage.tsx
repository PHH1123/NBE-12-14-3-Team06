'use client';

import { useRequireAuth } from '@/features/auth';
import AdminShell from './AdminShell';

export default function AdminSettingsPage() {
  const { loading, user } = useRequireAuth('ADMIN');
  if (loading) return <div className="min-h-screen bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]" role="status">불러오는 중입니다...</div>;
  if (!user || user.role !== 'ADMIN') return null;

  return (
    <AdminShell>
      <section className="rounded-xl border border-[#E1E5E8] bg-white p-6 shadow-xs">
        <h1 className="text-2xl font-bold tracking-tight text-[#14243A]">설정</h1>
        <p className="mt-1 text-sm text-[#67717C]">관리자 설정 기능은 준비 중입니다.</p>
        <div className="mt-6 flex min-h-48 items-center justify-center rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] px-5 text-center text-sm text-[#67717C]">설정 항목이 준비되면 이곳에서 관리할 수 있습니다.</div>
      </section>
    </AdminShell>
  );
}
