'use client';

import { useEffect, useLayoutEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import Link from 'next/link';
import { ChevronRight, Search } from 'lucide-react';
import { useRequireAuth } from '@/features/auth';
import { fetchAllAdminUsers, type AdminUserDto } from '../api';
import AdminShell from './AdminShell';
import { AdminRoleBadge, AdminStatusBadge, type AdminMemberRole, type AdminMemberStatus } from './AdminMemberBadges';

type RoleFilter = 'all' | AdminMemberRole;
type StatusFilter = 'all' | 'active' | 'suspended' | 'banned';
const PAGE_SIZE = 10;

const ROLE_FILTERS: { value: RoleFilter; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'client', label: '의뢰인' },
  { value: 'escort', label: '동행 매니저' },
  { value: 'admin', label: '관리자' },
];
const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'active', label: '활성' },
  { value: 'suspended', label: '정지' },
  { value: 'banned', label: '영구정지' },
];

type Option<T extends string> = { value: T; label: string };

function SlidingFilter<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: Option<T>[];
  onChange: (value: T) => void;
}) {
  const selected = Math.max(0, options.findIndex((item) => item.value === value));
  const groupRef = useRef<HTMLDivElement>(null);
  const buttonRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const [indicator, setIndicator] = useState({ left: 0, width: 0 });

  useLayoutEffect(() => {
    const measure = () => {
      const button = buttonRefs.current[selected];
      if (button) setIndicator({ left: button.offsetLeft, width: button.offsetWidth });
    };
    measure();
    // 각 탭의 원본 고유 너비를 유지하면서 활성 배경만 이동시킵니다.
    const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(measure) : null;
    if (groupRef.current) observer?.observe(groupRef.current);
    return () => observer?.disconnect();
  }, [selected]);

  return (
    <div ref={groupRef} role="group" aria-label={label}
      className="relative inline-flex shrink-0 items-center rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] p-1">
      <span aria-hidden="true"
        className="pointer-events-none absolute top-1 bottom-1 rounded-md bg-[#14243A] shadow-xs transition-[left,width] duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] motion-reduce:transition-none"
        style={{ left: indicator.left, width: indicator.width, opacity: indicator.width ? 1 : 0 }} />
      {options.map((option, index) => (
        <button ref={(element) => { buttonRefs.current[index] = element; }}
          type="button" key={option.value} aria-pressed={value === option.value}
          onClick={() => onChange(option.value)}
          className={`relative z-10 cursor-pointer rounded-md px-2.5 py-1 text-xs font-medium whitespace-nowrap transition-colors duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#14243A] ${selected === index ? 'text-white' : 'text-[#67717C] hover:text-[#14243A]'}`}>
          {option.label}
        </button>
      ))}
    </div>
  );
}

export default function AdminMembersPage() {
  const { loading, user } = useRequireAuth('ADMIN');
  const [queryInput, setQueryInput] = useState('');
  const [query, setQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('all');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [includeWithdrawn, setIncludeWithdrawn] = useState(false);
  const [page, setPage] = useState(0);

  const [members, setMembers] = useState<AdminUserDto[]>([]);
  const [fetching, setFetching] = useState(true);
  const [fetchError, setFetchError] = useState('');
  const [reload, setReload] = useState(0);
  useEffect(() => {
    if (loading || user?.role !== 'ADMIN') return;
    const controller = new AbortController();
    fetchAllAdminUsers(controller.signal).then((rows) => {
      if (controller.signal.aborted) return;
      setMembers(rows);
      setFetchError('');
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) { setMembers([]); setFetchError(error instanceof Error ? error.message : '회원 목록 조회에 실패했습니다.'); }
    }).finally(() => { if (!controller.signal.aborted) setFetching(false); });
    return () => controller.abort();
  }, [loading, user?.role, user?.username, reload]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const digits = q.replace(/\D/g, '');
    return members.filter((member) => {
      if (!includeWithdrawn && member.deleted) return false;
      const role = member.role.toLowerCase();
      if (roleFilter !== 'all' && role !== roleFilter) return false;
      if (statusFilter !== 'all' && (member.deleted || member.userStatus.toLowerCase() !== statusFilter)) return false;
      if (!q) return true;
      return [String(member.id), member.username, member.name, member.email, member.phoneNum ?? ''].some((field) => field.toLowerCase().includes(q)) ||
        Boolean(digits && (member.phoneNum ?? '').replace(/\D/g, '').includes(digits));
    });
  }, [members, roleFilter, statusFilter, includeWithdrawn, query]);
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const visible = filtered.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setQuery(queryInput);
    setPage(0);
  };
  const chooseRole = (role: RoleFilter) => { setRoleFilter(role); setPage(0); };
  const chooseStatus = (status: StatusFilter) => { setStatusFilter(status); setPage(0); };

  if (loading) return <div className="min-h-screen bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]" role="status">불러오는 중입니다...</div>;
  if (!user || user.role !== 'ADMIN') return null;

  return (
    <AdminShell>
      <div className="space-y-6">
        <section className="rounded-xl border border-[#E1E5E8] bg-white p-6 shadow-xs">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-[#14243A]">회원 관리</h1>
              <p className="mt-1 text-sm text-[#67717C]">전체 회원의 계정 정보, 가입일, 상태 및 역할별 필터링을 통해 상세 제재를 관리합니다.</p>
            </div>
            <span className="text-xs text-[#67717C]" aria-live="polite">{fetching ? '조회 중' : `전체 ${members.length}명 · ${filtered.length}명 표시`}</span>
          </div>
          <form onSubmit={search} className="relative mt-6">
            <Search className="absolute top-1/2 left-3.5 size-5 -translate-y-1/2 text-[#67717C]" aria-hidden="true" />
            <input value={queryInput} onChange={(e) => setQueryInput(e.target.value)} type="search"
              placeholder="이름, 회원 ID, 이메일 등으로 검색해보세요." aria-label="회원 검색"
              className="h-12 w-full rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] pr-24 pl-11 text-sm text-[#14243A] outline-none transition-colors placeholder:text-[#67717C] focus:border-[#14243A] focus:bg-white" />
            <button type="submit" className="absolute top-1/2 right-1.5 -translate-y-1/2 rounded-md bg-[#14243A] px-3.5 py-2 text-xs font-semibold text-white transition-colors duration-200 hover:bg-[#426B93] active:bg-[#345778]">검색</button>
          </form>
          <div className="mt-4 flex flex-wrap items-center justify-between gap-4 border-t border-[#E1E5E8] pt-4">
            <div className="flex flex-wrap items-center gap-3 text-xs">
              <div className="flex items-center gap-1.5">
                <span className="font-semibold text-[#67717C]">역할:</span>
                <SlidingFilter label="역할 필터" value={roleFilter} options={ROLE_FILTERS} onChange={chooseRole} />
              </div>
              <div className="flex items-center gap-1.5">
                <span className="font-semibold text-[#67717C]">상태:</span>
                <SlidingFilter label="회원 상태 필터" value={statusFilter} options={STATUS_FILTERS} onChange={chooseStatus} />
              </div>
            </div>
            <label className="flex cursor-pointer select-none items-center gap-2 text-xs font-medium text-[#14243A]">
              <input type="checkbox" checked={includeWithdrawn} onChange={(e) => setIncludeWithdrawn(e.target.checked)} className="size-4 cursor-pointer accent-[#14243A]" />
              탈퇴 회원 포함
            </label>
          </div>
        </section>

        <section className="overflow-hidden rounded-xl border border-[#E1E5E8] bg-white shadow-xs" aria-label="회원 목록">
          {fetchError && <div role="alert" className="flex flex-wrap items-center justify-between gap-3 border-b border-[#E1E5E8] px-5 py-4 text-sm text-[#B4413C]">
            <span>{fetchError}</span><button type="button" className="rounded-lg border px-3 py-1.5 text-xs" onClick={() => {setFetching(true); setReload((v) => v + 1);}}>다시 시도</button>
          </div>}
          <div className="overflow-x-auto">
            <table className="w-full min-w-[800px] border-collapse text-left">
              <thead className="border-b border-[#E1E5E8] bg-[#F7F8F8] text-xs font-bold text-[#67717C]">
                <tr>{['회원 ID','이름','아이디 · 이메일','역할','상태','가입일','상세'].map((label) => <th key={label} scope="col" className={`px-5 py-3.5 ${label === '상세' ? 'text-right' : ''}`}>{label}</th>)}</tr>
              </thead>
              <tbody className="divide-y divide-[#E1E5E8]">
                {visible.map((member) => (
                  <tr key={member.id} className="transition-colors hover:bg-[#F7F8F8]">
                    <td className="px-5 py-4 font-mono text-xs text-[#67717C]">{member.id}</td>
                    <td className="px-5 py-4 text-sm font-semibold text-[#14243A]">{member.name}</td>
                    <td className="px-5 py-4 text-xs"><p className="font-medium text-[#14243A]">{member.username}</p><p className="text-[#67717C]">{member.email}</p></td>
                    <td className="px-5 py-4"><AdminRoleBadge role={member.role.toLowerCase() as AdminMemberRole}/></td>
                    <td className="px-5 py-4">{member.deleted ? <AdminStatusBadge status="withdrawn" /> : <AdminStatusBadge status={member.userStatus.toLowerCase() as AdminMemberStatus} />}</td>
                    <td className="px-5 py-4 font-mono text-xs text-[#67717C]">{member.createdAt?.slice(0, 10) ?? '—'}</td>
                    <td className="px-5 py-4 text-right"><Link href={`/admin/members/${encodeURIComponent(member.id)}`}
                      className="inline-flex items-center gap-1 rounded-lg border border-[#E1E5E8] px-3 py-1.5 text-xs font-semibold text-[#14243A] transition-colors hover:border-[#426B93] hover:bg-[#426B93] hover:text-white">
                      상세 <ChevronRight className="size-3.5" aria-hidden="true" />
                    </Link></td>
                  </tr>
                ))}
                {visible.length === 0 && <tr><td colSpan={7} className="px-5 py-16 text-center text-sm text-[#67717C]">{fetching ? '회원 목록을 불러오는 중입니다...' : fetchError ? '목록을 불러오지 못했습니다.' : '검색 조건에 일치하는 회원이 없습니다.'}</td></tr>}
              </tbody>
            </table>
          </div>
          {pageCount > 1 && <div className="flex items-center justify-center gap-3 border-t border-[#E1E5E8] p-4 text-xs">
            <button type="button" disabled={currentPage === 0} onClick={() => setPage((v) => Math.max(0,v - 1))} className="rounded-md border border-[#E1E5E8] px-3 py-2 disabled:opacity-40">이전</button>
            <span>{currentPage + 1} / {pageCount}</span>
            <button type="button" disabled={currentPage === pageCount - 1} onClick={() => setPage((v) => Math.min(pageCount-1,v + 1))} className="rounded-md border border-[#E1E5E8] px-3 py-2 disabled:opacity-40">다음</button>
          </div>}
        </section>
      </div>
    </AdminShell>
  );
}
