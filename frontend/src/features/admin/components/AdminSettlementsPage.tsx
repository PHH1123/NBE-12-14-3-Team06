'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { Wallet, Info } from 'lucide-react';
import { useRequireAuth } from '@/features/auth';
import { useActionDialog } from '@/components/ui/useActionDialog';
import type { SpringPage } from '@/lib/api';
import { fetchAdminSettlementsPage, requestAdminSettlement, type AdminSettlementRow, type AdminSettlementStatus } from '../api';
import AdminShell from './AdminShell';
import PageFeedback from '@/components/ui/PageFeedback';
import { AdminDateFilter, AdminManageHeader, AdminPager, StatusChip, money } from './AdminManagementCommon';

const labels: Record<AdminSettlementStatus, string> = {
  PENDING: '정산 전', PROCESSING: '정산 중', COMPLETED: '정산 완료', FAILED: '정산 실패',
};
const tone = (status: AdminSettlementStatus) => status === 'COMPLETED' ? 'green' as const : status === 'PROCESSING' ? 'blue' as const : status === 'FAILED' ? 'red' as const : 'yellow' as const;
const toDate = (daysAgo = 0) => {
  const d = new Date(); d.setDate(d.getDate() - daysAgo);
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
};

export default function AdminSettlementsPage() {
  const { user, loading } = useRequireAuth('ADMIN');
  const { confirm, dialog } = useActionDialog();
  const [from, setFrom] = useState(() => toDate(30));
  const [to, setTo] = useState(() => toDate());
  const [range, setRange] = useState(() => ({ start: toDate(30), end: toDate() }));
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [result, setResult] = useState<SpringPage<AdminSettlementRow> | null>(null);
  const [busy, setBusy] = useState(false);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const reload = useCallback(() => setRefresh(n => n+1), []);

  useEffect(() => {
    if (loading || user?.role !== 'ADMIN') return;
    const controller = new AbortController();
    setBusy(true); setError('');
    fetchAdminSettlementsPage(page, range.start, range.end, controller.signal)
      .then(data => { if (!controller.signal.aborted) setResult(data); })
      .catch((e: unknown) => { if (!controller.signal.aborted) { setResult(null); setError(e instanceof Error ? e.message : '정산 내역을 불러오지 못했습니다.'); } })
      .finally(() => { if (!controller.signal.aborted) setBusy(false); });
    return () => controller.abort();
  }, [loading, user?.role, page, range, refresh]);

  const settle = async (row: AdminSettlementRow) => {
    if (savingId !== null) return;
    const okay = await confirm({
      title: '정산을 요청할까요?',
      description: `정산 #${row.id} · ${row.name || '동행매니저'}\n지급 예정액: ${money(row.payoutAmount)}\n\n정산 처리 절차가 진행됩니다.\n중복 요청에 유의해 주세요.`,
      confirmLabel: '정산 요청', tone: 'danger',
    });
    if (!okay) return;
    setSavingId(row.id); setError(''); setSuccess('');
    try {
      await requestAdminSettlement(row.id);
      setSuccess(`#${row.id} 정산 요청이 완료됐습니다.`); reload();
    } catch (e: unknown) { setError(e instanceof Error ? e.message : '정산 요청에 실패했습니다.'); }
    finally { setSavingId(null); }
  };

  if (loading) return <PageFeedback kind="loading" title="관리자 정보를 확인하는 중입니다." />;
  if (!user || user.role !== 'ADMIN') return null;
  return <AdminShell><div className="space-y-5">
    <AdminManageHeader title="정산 관리" description="동행매니저 정산 내역과 지급 상태를 확인하고 정산을 요청합니다." busy={busy} onRefresh={reload} />
    <AdminDateFilter start={from} end={to} setStart={setFrom} setEnd={setTo} disabled={busy} apply={() => { setPage(0); setRange({ start: from, end: to }); }} />
    <div className="flex items-start gap-2 rounded-lg border border-[#D6E1EB] bg-[#F1F6FA] px-4 py-3 text-xs leading-relaxed text-[#305879]"><Info className="mt-0.5 size-4 shrink-0" /><span>정산 요청은 서버에서 지급 처리 절차를 수행합니다. 정산 중 상태의 재요청은 중복 처리 위험이 있으므로, 완료·진행 중 정산에는 요청 버튼을 표시하지 않습니다.</span></div>
    {error && <p role="alert" className="rounded-lg border border-[#F1D4D1] bg-[#FFF5F4] px-4 py-3 text-sm text-[#B4413C]">{error}</p>}
    {success && <p role="status" className="rounded-lg border border-[#C6DDD0] bg-[#F0F7F3] px-4 py-3 text-sm text-[#2F7D5B]">{success}</p>}
    <section className="overflow-hidden rounded-xl border border-[#E1E5E8] bg-white shadow-xs">
      <div className="flex items-center gap-2 border-b border-[#E1E5E8] px-5 py-4"><Wallet className="size-4 text-[#426B93]" /><h2 className="font-bold text-[#14243A]">정산 내역</h2></div>
      <div className="overflow-x-auto"><table className="w-full min-w-[850px] text-left text-sm">
        <thead className="bg-[#F7F8F8] text-xs text-[#67717C]"><tr><th className="px-4 py-3">정산번호 / 지원</th><th className="px-4 py-3">매니저</th><th className="px-4 py-3 text-right">지급 금액</th><th className="px-4 py-3 text-right">수수료</th><th className="px-4 py-3 text-right">페널티</th><th className="px-4 py-3">상태·정산일</th><th className="px-4 py-3">관리</th></tr></thead>
        <tbody className="divide-y divide-[#E1E5E8]">
          {busy && !result ? <tr><td colSpan={7} className="px-5 py-16 text-center text-[#67717C]">정산 내역을 불러오는 중입니다...</td></tr> : result?.content?.length ? result.content.map(row => <tr key={row.id} className="transition-colors hover:bg-[#F7F8F8]">
            <td className="px-4 py-3"><strong className="block text-[#14243A]">#{row.id}</strong><span className="text-xs text-[#67717C]">{row.applicationId != null ? `지원 #${row.applicationId}` : '지원 정보 없음'}</span></td>
            <td className="px-4 py-3 text-[#344055]">{row.userId != null ? <Link href={`/admin/members/${row.userId}`} className="font-semibold text-[#426B93] hover:underline">{row.name || `회원 #${row.userId}`}</Link> : row.name || '—'}{row.userId != null && <span className="block text-xs text-[#67717C]">#{row.userId}</span>}</td>
            <td className="whitespace-nowrap px-4 py-3 text-right font-semibold text-[#14243A]">{money(row.payoutAmount)}</td>
            <td className="whitespace-nowrap px-4 py-3 text-right text-[#344055]">{money(row.platformFee)}</td>
            <td className="whitespace-nowrap px-4 py-3 text-right text-[#B4413C]">{money(row.penaltyAmount)}</td>
            <td className="px-4 py-3"><StatusChip tone={tone(row.status)}>{labels[row.status]}</StatusChip><span className="mt-1 block text-xs text-[#67717C]">{row.settledAt ?? '—'}</span></td>
            <td className="px-4 py-3">{row.status === 'PENDING' || row.status === 'FAILED' ? <button type="button" disabled={savingId !== null} onClick={() => void settle(row)} className="h-9 whitespace-nowrap rounded-lg bg-[#14243A] px-3 text-xs font-semibold text-white hover:bg-[#1F3757] disabled:opacity-40">{savingId === row.id ? '요청 중...' : row.status === 'FAILED' ? '재요청' : '정산 요청'}</button> : <span className="text-xs text-[#67717C]">{row.status === 'COMPLETED' ? '처리 완료' : '처리 중'}</span>}</td>
          </tr>) : <tr><td colSpan={7} className="px-5 py-16 text-center text-[#67717C]">조회 기간에 정산 내역이 없습니다.</td></tr>}
        </tbody>
      </table></div>
      <AdminPager page={page} pages={result?.totalPages ?? 0} total={result?.totalElements ?? 0} disabled={busy} onChange={setPage} />
    </section>
    {dialog}
  </div></AdminShell>;
}
