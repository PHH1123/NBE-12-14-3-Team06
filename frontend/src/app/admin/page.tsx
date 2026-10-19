'use client';

import dynamic from 'next/dynamic';

/** 관리자 진입점 — 관리자 대시보드 */
const AdminDashboardPage = dynamic(
  () => import('@/features/admin').then((module) => module.AdminDashboardPage),
  { ssr: false, loading: () => <div className="min-h-screen bg-[#F7F8F8]" /> },
);

export default function Page() {
  return <AdminDashboardPage />;
}
