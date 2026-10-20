'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { ChevronDown, CreditCard, Info } from 'lucide-react';
import { useRequireAuth } from '@/features/auth';
import { useActionDialog } from '@/components/ui/useActionDialog';
import type { SpringPage } from '@/lib/api';
import { fetchAdminPaymentsPage, updateAdminPaymentStatus, type AdminPaymentRow, type AdminPaymentStatus } from '../api';
import AdminShell from './AdminShell';
import PageFeedback from '@/components/ui/PageFeedback';
import { AdminDateFilter, AdminManageHeader, AdminPager, StatusChip, dateTime, money } from './AdminManagementCommon';

const labels: Record<AdminPaymentStatus, string> = {
  READY: '결제 대기', IN_PROGRESS: '결제 중', DONE: '결제 완료',
  CANCELED: '결제 취소', PARTIAL_CANCELED: '부분 취소', DELETED: '공고 삭제',
};
const transitions: Record<AdminPaymentStatus, AdminPaymentStatus[]> = {
  READY: ['IN_PROGRESS', 'DELETED'],
  IN_PROGRESS: ['DONE'],
  DONE: ['CANCELED', 'PARTIAL_CANCELED', 'DELETED'],
  CANCELED: [], PARTIAL_CANCELED: [], DELETED: ['CANCELED'],
};
const tone = (status: AdminPaymentStatus) => status === 'DONE' ? 'green' as const : status === 'READY' ? 'yellow' as const : status === 'IN_PROGRESS' ? 'blue' as const : status === 'CANCELED' || status === 'DELETED' ? 'red' as const : 'gray' as const;
const toDate = (daysAgo = 0) => {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

export default function AdminPaymentsPage() {
  const { user, loading } = useRequireAuth('ADMIN');
  const { confirm, dialog } = useActionDialog();
  const [from, setFrom] = useState(() => toDate(30));
  const [to, setTo] = useState(() => toDate());
  const [range, setRange] = useState(() => ({ start: toDate(30), end: toDate() }));
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [result, setResult] = useState<SpringPage<AdminPaymentRow> | null>(null);
  const [busy, setBusy] = useState(false);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [draft, setDraft] = useState<Record<number, AdminPaymentStatus>>({});

  const reload = useCallback(() => { setRefresh(n => n + 1); }, []);
  useEffect(() => {
    if (loading || user?.role !== 'ADMIN') return;
    const controller = new AbortController();
    setBusy(true); setError('');
    fetchAdminPaymentsPage(page, range.start, range.end, controller.signal)
      .then(data => { if (!controller.signal.aborted) { setResult(data); setDraft({}); } })
      .catch((e: unknown) => { if (!controller.signal.aborted) { setResult(null); setError(e instanceof Error ? e.message : '결제 내역을 불러오지 못했습니다.'); } })
      .finally(() => { if (!controller.signal.aborted) setBusy(false); });
    return () => controller.abort();
  }, [loading, user?.role, page, range, refresh]);

  const update = async (row: AdminPaymentRow) => {
    const next = draft[row.id] ?? row.paymentStatus;
    if (next === row.paymentStatus || savingId !== null) return;
    const okay = await confirm({
      title: '결제 상태를 변경할까요?',
      description: `결제 #${row.id} · ${labels[row.paymentStatus]} → ${labels[next]}\n\n결제 상태 정보만 변경됩니다.\n실제 토스 승인·환불은 실행되지 않습니다.`,
      confirmLabel: '상태 변경', tone: 'danger',
    });
    if (!okay) return;
    setSavingId(row.id); setError(''); setSuccess('');
    try {
      await updateAdminPaymentStatus(row.id, next);
      setSuccess(`#${row.id} 결제 상태 변경 요청이 완료됐습니다.`);
      reload();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '결제 상태 변경에 실패했습니다.');
    } finally { setSavingId(null); }
  };

  if (loading) return <PageFeedback kind="loading" title="관리자 정보를 확인하는 중입니다." />;
  if (!user || user.role !== 'ADMIN') return null;
  return <AdminShell><div className="space-y-5">
    <AdminManageHeader title="결제 관리" description="결제 내역을 조회하고 결제 상태를 관리합니다." onRefresh={reload} busy={busy} />
    <AdminDateFilter start={from} end={to} setStart={setFrom} setEnd={setTo} disabled={busy} apply={() => { setPage(0); setRange({ start: from, end: to }); }} />
    <div className="flex items-start gap-2 rounded-lg border border-[#D6E1EB] bg-[#F1F6FA] px-4 py-3 text-xs leading-relaxed text-[#305879]"><Info className="mt-0.5 size-4 shrink-0" /><span>조회 기간은 결제 승인일 기준입니다. 승인 전 결제는 목록 API의 기간 조건에 따라 표시되지 않을 수 있습니다. 상태 변경은 결제 DB 정보만 변경하며 실제 토스 승인·환불을 실행하지 않습니다.</span></div>
    {error && <p role="alert" className="rounded-lg border border-[#F1D4D1] bg-[#FFF5F4] px-4 py-3 text-sm text-[#B4413C]">{error}</p>}
    {success && <p role="status" className="rounded-lg border border-[#C6DDD0] bg-[#F0F7F3] px-4 py-3 text-sm text-[#2F7D5B]">{success}</p>}
    <section className="overflow-hidden rounded-xl border border-[#E1E5E8] bg-white shadow-xs">
      <div className="flex items-center gap-2 border-b border-[#E1E5E8] px-5 py-4"><CreditCard className="size-4 text-[#426B93]" /><h2 className="font-bold text-[#14243A]">결제 내역</h2></div>
      <div className="overflow-x-auto"><table className="w-full min-w-[980px] text-left text-sm">
        <thead className="bg-[#F7F8F8] text-xs text-[#67717C]"><tr><th className="px-4 py-3">결제번호 / 공고</th><th className="px-4 py-3">의뢰인</th><th className="px-4 py-3">승인일</th><th className="px-4 py-3 text-right">결제금액</th><th className="px-4 py-3 text-right">잔액</th><th className="px-4 py-3">현재 상태</th><th className="px-4 py-3">상태 변경</th></tr></thead>
        <tbody className="divide-y divide-[#E1E5E8]">
          {busy && !result ? <tr><td colSpan={7} className="px-5 py-16 text-center text-[#67717C]">결제 내역을 불러오는 중입니다...</td></tr> : result?.content?.length ? result.content.map(row => {
            const available = transitions[row.paymentStatus] ?? [];
            const selected = draft[row.id] ?? row.paymentStatus;
            return <tr key={row.id} className="align-middle transition-colors hover:bg-[#F7F8F8]">
              <td className="max-w-[250px] px-4 py-3"><strong className="block text-[#14243A]">#{row.id}</strong>{row.postId != null ? <Link href={`/posts/${row.postId}?from=admin-payments`} className="block truncate text-xs text-[#426B93] hover:underline">{row.postTitle || `공고 #${row.postId}`}</Link> : <span className="text-xs text-[#67717C]">공고 정보 없음</span>}</td>
              <td className="px-4 py-3 text-[#344055]">{row.name || '—'}{row.userId != null && <span className="block text-xs text-[#67717C]">#{row.userId}</span>}</td>
              <td className="whitespace-nowrap px-4 py-3 text-xs text-[#67717C]">{dateTime(row.approvedAt)}</td>
              <td className="whitespace-nowrap px-4 py-3 text-right font-semibold text-[#14243A]">{money(row.amount)}</td>
              <td className="whitespace-nowrap px-4 py-3 text-right text-[#344055]">{money(row.balanceAmount)}</td>
              <td className="px-4 py-3"><StatusChip tone={tone(row.paymentStatus)}>{labels[row.paymentStatus]}</StatusChip></td>
              <td className="px-4 py-3">{available.length ? <div className="flex items-center gap-2"><div className="relative w-[140px] shrink-0">
                  <select
                    aria-label={`결제 ${row.id} 상태 변경`}
                    value={selected}
                    onChange={e => setDraft(current => ({ ...current, [row.id]: e.target.value as AdminPaymentStatus }))}
                    disabled={savingId !== null}
                    className="h-9 w-full cursor-pointer appearance-none rounded-lg border border-[#E1E5E8] bg-white pl-3 pr-9 text-xs font-medium text-[#14243A] outline-none transition-colors hover:border-[#BCC7D2] focus-visible:border-[#426B93] focus-visible:ring-2 focus-visible:ring-[#426B93]/20 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    <option value={row.paymentStatus}>선택</option>
                    {available.map(status => <option key={status} value={status}>{labels[status]}</option>)}
                  </select>
                  <ChevronDown className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-[#8793A0]" aria-hidden="true" />
                </div><button type="button" disabled={savingId !== null || selected === row.paymentStatus} onClick={() => void update(row)} className="h-9 whitespace-nowrap rounded-lg bg-[#14243A] px-3 text-xs font-semibold text-white hover:bg-[#1F3757] disabled:cursor-not-allowed disabled:opacity-35">{savingId === row.id ? '처리 중' : '적용'}</button></div> : <span className="text-xs text-[#67717C]">변경 불가</span>}</td>
            </tr>;
          }) : <tr><td colSpan={7} className="px-5 py-16 text-center text-[#67717C]">조회 기간에 결제 내역이 없습니다.</td></tr>}
        </tbody>
      </table></div>
      <AdminPager page={page} pages={result?.totalPages ?? 0} total={result?.totalElements ?? 0} disabled={busy} onChange={setPage} />
    </section>
    {dialog}
  </div></AdminShell>;
}
