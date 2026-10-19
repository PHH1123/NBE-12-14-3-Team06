'use client';

import { ShieldCheck } from 'lucide-react';
import { useRequireAuth } from '@/features/auth';
import AdminShell from './AdminShell';

export default function AdminVerificationPage() {
  const { loading, user } = useRequireAuth('ADMIN');
  if (loading) return <div className="min-h-screen bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]" role="status">불러오는 중입니다...</div>;
  if (!user || user.role !== 'ADMIN') return null;

  return (
    <AdminShell>
      <div className="space-y-6">
        <section className="rounded-xl border border-[#E1E5E8] bg-white p-6 shadow-xs">
          <h1 className="text-2xl font-bold tracking-tight text-[#14243A]">동행 매니저 자격 검증</h1>
          <p className="mt-1 text-sm text-[#67717C]">신규 신청 매니저의 서류 대기열과 플랫폼 정책 준수 현황을 모니터링합니다.</p>
        </section>
        <section className="grid grid-cols-1 gap-6 sm:grid-cols-3" aria-label="자격 검증 현황">
          <div className="rounded-xl border border-[#E1E5E8] bg-white p-5 shadow-xs">
            <span className="mb-1 block text-xs font-semibold text-[#67717C]">2026 최저시급 준수율</span>
            <span className="text-2xl font-bold tabular-nums text-[#2F7D5B]">—</span>
            <p className="mt-1 text-xs text-[#67717C]">모든 공고 시급 10,320원 이상 자동 검증</p>
          </div>
          <div className="rounded-xl border border-[#E1E5E8] bg-white p-5 shadow-xs">
            <span className="mb-1 block text-xs font-semibold text-[#67717C]">누적 노쇼 패널티 적립액</span>
            <span className="text-2xl font-bold tabular-nums text-[#14243A]">—</span>
            <p className="mt-1 text-xs text-[#67717C]">이용자 보호 및 긴급 대체 지원 기금</p>
          </div>
          <div className="rounded-xl border border-[#E1E5E8] bg-white p-5 shadow-xs">
            <span className="mb-1 block text-xs font-semibold text-[#67717C]">신원 심사 대기</span>
            <span className="text-2xl font-bold tabular-nums text-[#426B93]">—</span>
            <p className="mt-1 text-xs text-[#67717C]">24시간 이내 승인 원칙</p>
          </div>
        </section>
        <section className="overflow-hidden rounded-xl border border-[#E1E5E8] bg-white shadow-xs" aria-label="자격증 서류 확인 대기열">
          <div className="flex items-center justify-between border-b border-[#E1E5E8] px-6 py-4">
            <h2 className="flex items-center gap-2 text-base font-semibold text-[#14243A]">
              <ShieldCheck className="h-5 w-5 text-[#426B93]" aria-hidden="true" />
              <span>동행 매니저 자격증 서류 확인 대기열</span>
            </h2>
          </div>
          <div className="px-6 py-16 text-center text-sm text-[#67717C]">자격 심사 목록 API 연결 후 실제 신청 내역이 표시됩니다.</div>
        </section>
      </div>
    </AdminShell>
  );
}
