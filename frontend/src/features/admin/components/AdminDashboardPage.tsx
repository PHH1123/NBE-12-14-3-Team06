'use client';

import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import Link from 'next/link';
import {
  CreditCard,
  Percent,
  Banknote,
  ShieldCheck,
  Table,
  BarChart3,
  ChevronRight,
  ShieldAlert,
  UserCheck,
} from 'lucide-react';
import { useRequireAuth } from '@/features/auth';
import AdminShell from './AdminShell';
import PageFeedback from '@/components/ui/PageFeedback';
import { AdminRoleBadge } from './AdminMemberBadges';
import { fetchAdminUsersPage, fetchAllAdminUsers, fetchAdminPaymentAnalytics, fetchAdminDailyPayments, fetchAdminSettlementAnalytics, type AdminUserDto, type AdminPaymentAnalytics, type AdminSettlementAnalytics } from '../api';

type Period = 'today' | 'week' | 'month';
const PERIODS: { key: Period; label: string }[] = [
  { key: 'today', label: '오늘' },
  { key: 'week', label: '이번 주' },
  { key: 'month', label: '이번 달' },
];

const moneyCards = [
  { label: '결제 금액', icon: CreditCard, color: 'text-[#14243A]', description: '취소 반영 후 결제액' },
  { label: '수수료 수익', icon: Percent, color: 'text-[#426B93]', description: '플랫폼 중개 수수료' },
  { label: '정산 지급액', icon: Banknote, color: 'text-[#2F7D5B]', description: '매니저 정산 지급액' },
  { label: '노쇼 패널티 적립액', icon: ShieldCheck, color: 'text-[#426B93]', description: '정산 완료된 패널티 합계' },
] as const;

const memberCards = [
  { label: '정상 활성', color: 'text-[#2F7D5B]' },
  { label: '기간 정지', color: 'text-[#9A6B12]' },
  { label: '영구 정지 (블랙리스트)', color: 'text-[#B4413C]' },
  { label: '탈퇴 회원', color: 'text-[#67717C]' },
] as const;

type DailyPayment = { date: string; day: string; count: number; amount: number };

const dateKey = (d: Date) => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
const rangeFor = (period: Period) => {
  const end = new Date(); const start = new Date(end);
  if (period === 'today') return {start: dateKey(start), end: dateKey(end)};
  if (period === 'week') start.setDate(end.getDate() - ((end.getDay() + 6) % 7));
  if (period === 'month') start.setDate(1);
  return {start: dateKey(start), end: dateKey(end)};
};
const weekday = (date: string) => ['일','월','화','수','목','금','토'][new Date(`${date}T12:00:00`).getDay()];


/** 실제 데이터가 들어오면 화면 진입 시 막대 상승, 호버 시 블루와 툴팁을 표시합니다. */
function DailyPaymentBarChart({ data }: { data: DailyPayment[] }) {
  const chartRef = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);
  const [hovered, setHovered] = useState<number | null>(null);

  useEffect(() => {
    if (data.length === 0) return;
    setVisible(false);
    const element = chartRef.current;
    if (!element || typeof IntersectionObserver === 'undefined') {
      setVisible(true);
      return;
    }
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setVisible(true);
        observer.disconnect();
      }
    }, { threshold: 0.1 });
    observer.observe(element);
    return () => observer.disconnect();
  }, [data]);

  if (data.length === 0) {
    return (
      <div className="flex h-60 items-center justify-center rounded-lg border-b border-[#E1E5E8] bg-[#FCFDFD] text-center text-sm text-[#67717C]">
        조회 기간에 결제 내역이 없습니다.
      </div>
    );
  }

  const maxAmount = Math.max(1, ...data.map((item) => item.amount));
  return (
    <div ref={chartRef} className="space-y-4" aria-label="일별 결제 금액 막대 차트">
      <div className="relative flex h-60 items-end justify-between gap-3 border-b border-[#E1E5E8] px-2 pt-8 pb-2">
        <div aria-hidden="true" className="pointer-events-none absolute inset-0 flex flex-col justify-between font-mono text-[11px] text-[#67717C]/60">
          <div className="border-b border-[#E1E5E8]">{maxAmount.toLocaleString()}원</div>
          <div className="border-b border-[#E1E5E8]">{(maxAmount / 2).toLocaleString()}원</div>
          <div className="border-b border-[#E1E5E8]">0원</div>
        </div>
        {data.map((item, index) => (
          <div key={`${item.date}-${index}`} tabIndex={0}
            onMouseEnter={() => setHovered(index)} onMouseLeave={() => setHovered(null)}
            onFocus={() => setHovered(index)} onBlur={() => setHovered(null)}
            aria-label={`${item.date} 결제 ${item.count}건, ${item.amount.toLocaleString()}원`}
            className="group relative z-10 flex h-full flex-1 cursor-default flex-col items-center justify-end focus-visible:outline-2 focus-visible:outline-[#426B93]">
            {hovered === index && (
              <div className="pointer-events-none absolute -top-12 z-20 flex flex-col items-center whitespace-nowrap rounded-lg bg-[#14243A] px-2.5 py-1.5 text-xs text-white shadow-lg">
                <span className="font-bold tabular-nums">{item.amount.toLocaleString()}원</span>
                <span className="text-[10px] text-white/75">{item.date} ({item.day}) · {item.count}건</span>
              </div>
            )}
            <div className={`w-full max-w-[48px] rounded-t-sm transition-[height,background-color] duration-700 ease-out motion-reduce:transition-none ${hovered === index ? 'bg-[#426B93]' : 'bg-[#14243A]'}`}
              style={{ height: visible ? `${Math.max(0, item.amount / maxAmount * 100)}%` : '0%', transitionDelay: visible ? `${index * 85}ms` : '0ms' }} />
          </div>
        ))}
      </div>
      <div className="flex justify-between gap-3 px-2 text-xs font-medium text-[#67717C]">
        {data.map((item, index) => (
          <div key={`${item.date}-${index}`} className="flex-1 text-center font-mono">
            <div>{item.date}</div><div className="text-[11px]">({item.day})</div>
          </div>
        ))}
      </div>
    </div>
  );
}

/** Preserve the original period button sizes while sliding only the active navy background. */
function SlidingPeriodTabs({ period, onChange }: { period: Period; onChange: (period: Period) => void }) {
  const groupRef = useRef<HTMLDivElement>(null);
  const buttonRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const [indicator, setIndicator] = useState({ left: 0, width: 0 });
  const selected = PERIODS.findIndex((item) => item.key === period);

  useLayoutEffect(() => {
    const measure = () => {
      const button = buttonRefs.current[selected];
      if (!button) return;
      const next = { left: button.offsetLeft, width: button.offsetWidth };
      setIndicator((previous) => previous.left === next.left && previous.width === next.width ? previous : next);
    };
    measure();
    const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(measure) : null;
    if (groupRef.current) observer?.observe(groupRef.current);
    return () => observer?.disconnect();
  }, [selected]);

  return (
    <div ref={groupRef} role="group" aria-label="통계 조회 기간"
      className="relative flex self-start rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] p-1 sm:self-auto">
      <span aria-hidden="true"
        className="pointer-events-none absolute top-1 bottom-1 rounded-md bg-[#14243A] shadow-xs transition-[left,width] duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] motion-reduce:transition-none"
        style={{ left: indicator.left, width: indicator.width, opacity: indicator.width ? 1 : 0 }} />
      {PERIODS.map((item, index) => (
        <button ref={(element) => { buttonRefs.current[index] = element; }}
          key={item.key} type="button" aria-pressed={period === item.key}
          onClick={() => onChange(item.key)}
          className={`relative z-10 rounded-md px-3.5 py-1.5 text-xs font-semibold transition-colors duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93] ${period === item.key ? 'text-white' : 'text-[#67717C] hover:text-[#14243A]'}`}>
          {item.label}
        </button>
      ))}
    </div>
  );
}

/** 관리자 대시보드: 2026-10-20 관리자 결제·정산 통계 API */
export default function AdminDashboardPage() {
  const { loading, user } = useRequireAuth('ADMIN');
  const [period, setPeriod] = useState<Period>('month');
  const [showTable, setShowTable] = useState(false);
  const [recentMembers, setRecentMembers] = useState<AdminUserDto[] | null>(null);
  const [recentError, setRecentError] = useState(false);
  const [allMembers, setAllMembers] = useState<AdminUserDto[] | null>(null);
  const [payment, setPayment] = useState<AdminPaymentAnalytics | null>(null);
  const [settlement, setSettlement] = useState<AdminSettlementAnalytics | null>(null);
  const [dailyPayments, setDailyPayments] = useState<DailyPayment[]>([]);
  const [statsLoading, setStatsLoading] = useState(true);
  const [statsError, setStatsError] = useState('');
  useEffect(() => {
    if (loading || user?.role !== 'ADMIN') return;
    const controller = new AbortController();
    fetchAdminUsersPage(0, 4, controller.signal)
      .then((page) => {
        if (controller.signal.aborted) return;
        setRecentMembers(page.content.slice(0, 4));
        setRecentError(false);
      })
      .catch(() => {
        if (!controller.signal.aborted) setRecentError(true);
      });
    return () => controller.abort();
  }, [loading, user?.username, user?.role]);

  useEffect(() => {
    if (loading || user?.role !== 'ADMIN') return;
    const ctrl = new AbortController();
    fetchAllAdminUsers(ctrl.signal).then(rows => { if (!ctrl.signal.aborted) setAllMembers(rows); })
      .catch(() => { if (!ctrl.signal.aborted) setAllMembers(null); });
    return () => ctrl.abort();
  }, [loading, user?.role, user?.username]);
  useEffect(() => {
    if (loading || user?.role !== 'ADMIN') return;
    const ctrl = new AbortController();
    const range = rangeFor(period);
    setStatsLoading(true); setStatsError('');
    Promise.allSettled([
      fetchAdminPaymentAnalytics(range.start, range.end, ctrl.signal),
      fetchAdminSettlementAnalytics(range.start, range.end, ctrl.signal),
      fetchAdminDailyPayments(range.start, range.end, ctrl.signal),
    ]).then(results => {
      if (ctrl.signal.aborted) return;
      const [pay, settle, daily] = results;
      setPayment(pay.status === 'fulfilled' ? pay.value as AdminPaymentAnalytics : null);
      setSettlement(settle.status === 'fulfilled' ? settle.value as AdminSettlementAnalytics : null);
      setDailyPayments(daily.status === 'fulfilled' ? (daily.value as {approvedAt:string;paidAmount:number;counts:number}[]).map(d => ({
        date: d.approvedAt, day: weekday(d.approvedAt), count: d.counts, amount: d.paidAmount,
      })).sort((a,b) => a.date.localeCompare(b.date)) : []);
      if (results.some(r => r.status === 'rejected')) setStatsError('일부 통계를 불러오지 못했습니다.');
    }).finally(() => { if (!ctrl.signal.aborted) setStatsLoading(false); });
    return () => ctrl.abort();
  }, [loading, user?.role, user?.username, period]);
  const moneyValues = [payment?.paidAmount, settlement?.totalPlatformFee, settlement?.totalSettledAmount, settlement?.totalDonationAmount];
  const memberCounts = allMembers ? [
    allMembers.filter(m => !m.deleted && m.userStatus === 'ACTIVE').length,
    allMembers.filter(m => !m.deleted && m.userStatus === 'SUSPENDED').length,
    allMembers.filter(m => !m.deleted && m.userStatus === 'BANNED').length,
    allMembers.filter(m => m.deleted).length,
  ] : null;
  const sanctionedMembers = allMembers?.filter(m => !m.deleted && m.userStatus !== 'ACTIVE').slice(0, 4) ?? [];

  if (loading) return <PageFeedback kind="loading" title="관리자 정보를 확인하는 중입니다." />;
  if (!user || user.role !== 'ADMIN') return null;

  return (
    <AdminShell>
      <div className="space-y-6">
        {/* 원본: 대시보드 제목 / 기간 선택 */}
        <div className="flex flex-col gap-4 rounded-xl border border-[#E1E5E8] bg-white p-6 shadow-xs sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-[#14243A]">대시보드</h1>
            <p className="mt-1 text-sm text-[#67717C]">플랫폼 결제액, 정산 수익, 회원 상태 및 운영 지표를 모니터링합니다.</p>
          </div>
          <SlidingPeriodTabs period={period} onChange={setPeriod} />
        </div>

        {statsError && <p role="alert" className="rounded-lg border border-[#E1E5E8] bg-white px-4 py-3 text-xs text-[#B4413C]">{statsError}</p>}
        {/* 원본: 요약 통계 카드 네 개 */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="결제 및 운영 통계">
          {moneyCards.map((card) => {
            const Icon = card.icon;
            return (
              <div key={card.label} className="space-y-2 rounded-xl border border-[#E1E5E8] bg-white p-5 shadow-xs">
                <div className="flex items-center justify-between text-[#67717C]">
                  <span className="text-xs font-semibold">{card.label}</span>
                  <Icon aria-hidden="true" className={`size-4 ${card.color}`} />
                </div>
                <div className={`text-2xl font-bold tabular-nums tracking-tight ${card.color}`} aria-label={card.label}>
                  {statsLoading ? '—' : moneyValues[moneyCards.indexOf(card)] === undefined ? '—' : `${moneyValues[moneyCards.indexOf(card)]!.toLocaleString()}원`}
                </div>
                <p className="text-xs text-[#67717C]">{card.description}</p>
              </div>
            );
          })}
        </div>

        {/* 원본: 일별 결제 차트 / 표 전환 */}
        <section className="rounded-xl border border-[#E1E5E8] bg-white p-6 shadow-xs" aria-labelledby="daily-payments-heading">
          <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 id="daily-payments-heading" className="text-lg font-bold tracking-tight text-[#14243A]">일별 결제 금액</h2>
              <p className="mt-0.5 text-xs text-[#67717C]">선택한 기간의 일자별 결제 거래액 추이</p>
            </div>
            <button
              type="button"
              onClick={() => setShowTable((prev) => !prev)}
              aria-pressed={showTable}
              className="flex items-center gap-1.5 rounded-lg border border-[#E1E5E8] px-3 py-1.5 text-xs font-medium text-[#14243A] transition-colors hover:bg-[#F7F8F8]"
            >
              {showTable ? <BarChart3 aria-hidden="true" className="size-3.5 text-[#426B93]" /> : <Table aria-hidden="true" className="size-3.5 text-[#426B93]" />}
              <span>{showTable ? '차트로 보기' : '표로 보기'}</span>
            </button>
          </div>

          {showTable ? (
            <div className="overflow-x-auto rounded-lg border border-[#E1E5E8]">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-[#E1E5E8] bg-[#F7F8F8] text-xs font-semibold text-[#67717C]">
                  <tr>
                    <th className="px-4 py-3">날짜</th>
                    <th className="px-4 py-3">요일</th>
                    <th className="px-4 py-3 text-right">결제 건수</th>
                    <th className="px-4 py-3 text-right">결제 금액</th>
                  </tr>
                </thead>
                <tbody>
                  {dailyPayments.length === 0 ? (
                    <tr><td colSpan={4} className="px-4 py-14 text-center text-sm text-[#67717C]">조회 기간에 결제 내역이 없습니다.</td></tr>
                  ) : dailyPayments.map((item) => (
                    <tr key={item.date} className="transition-colors hover:bg-[#F3F7FB]">
                      <td className="px-4 py-3 font-mono">{item.date}</td>
                      <td className="px-4 py-3">{item.day}</td>
                      <td className="px-4 py-3 text-right tabular-nums">{item.count}건</td>
                      <td className="px-4 py-3 text-right font-semibold tabular-nums">{item.amount.toLocaleString()}원</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <DailyPaymentBarChart data={dailyPayments} />
          )}
        </section>

        {/* 원본: 회원 상태 요약 */}
        <section className="rounded-xl border border-[#E1E5E8] bg-white p-6 shadow-xs" aria-labelledby="member-status-heading">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 id="member-status-heading" className="text-lg font-bold tracking-tight text-[#14243A]">회원 상태 요약</h2>
              <p className="mt-0.5 text-xs text-[#67717C]">현재 플랫폼에 등록된 전체 회원 계정의 상태 현황</p>
            </div>
            <Link href="/admin/members" className="flex items-center gap-1 text-xs font-semibold text-[#426B93] transition-colors hover:text-[#14243A] hover:underline">
              <span>전체 회원 관리</span>
              <ChevronRight aria-hidden="true" className="size-3.5" />
            </Link>
          </div>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            {memberCards.map((card) => (
              <Link key={card.label} href="/admin/members" className="rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] p-4 text-left transition-colors hover:border-[#14243A] focus-visible:border-[#14243A]">
                <span className="mb-1 block text-xs font-medium text-[#67717C]">{card.label}</span>
                <span className="flex items-baseline gap-1">
                  <span className={`text-2xl font-bold tabular-nums ${card.color}`}>{memberCounts ? memberCounts[memberCards.indexOf(card)] : '—'}</span>
                  <span className="text-xs text-[#67717C]">명</span>
                </span>
              </Link>
            ))}
          </div>
        </section>

        {/* 원본: 최근 가입 / 정지 처리 두 개의 목록 */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <section className="overflow-hidden rounded-xl border border-[#E1E5E8] bg-white shadow-xs" aria-labelledby="recent-signup-heading">
            <div className="flex items-center justify-between border-b border-[#E1E5E8] px-5 py-4">
              <h3 id="recent-signup-heading" className="flex items-center gap-2 text-sm font-bold text-[#14243A]">
                <UserCheck aria-hidden="true" className="size-4 text-[#2F7D5B]" />
                최근 가입 회원
              </h3>
              <Link href="/admin/members" className="text-xs font-semibold text-[#67717C] transition-colors hover:text-[#14243A]">더보기</Link>
            </div>
            {recentMembers && recentMembers.length ? (
              <div className="divide-y divide-[#E1E5E8]">
                {recentMembers.map((member) => (
                  <Link key={member.id} href={`/admin/members/${member.id}`}
                    className="flex items-center justify-between gap-3 px-5 py-3 transition-colors hover:bg-[#F7F8F8]">
                    <div className="flex min-w-0 items-center gap-2.5">
                      <span className="truncate text-sm font-semibold text-[#14243A]">{member.name}</span>
                      {member.role === 'CLIENT' ? <AdminRoleBadge role="client" compact /> : member.role === 'ESCORT' ? <AdminRoleBadge role="escort" compact /> : member.role === 'ADMIN' ? <AdminRoleBadge role="admin" compact /> : null}
                      {member.deleted && <span className="text-xs font-semibold text-[#67717C]">탈퇴</span>}
                    </div>
                    <span className="shrink-0 font-mono text-xs tabular-nums text-[#67717C]">{member.createdAt?.slice(0, 10) ?? '—'}</span>
                  </Link>
                ))}
              </div>
            ) : (
              <div className="flex min-h-40 items-center justify-center px-5 text-center text-sm text-[#67717C]">
                {recentError ? '최근 가입 회원을 불러오지 못했습니다.' : recentMembers ? '조회된 회원이 없습니다.' : '불러오는 중입니다...'}
              </div>
            )}
          </section>
          <section className="overflow-hidden rounded-xl border border-[#E1E5E8] bg-white shadow-xs" aria-labelledby="recent-sanctions-heading">
            <div className="flex items-center justify-between border-b border-[#E1E5E8] px-5 py-4">
              <h3 id="recent-sanctions-heading" className="flex items-center gap-2 text-sm font-bold text-[#14243A]">
                <ShieldAlert aria-hidden="true" className="size-4 text-[#B4413C]" />
                현재 제재 회원
              </h3>
              <Link href="/admin/blacklist" className="text-xs font-semibold text-[#426B93] hover:underline">전체 보기</Link>
            </div>
            {sanctionedMembers.length ? <div className="divide-y divide-[#E1E5E8]">{sanctionedMembers.map(m => <Link key={m.id} href={`/admin/members/${m.id}`} className="flex items-center justify-between gap-3 px-5 py-3 text-sm hover:bg-[#F7F8F8]"><span className="truncate font-semibold text-[#14243A]">{m.name}</span><span className="text-xs text-[#B4413C]">{m.userStatus === 'BANNED' ? '영구 정지' : '기간 정지'}</span></Link>)}</div> : <div className="flex min-h-40 items-center justify-center px-5 text-center text-sm text-[#67717C]">{allMembers ? '현재 제재 중인 회원이 없습니다.' : '회원 정보를 불러오는 중입니다...'}</div>}
          </section>
        </div>
      </div>
    </AdminShell>
  );
}
