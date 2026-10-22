'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { Activity, ChevronLeft, ChevronRight, FileText, Search } from 'lucide-react';
import { AppShell } from '@/components/layout';
import DropdownSelect from '@/components/ui/DropdownSelect';
import { useActionDialog } from '@/components/ui/useActionDialog';
import PageFeedback from '@/components/ui/PageFeedback';
import { cancelApplication, fetchMyApplications, type MyApplicationDto } from '@/features/application';
import { useRequireAuth } from '@/features/auth';

type ViewStatus = 'pending' | 'matched' | 'inProgress' | 'completed' | 'rejected' | 'canceled' | 'noShow';
type StatusTab = 'all' | Exclude<ViewStatus, 'noShow'>;
type SortOrder = 'latest' | 'oldest';

type ApplicationRow = {
  id: number;
  postId: number;
  postStatus: string;
  title: string;
  hospitalName: string;
  region: string;
  date: string;
  time: string;
  hours: number;
  pay: number;
  status: ViewStatus;
};

const TABS: { value: StatusTab; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'pending', label: '대기 중' },
  { value: 'matched', label: '매칭 완료' },
  { value: 'inProgress', label: '진행 중' },
  { value: 'completed', label: '완료' },
  { value: 'rejected', label: '거절' },
  { value: 'canceled', label: '지원 취소' },
];

const BADGES: Record<ViewStatus, { label: string; style: string; dot: string }> = {
  pending: { label: '대기 중', style: 'border-[#9A6B12]/30 bg-[#F7F0DE] text-[#84601B]', dot: 'bg-[#9A6B12]' },
  matched: { label: '매칭 완료', style: 'border-[#2F7D5B]/35 bg-[#E5F1EA] text-[#286849]', dot: 'bg-[#2F7D5B]' },
  inProgress: { label: '진행 중', style: 'border-[#14243A]/30 bg-[#E5E9F0] text-[#14243A]', dot: 'bg-[#14243A]' },
  completed: { label: '완료', style: 'border-[#5E697A]/30 bg-[#E9EDF1] text-[#344055]', dot: 'bg-[#67717C]' },
  rejected: { label: '거절', style: 'border-[#B4413C]/25 bg-[#F7ECEB] text-[#99433E]', dot: 'bg-[#B4413C]' },
  canceled: { label: '지원 취소', style: 'border-[#67717C]/30 bg-[#EDF0F2] text-[#536273]', dot: 'bg-[#67717C]' },
  noShow: { label: '노쇼', style: 'border-[#B4413C]/25 bg-[#F7ECEB] text-[#99433E]', dot: 'bg-[#B4413C]' },
};

const ACTION_SIZE = 'inline-flex h-9 w-[104px] shrink-0 items-center justify-center gap-1.5 whitespace-nowrap rounded-lg px-3 text-xs font-semibold transition-colors';
const PRIMARY = `${ACTION_SIZE} bg-[#14243A] text-white hover:bg-[#1F3757]`;
const BLUE = `${ACTION_SIZE} bg-[#426B93] text-white hover:bg-[#345778]`;
const SECONDARY = `${ACTION_SIZE} border border-[#E1E5E8] bg-white text-[#14243A] hover:bg-[#F7F8F8]`;
const CANCEL = `${ACTION_SIZE} border border-[#B4413C]/40 bg-white text-[#B4413C] hover:bg-[#F7ECEB]`;

function viewStatus(dto: MyApplicationDto): ViewStatus {
  switch (dto.applicationStatus) {
    case 'PENDING': return 'pending';
    case 'REJECTED': return 'rejected';
    case 'CANCELED': return 'canceled';
    case 'NO_SHOW': return 'noShow';
    case 'ACCEPTED':
      if (dto.postStatus === 'IN_PROGRESS') return 'inProgress';
      if (dto.postStatus === 'COMPLETED') return 'completed';
      return 'matched';
  }
}

function mapApplication(dto: MyApplicationDto): ApplicationRow {
  const start = new Date(dto.escortStartAt);
  const validDate = Number.isFinite(start.getTime());
  return {
    id: dto.applicationId,
    postId: dto.postId,
    postStatus: dto.postStatus,
    title: dto.title,
    hospitalName: dto.hospitalName,
    region: dto.region ?? '',
    date: validDate ? new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' }).format(start) : '일정 미정',
    time: validDate ? new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit', hour12: true }).format(start) : '',
    hours: dto.escortHours,
    pay: dto.hourlyPay,
    status: viewStatus(dto),
  };
}

function StatusBadge({ status }: { status: ViewStatus }) {
  const badge = BADGES[status];
  return (
    <span className={`inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-md border px-2.5 py-1 text-xs font-bold ${badge.style}`}>
      <span aria-hidden="true" className={`size-1.5 shrink-0 rounded-full ${badge.dot}`} />
      {badge.label}
    </span>
  );
}

function ApplicationAction({ row, onCancel }: { row: ApplicationRow; onCancel: (row: ApplicationRow) => void }) {
  switch (row.status) {
    case 'pending':
      return <button type="button" onClick={() => onCancel(row)} className={CANCEL}>지원 취소</button>;
    case 'matched':
      return (
        <div className="flex flex-col items-center gap-2">
          <Link href={`/escort/${row.id}`} className={PRIMARY}>
            <Activity aria-hidden="true" className="size-3.5 text-[#2F7D5B]" />동행 보기
          </Link>
          {row.postStatus === 'MATCHED' && (
            <button type="button" onClick={() => onCancel(row)} className={CANCEL}>지원 취소</button>
          )}
        </div>
      );
    case 'inProgress':
      return (
        <Link href={`/escort/${row.id}`} className={PRIMARY}>
          <Activity aria-hidden="true" className="size-3.5 text-[#2F7D5B]" />동행 보기
        </Link>
      );
    case 'completed':
      return (
        <Link href={`/escort/${row.id}/report`} className={BLUE}>
          <FileText aria-hidden="true" className="size-3.5" />보고서 보기
        </Link>
      );
    case 'rejected':
    case 'canceled':
    case 'noShow':
      return <span className="text-xs font-medium text-[#67717C]">처리 완료</span>;
  }
}

export default function MyApplicationsPage() {
  const { confirm, dialog } = useActionDialog();
  const { loading: authLoading, user } = useRequireAuth('ESCORT');
  const [rows, setRows] = useState<ApplicationRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [notice, setNotice] = useState<string>();
  const [reloadKey, setReloadKey] = useState(0);
  const [tab, setTab] = useState<StatusTab>('all');
  const [search, setSearch] = useState('');
  const [sortOrder, setSortOrder] = useState<SortOrder>('latest');
  const [canceling, setCanceling] = useState(false);

  const reload = useCallback(() => setReloadKey((value) => value + 1), []);

  useEffect(() => {
    if (authLoading || user?.role !== 'ESCORT') return;
    let ignored = false;
    setLoading(true);
    setError(undefined);
    fetchMyApplications()
      .then((result) => {
        if (ignored) return;
        // 동일 공고에 재지원한 경우도 별도의 지원 기록으로 취급합니다.
        setRows(result.map(mapApplication));
      })
      .catch((cause: unknown) => {
        if (!ignored) setError(cause instanceof Error ? cause.message : '지원 내역을 불러오지 못했습니다.');
      })
      .finally(() => { if (!ignored) setLoading(false); });
    return () => { ignored = true; };
  }, [authLoading, user?.role, user?.username, reloadKey]);

  const confirmCancel = async (row: ApplicationRow) => {
    if (canceling) return;
    const isMatched = row.status === 'matched' && row.postStatus === 'MATCHED';
    const approved = await confirm({
      title: isMatched ? '매칭된 지원을 취소하시겠습니까?' : '지원을 취소하시겠습니까?',
      description: isMatched
        ? `${row.title}\n매칭 확정 후 취소하면 노쇼로 처리됩니다.\n노쇼 횟수가 증가하고 등급에 영향을 줄 수 있으며, 노쇼 페널티 기록이 생성됩니다.\n취소 후에는 되돌릴 수 없습니다.`
        : `${row.title}\n대기 중 지원 취소는 노쇼로 처리되지 않습니다.`,
      confirmLabel: '지원 취소',
      tone: 'danger',
    });
    if (!approved) return;
    setCanceling(true);
    setError(undefined);
    try {
      await cancelApplication(row.id);
      setRows((current) => current.map((item) => item.id === row.id ? { ...item, status: isMatched ? 'noShow' : 'canceled' } : item));
      setNotice(isMatched ? '지원이 취소되었으며 노쇼로 처리되었습니다.' : '지원이 취소되었습니다.');
      reload();
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : '지원 취소에 실패했습니다.');
    } finally {
      setCanceling(false);
    }
  };

  const keyword = search.trim().toLocaleLowerCase('ko-KR');
  const visibleRows = rows
    .filter((row) => (tab === 'all' || row.status === tab || (tab === 'canceled' && row.status === 'noShow'))
      && (!keyword || [row.title, row.hospitalName, row.region].some((item) => item.toLocaleLowerCase('ko-KR').includes(keyword))))
    .sort((a, b) => sortOrder === 'latest' ? b.id - a.id : a.id - b.id);

  if (authLoading || !user || user.role !== 'ESCORT') {
    return <AppShell><PageFeedback kind="loading" title="로그인 상태를 확인하는 중입니다." /></AppShell>;
  }

  return (
    <AppShell>
      <div className="min-h-[calc(100vh-72px)] bg-[#F7F8F8] py-12 lg:py-16">
        <div className="mx-auto max-w-[1240px] space-y-8 px-5 sm:px-8 lg:px-16">
          <Link href="/mypage" className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] transition-colors hover:text-[#14243A] sc-focus">
            <ChevronLeft aria-hidden="true" className="h-4 w-4" />
            마이페이지로 돌아가기
          </Link>
          <div>
            <h1 className="text-3xl font-semibold tracking-tight text-[#14243A]">내가 신청한 공고</h1>
            <p className="mt-1 text-sm text-[#67717C]">지원한 동행 공고의 매칭 결과와 진행 상태를 확인할 수 있습니다.</p>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-4">
            <div role="tablist" aria-label="지원 상태" className="inline-flex max-w-full gap-1 overflow-x-auto rounded-xl border border-[#E1E5E8] bg-[#EDF0F2] p-1">
              {TABS.map((item) => (
                <button
                  key={item.value}
                  type="button"
                  role="tab"
                  aria-selected={tab === item.value}
                  onClick={() => setTab(item.value)}
                  className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-semibold transition-colors ${tab === item.value ? 'bg-white text-[#14243A] shadow-xs' : 'text-[#67717C] hover:text-[#14243A]'}`}
                >
                  {item.label}
                </button>
              ))}
            </div>
            <div className="text-sm text-[#67717C]">총 <span className="font-semibold tabular-nums text-[#14243A]">{visibleRows.length}</span>건</div>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-end">
            <label className="flex h-[46px] w-full items-center gap-2.5 rounded-lg border border-[#E1E5E8] bg-white px-3.5 sm:max-w-[310px]">
              <Search className="size-4 shrink-0 text-[#8793A0]" aria-hidden="true" />
              <span className="sr-only">지원 내역 검색</span>
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="공고 제목, 병원명, 지역 검색"
                className="min-w-0 flex-1 bg-transparent text-sm text-[#14243A] outline-none placeholder:text-[#98A1AD]"
              />
            </label>
            <DropdownSelect
              id="escort-applications-sort"
              label="정렬"
              value={sortOrder}
              onValueChange={(value) => setSortOrder(value as SortOrder)}
              options={[{ value: 'latest', label: '최신 순' }, { value: 'oldest', label: '오래된 순' }]}
              size="filter"
              className="w-full sm:w-[145px]"
            />
          </div>

          {notice && <p role="status" className="text-sm font-medium text-[#2F7D5B]">{notice}</p>}
          {error && (
            <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#B4413C]/25 bg-[#F7ECEB] p-4 text-sm text-[#99433E]">
              <span>{error}</span>
              <button type="button" onClick={reload} className="rounded-lg border border-[#B4413C]/25 bg-white px-3 py-2 text-xs font-semibold">다시 시도</button>
            </div>
          )}

          <div className="overflow-hidden rounded-2xl border border-[#E1E5E8] bg-white shadow-xs">
            <div className="hidden grid-cols-[112px_minmax(0,1fr)_150px_72px_94px_125px] gap-4 border-b border-[#E1E5E8] bg-[#EDF0F2]/60 px-6 py-3.5 text-xs font-semibold text-[#67717C] min-[1100px]:grid" style={{ gridTemplateColumns: "88px minmax(0, 1fr) 150px 72px 94px 125px", columnGap: "12px" }}>
              <div>상태</div>
              <div>병원 및 공고</div>
              <div>동행 일시</div>
              <div>소요</div>
              <div className="text-right">시급</div>
              <div className="text-center">진행 액션</div>
            </div>

            {loading ? (
              <div className="py-20 text-center text-sm text-[#67717C]">지원 내역을 불러오는 중입니다.</div>
            ) : !error && visibleRows.length === 0 ? (
              <div className="space-y-3 py-20 text-center">
                <p className="text-base font-medium text-[#14243A]">{tab === 'all' && !search ? '아직 지원한 공고가 없습니다.' : '조건에 맞는 지원 내역이 없습니다.'}</p>
                <p className="text-sm text-[#67717C]">{tab === 'all' && !search ? '관심 있는 병원 동행 공고를 찾아 지원해 보세요.' : '상태나 검색어를 바꿔 확인해 보세요.'}</p>
                {tab === 'all' && !search && <Link href="/escort/posts" className={`${PRIMARY} mx-auto mt-3`}>공고 찾기 <ChevronRight aria-hidden="true" className="size-3.5" /></Link>}
              </div>
            ) : !error ? (
              <div className="divide-y divide-[#E1E5E8]">
                {visibleRows.map((row) => (
                  <div key={row.id} className="px-6 py-5 transition-colors hover:bg-[#F7F8F8]">
                    <div className="hidden grid-cols-[112px_minmax(0,1fr)_150px_72px_94px_125px] items-center gap-4 text-sm min-[1100px]:grid" style={{ gridTemplateColumns: "88px minmax(0, 1fr) 150px 72px 94px 125px", columnGap: "12px" }}>
                      <div><StatusBadge status={row.status} /></div>
                      <div className="min-w-0 pr-2">
                        <Link href={`/posts/${row.postId}`} className="block truncate text-base font-semibold text-[#14243A] hover:text-[#426B93]">{row.title}</Link>
                        <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-[#67717C]">
                          <span className="font-medium text-[#202832]">{row.hospitalName}</span>
                          {row.region && <><span aria-hidden="true">·</span><span>{row.region}</span></>}
                        </div>
                      </div>
                      <div className="text-sm tabular-nums text-[#202832]">{row.date}<br />{row.time}</div>
                      <div className="text-sm tabular-nums text-[#67717C]">약 {row.hours}시간</div>
                      <div className="text-right font-bold tabular-nums text-[#14243A]">{row.pay.toLocaleString('ko-KR')}원</div>
                      <div className="flex items-center justify-center"><ApplicationAction row={row} onCancel={(item) => void confirmCancel(item)} /></div>
                    </div>
                    <div className="space-y-3 min-[1100px]:hidden">
                      <div className="flex items-center justify-between gap-3">
                        <StatusBadge status={row.status} />
                        <span className="text-xs tabular-nums text-[#67717C]">{row.date} {row.time}</span>
                      </div>
                      <Link href={`/posts/${row.postId}`} className="block text-base font-semibold text-[#14243A] hover:text-[#426B93]">{row.title}</Link>
                      <p className="text-xs text-[#67717C]">{row.hospitalName}{row.region ? ` · ${row.region}` : ''} · 약 {row.hours}시간 · 시급 {row.pay.toLocaleString('ko-KR')}원</p>
                      <div className="flex justify-end border-t border-[#E1E5E8] pt-3"><ApplicationAction row={row} onCancel={(item) => void confirmCancel(item)} /></div>
                    </div>
                  </div>
                ))}
              </div>
            ) : null}
          </div>
        </div>
      </div>

      {dialog}
    </AppShell>
  );
}
