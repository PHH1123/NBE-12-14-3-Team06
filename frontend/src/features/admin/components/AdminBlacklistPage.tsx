'use client';

import { useState } from 'react';
import Link from 'next/link';
import { ShieldAlert, ChevronRight, UserX, Search } from 'lucide-react';
import { useRequireAuth } from '@/features/auth';
import AdminShell from './AdminShell';

export default function AdminBlacklistPage() {
  const { loading, user } = useRequireAuth('ADMIN');
  const [searchTerm, setSearchTerm] = useState('');

  if (loading) return <div className="min-h-screen bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]" role="status">불러오는 중입니다...</div>;
  if (!user || user.role !== 'ADMIN') return null;

  return (
    <AdminShell>
      <div className="space-y-6">
        <section className="rounded-xl border border-[#E1E5E8] bg-white p-6 shadow-xs">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="flex items-center gap-2">
                <ShieldAlert className="h-6 w-6 text-[#B4413C]" aria-hidden="true" />
                <h1 className="text-2xl font-bold tracking-tight text-[#14243A]">블랙리스트 (영구정지 회원)</h1>
              </div>
              <p className="mt-1 text-sm text-[#67717C]">심각한 규정 위반으로 서비스 영구 이용이 제한된 회원 목록입니다. 로그인 및 신규 접수가 영구 차단됩니다.</p>
            </div>
            <p className="text-xs text-[#67717C]">총 <span className="font-bold tabular-nums text-[#B4413C]">—</span>명 영구정지 중</p>
          </div>
          <div className="relative mt-6">
            <Search className="absolute top-1/2 left-3.5 h-5 w-5 -translate-y-1/2 text-[#67717C]" aria-hidden="true" />
            <input type="search" value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="이름, 아이디, 제재 사유로 검색해보세요." aria-label="블랙리스트 검색"
              className="h-11 w-full rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] pr-4 pl-11 text-sm text-[#14243A] outline-none transition-colors placeholder:text-[#67717C] focus:border-[#14243A] focus:bg-white" />
          </div>
        </section>

        <section className="overflow-hidden rounded-xl border border-[#E1E5E8] bg-white shadow-xs" aria-label="블랙리스트 목록">
          <div className="hidden overflow-x-auto md:block">
            <table className="w-full border-collapse text-left text-sm">
              <thead className="sticky top-0 border-b border-[#E1E5E8] bg-[#F7F8F8] text-xs font-bold text-[#67717C]">
                <tr><th className="px-5 py-3.5">이름</th><th className="px-5 py-3.5">아이디</th><th className="px-5 py-3.5">사유</th><th className="px-5 py-3.5">처리일</th><th className="px-5 py-3.5">처리 관리자</th><th className="px-5 py-3.5 text-right">상세</th></tr>
              </thead>
            </table>
          </div>
          <div className="space-y-3 p-12 text-center sm:p-16">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full border border-[#E1E5E8] bg-[#F7F8F8] text-[#67717C]">
              <UserX className="h-6 w-6" aria-hidden="true" />
            </div>
            <p className="text-base font-semibold text-[#14243A]">블랙리스트 내역을 불러올 수 없습니다.</p>
            <p className="mx-auto max-w-sm text-sm text-[#67717C]">영구정지 회원 조회 API 연결 후 실제 제재 내역이 표시됩니다.</p>
            <Link href="/admin/members" className="mt-2 inline-flex items-center gap-1.5 rounded-lg bg-[#14243A] px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-[#202832]">
              회원 관리 목록으로 이동 <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Link>
          </div>
        </section>
      </div>
    </AdminShell>
  );
}
