'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { ShieldAlert, ChevronRight, UserX, Search } from 'lucide-react';
import { useRequireAuth } from '@/features/auth';
import { fetchAllAdminUsers, type AdminUserDto } from '../api';
import AdminShell from './AdminShell';

export default function AdminBlacklistPage() {
  const { loading, user } = useRequireAuth('ADMIN');
  const [searchTerm, setSearchTerm] = useState('');
  const [members, setMembers] = useState<AdminUserDto[]>([]);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    if (loading || user?.role !== 'ADMIN') return;
    const ctrl = new AbortController();
    fetchAllAdminUsers(ctrl.signal).then(data => {
      if (ctrl.signal.aborted) return;
      setMembers(data.filter(m => !m.deleted && m.userStatus === 'BANNED'));
      setError('');
    }).catch((e: unknown) => {
      if (!ctrl.signal.aborted) setError(e instanceof Error ? e.message : '영구 정지 회원 목록 조회에 실패했습니다.');
    }).finally(() => { if (!ctrl.signal.aborted) setFetching(false); });
    return () => ctrl.abort();
  }, [loading, user?.role, user?.username, retry]);
  const filtered = useMemo(() => {
    const q = searchTerm.trim().toLowerCase();
    return members.filter(m => !q || [m.name, m.username, String(m.id), m.email].some(field => field.toLowerCase().includes(q)));
  }, [members, searchTerm]);
  if (loading) return <div className="min-h-screen bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]" role="status">불러오는 중입니다...</div>;
  if (!user || user.role !== 'ADMIN') return null;
  return <AdminShell><div className="space-y-6">
    <section className="rounded-xl border border-[#E1E5E8] bg-white p-6 shadow-xs">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div><div className="flex items-center gap-2"><ShieldAlert className="size-6 text-[#B4413C]" aria-hidden="true"/><h1 className="text-2xl font-bold tracking-tight text-[#14243A]">블랙리스트 (영구정지 회원)</h1></div>
          <p className="mt-1 text-sm text-[#67717C]">현재 영구 정지된 회원을 조회하고 회원 상세에서 제재 해제를 관리합니다.</p></div>
        <p className="text-xs text-[#67717C]">총 <span className="font-bold tabular-nums text-[#B4413C]">{fetching ? '—' : members.length}</span>명 영구정지 중</p>
      </div>
      <div className="relative mt-6"><Search className="absolute top-1/2 left-3.5 size-5 -translate-y-1/2 text-[#67717C]" aria-hidden="true"/>
        <input type="search" value={searchTerm} onChange={e => setSearchTerm(e.target.value)} placeholder="이름, 아이디, 이메일로 검색해보세요." aria-label="블랙리스트 검색" className="h-11 w-full rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] pr-4 pl-11 text-sm text-[#14243A] outline-none transition-colors placeholder:text-[#67717C] focus:border-[#14243A] focus:bg-white"/>
      </div>
    </section>
    <section className="overflow-hidden rounded-xl border border-[#E1E5E8] bg-white shadow-xs" aria-label="블랙리스트 목록">
      {error && <div role="alert" className="flex flex-wrap items-center justify-between gap-2 border-b border-[#E1E5E8] px-5 py-4 text-sm text-[#B4413C]">{error}<button type="button" className="rounded-lg border px-3 py-1.5 text-xs" onClick={() => {setFetching(true);setRetry(v=>v+1);}}>다시 시도</button></div>}
      <div className="overflow-x-auto"><table className="w-full min-w-[650px] border-collapse text-left text-sm">
        <thead className="border-b border-[#E1E5E8] bg-[#F7F8F8] text-xs font-bold text-[#67717C]"><tr><th className="px-5 py-3.5">이름</th><th className="px-5 py-3.5">아이디</th><th className="px-5 py-3.5">이메일</th><th className="px-5 py-3.5">상태</th><th className="px-5 py-3.5 text-right">상세</th></tr></thead>
        <tbody className="divide-y divide-[#E1E5E8]">
          {filtered.map(member => <tr key={member.id} className="hover:bg-[#F7F8F8]"><td className="px-5 py-4 font-semibold text-[#14243A]">{member.name}</td><td className="px-5 py-4 font-mono text-xs text-[#67717C]">{member.username}</td><td className="px-5 py-4 text-xs text-[#67717C]">{member.email}</td><td className="px-5 py-4 text-xs font-semibold text-[#B4413C]">영구 정지</td><td className="px-5 py-4 text-right"><Link href={`/admin/members/${member.id}`} className="inline-flex items-center gap-1 rounded-lg border border-[#E1E5E8] px-3 py-1.5 text-xs font-semibold text-[#14243A] hover:bg-[#F7F8F8]">상세 <ChevronRight className="size-3.5"/></Link></td></tr>)}
          {!filtered.length && <tr><td colSpan={5} className="px-5 py-14 text-center text-sm text-[#67717C]"><UserX className="mx-auto mb-3 size-8 text-[#9AA4AD]"/>{fetching ? '영구 정지 회원을 조회하는 중입니다...' : error ? '조회 결과를 표시할 수 없습니다.' : '해당하는 영구 정지 회원이 없습니다.'}</td></tr>}
        </tbody>
      </table></div>
      {members.length > 0 && <div className="border-t border-[#E1E5E8] px-5 py-3 text-xs text-[#67717C]">제재 사유는 회원 상세의 현재 제재 정보에서 확인할 수 있습니다.</div>}
    </section>
  </div></AdminShell>;
}
