'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { CheckCircle2, ChevronLeft, Receipt, X, ShieldCheck } from 'lucide-react';
import { AppShell } from '@/components/layout';
import { useRequireAuth } from '@/features/auth';
import { api } from '@/lib/api';
import { fetchMyEscortProfile, fetchSettlements, requestSettlement } from '../api';
import type { EscortProfileDto, SettlementDto, SettlementStatus } from '../types';

type Result = {
  key: string;
  settlements?: SettlementDto[];
  totalPages?: number;
  totalElements?: number;
  error?: string;
};

type DetailResult = {
  id: number;
  data?: SettlementDto;
  error?: string;
};

const formatMoney = (amount: number) => `${amount.toLocaleString('ko-KR')}원`;
const formatDate = (date?: string | null) => date ? date.slice(0, 10).replaceAll('-', '.') : '-';
const formatHours = (hours: number) => `${hours}시간`;
const toDayStart = (value: string) => `${value}T00:00:00`;
const toDayEnd = (value: string) => `${value}T23:59:59`;

function maskAccount(value: string | null | undefined): string {
  if (!value) return '계좌번호 미등록';
  const digits = value.replace(/\D/g, '');
  return digits.length >= 4 ? `****${digits.slice(-4)}` : '****';
}

function StatusBadge({ status }: { status: SettlementStatus }) {
  switch (status) {
    case 'PENDING':
      return <span className="px-2 py-0.5 rounded text-xs font-semibold bg-[#9A6B12]/15 text-[#9A6B12] border border-[#9A6B12]/20 whitespace-nowrap">정산 대기</span>;
    case 'PROCESSING':
      return <span className="px-2 py-0.5 rounded text-xs font-semibold bg-[#426B93]/10 text-[#426B93] border border-[#426B93]/20 whitespace-nowrap">정산 중</span>;
    case 'COMPLETED':
      return <span className="px-2 py-0.5 rounded text-xs font-semibold bg-[#2F7D5B]/10 text-[#2F7D5B] border border-[#2F7D5B]/20 whitespace-nowrap">정산 완료</span>;
    case 'FAILED':
      return <span className="px-2 py-0.5 rounded text-xs font-semibold bg-[#B4413C]/10 text-[#B4413C] border border-[#B4413C]/20 whitespace-nowrap">정산 실패</span>;
  }
}

export default function MySettlementsPage() {
  const { loading: authLoading, user } = useRequireAuth('ESCORT');
  const [fromInput, setFromInput] = useState('');
  const [toInput, setToInput] = useState('');
  const [range, setRange] = useState({ from: '', to: '' });
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [result, setResult] = useState<Result>();
  const [profile, setProfile] = useState<EscortProfileDto | null>(null);
  const [profileUnavailable, setProfileUnavailable] = useState(false);
  const [selectedSettlementId, setSelectedSettlementId] = useState<number | null>(null);
  const [detail, setDetail] = useState<DetailResult | null>(null);
  const [requesting, setRequesting] = useState<number | null>(null);
  const [requestError, setRequestError] = useState<{ id: number; message: string } | null>(null);
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  const requestKey = JSON.stringify({ range, page, refresh, username: user?.username });

  useEffect(() => {
    if (!user || user.role !== 'ESCORT') return;
    let ignore = false;
    fetchSettlements({
      startDate: range.from ? toDayStart(range.from) : undefined,
      endDate: range.to ? toDayEnd(range.to) : undefined,
      page,
      size: 100,
    }).then((data) => {
      if (!ignore) setResult({ key: requestKey, ...data });
    }).catch((cause: unknown) => {
      if (!ignore) setResult({ key: requestKey, error: cause instanceof Error ? cause.message : '정산 내역을 조회하지 못했습니다.' });
    });
    return () => { ignore = true; };
  }, [user, requestKey, range.from, range.to, page]);

  useEffect(() => {
    if (!user || user.role !== 'ESCORT') return;
    let ignore = false;
    fetchMyEscortProfile().then((data) => {
      if (!ignore) setProfile(data);
    }).catch(() => {
      if (!ignore) setProfileUnavailable(true);
    });
    return () => { ignore = true; };
  }, [user]);

  useEffect(() => {
    if (selectedSettlementId === null) return;
    let ignore = false;
    const id = selectedSettlementId;
    setDetail(null);
    api<SettlementDto>(`/api/v1/settlements/${id}`).then((data) => {
      if (!ignore) setDetail({ id, data });
    }).catch((cause: unknown) => {
      if (!ignore) setDetail({ id, error: cause instanceof Error ? cause.message : '상세 내역을 조회하지 못했습니다.' });
    });
    return () => { ignore = true; };
  }, [selectedSettlementId, refresh]);

  useEffect(() => {
    if (!toastMsg) return;
    const timer = window.setTimeout(() => setToastMsg(null), 3000);
    return () => window.clearTimeout(timer);
  }, [toastMsg]);

  useEffect(() => {
    if (selectedSettlementId === null) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setSelectedSettlementId(null);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [selectedSettlementId]);

  const loading = result?.key !== requestKey;
  const amountsReady = !loading && !result?.error;
  const settlementRows = !loading ? (result?.settlements ?? []) : [];
  const pendingItems = settlementRows.filter((row) => row.status === 'PENDING');
  const completedItems = settlementRows.filter((row) => row.status === 'COMPLETED');
  const otherItems = settlementRows.filter((row) => row.status === 'PROCESSING' || row.status === 'FAILED');
  const pendingTotal = pendingItems.reduce((sum, item) => sum + item.payoutAmount, 0);
  const completedTotal = completedItems.reduce((sum, item) => sum + item.payoutAmount, 0);
  const otherTotal = otherItems.reduce((sum, item) => sum + item.payoutAmount, 0);

  const handleRequestPayout = async (settlementId: number) => {
    if (requesting !== null) return;
    setRequestError(null);
    setRequesting(settlementId);
    try {
      await requestSettlement(settlementId);
      setToastMsg('정산 요청이 정상 처리되었습니다.');
      setRefresh((prev) => prev + 1);
    } catch (cause) {
      setRequestError({ id: settlementId, message: cause instanceof Error ? cause.message : '정산 요청에 실패했습니다.' });
    } finally {
      setRequesting(null);
    }
  };

  const applyRange = () => {
    if (fromInput && toInput && fromInput > toInput) {
      setToastMsg('조회 시작일이 종료일보다 늦을 수 없습니다.');
      return;
    }
    setPage(0);
    setRange({ from: fromInput, to: toInput });
  };

  if (authLoading) {
    return <AppShell><div className="py-24 text-center text-[#67717C]">불러오는 중입니다.</div></AppShell>;
  }
  if (!user || user.role !== 'ESCORT') return null;

  const activeDetail = detail?.id === selectedSettlementId ? detail : null;
  const row = activeDetail?.data;
  const originalPrice = row ? row.payoutAmount + row.platformFee + row.penaltyAmount : 0;
  // hourlyPay 는 실제 PostDto 에 포함되어 있지만 기존 화면의 축약 타입에는 없습니다.
  const hourlyPay = row ? (row.post as typeof row.post & { hourlyPay?: number }).hourlyPay : undefined;

  return (
    <AppShell>
      <div className="w-full py-12 lg:py-16 bg-[#F7F8F8] min-h-[calc(100vh-80px)]">
        <div className="max-w-[1240px] mx-auto px-5 sm:px-8 lg:px-16 space-y-8">
          <Link href="/mypage" className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] hover:text-[#14243A] sc-focus">
            <ChevronLeft className="w-4 h-4" />
            <span>마이페이지로 돌아가기</span>
          </Link>

          {toastMsg && (
            <div role="status" className="fixed bottom-6 right-6 z-50 bg-[#14243A] text-white px-5 py-3.5 rounded-xl shadow-lg border border-white/20 text-sm font-medium flex items-center gap-2 animate-in fade-in slide-in-from-bottom-4">
              <CheckCircle2 className="w-5 h-5 text-[#2F7D5B]" /><span>{toastMsg}</span>
            </div>
          )}

          <div>
            <span className="text-xs font-semibold text-[#426B93] uppercase tracking-wider block mb-1">Manager Settlement</span>
            <h1 className="text-3xl font-semibold text-[#14243A] tracking-tight">내 정산</h1>
            <p className="text-sm text-[#67717C] mt-1">완료된 동행 내역의 수수료 공제 및 계좌 정산 상태를 확인합니다.</p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
            <div className="bg-white rounded-2xl border border-[#E1E5E8] p-5 shadow-xs space-y-2">
              <span className="text-xs font-semibold text-[#9A6B12] block">정산 예정액 ({pendingItems.length}건)</span>
              <span className="text-2xl font-bold text-[#14243A] tabular-nums block">{amountsReady ? formatMoney(pendingTotal) : '—'}</span>
              <p className="text-[11px] text-[#67717C]">현재 표시된 내역 기준</p>
            </div>
            <div className="bg-white rounded-2xl border border-[#E1E5E8] p-5 shadow-xs space-y-2">
              <span className="text-xs font-semibold text-[#2F7D5B] block">정산 완료액 ({completedItems.length}건)</span>
              <span className="text-2xl font-bold text-[#2F7D5B] tabular-nums block">{amountsReady ? formatMoney(completedTotal) : '—'}</span>
              <p className="text-[11px] text-[#67717C]">현재 표시된 내역 기준</p>
            </div>
            <div className="bg-white rounded-2xl border border-[#E1E5E8] p-5 shadow-xs space-y-2">
              <span className="text-xs font-semibold text-[#67717C] block">정산 중·실패 금액 ({otherItems.length}건)</span>
              <span className="text-2xl font-bold text-[#67717C] tabular-nums block">{amountsReady ? formatMoney(otherTotal) : '—'}</span>
              <p className="text-[11px] text-[#67717C]">현재 표시된 내역 기준</p>
            </div>
            <div className="bg-[#EDF0F2] rounded-2xl border border-[#E1E5E8] p-5 shadow-xs space-y-1.5">
              <span className="text-xs font-semibold text-[#426B93] block">등록 정산 계좌 정보</span>
              <span className="text-sm font-bold text-[#14243A] block">
                {profile ? `${profile.bankName || '은행 미등록'} · ${maskAccount(profile.accountNumber)}` : profileUnavailable ? '계좌 정보를 조회하지 못했습니다.' : '계좌 정보 확인 중…'}
              </span>
              <p className="text-[11px] text-[#67717C]">{profile ? `예금주: ${profile.accountHolder || '미등록'}` : '마이페이지에서 계좌 정보를 확인할 수 있습니다.'}</p>
            </div>
          </div>

          <div className="flex flex-wrap items-end justify-end gap-3 text-xs text-[#67717C]">
            <span className="mr-auto self-center">{range.from || range.to ? '선택 기간 조회' : '기본 조회 기간: 최근 한 달'} · 조회 결과 {result?.key === requestKey ? (result.totalElements ?? 0) : 0}건</span>
            <label className="flex flex-col gap-1 font-semibold">시작일
              <input type="date" value={fromInput} onChange={(event) => setFromInput(event.target.value)} className="h-10 rounded-lg border border-[#E1E5E8] bg-white px-3 text-sm text-[#14243A]" />
            </label>
            <label className="flex flex-col gap-1 font-semibold">종료일
              <input type="date" value={toInput} onChange={(event) => setToInput(event.target.value)} className="h-10 rounded-lg border border-[#E1E5E8] bg-white px-3 text-sm text-[#14243A]" />
            </label>
            <button type="button" onClick={applyRange} className="h-10 px-4 rounded-lg bg-[#14243A] text-white text-xs font-semibold hover:bg-[#1f3757]">조회</button>
            <button type="button" onClick={() => { setFromInput(''); setToInput(''); setRange({ from: '', to: '' }); setPage(0); }} className="h-10 px-4 rounded-lg border border-[#E1E5E8] bg-white text-[#14243A] text-xs font-semibold hover:bg-[#F7F8F8]">초기화</button>
          </div>

          <div className="bg-white rounded-2xl border border-[#E1E5E8] shadow-xs overflow-hidden">
            <div className="hidden min-[1100px]:grid grid-cols-12 gap-4 px-6 py-3.5 bg-[#EDF0F2]/60 border-b border-[#E1E5E8] text-xs font-semibold text-[#67717C]">
              <div className="col-span-2">동행일</div>
              <div className="col-span-4">공고 및 병원</div>
              <div className="col-span-2 text-center">소요 시간</div>
              <div className="col-span-2 text-right">정산 금액</div>
              <div className="col-span-1 text-center">상태</div>
              <div className="col-span-1 text-center">액션</div>
            </div>

            {loading ? (
              <div className="px-6 py-14 text-center text-sm text-[#67717C]">정산 내역을 불러오는 중입니다.</div>
            ) : result?.error ? (
              <div role="alert" className="px-6 py-14 text-center text-sm text-[#B4413C]">정산 내역을 불러오지 못했습니다. ({result.error})</div>
            ) : settlementRows.length === 0 ? (
              <div className="px-6 py-14 text-center space-y-4">
                <p className="text-sm text-[#67717C]">해당 기간에 정산 내역이 없습니다.</p>
                <Link href="/posts" className="inline-flex h-10 items-center justify-center rounded-lg border border-[#E1E5E8] bg-white px-5 text-xs font-semibold text-[#14243A] hover:bg-[#F7F8F8]">공고 찾기</Link>
              </div>
            ) : (
              <div className="divide-y divide-[#E1E5E8]">
                {settlementRows.map((item) => (
                  <div key={item.id} role="button" tabIndex={0} onClick={() => setSelectedSettlementId(item.id)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setSelectedSettlementId(item.id); } }} className="group px-6 py-5 hover:bg-[#F7F8F8] transition-colors cursor-pointer focus-visible:outline focus-visible:outline-[#426B93]">
                    <div className="hidden min-[1100px]:grid grid-cols-12 gap-4 items-center text-sm">
                      <div className="col-span-2 text-xs text-[#67717C] tabular-nums font-medium">{formatDate(item.post.escortStartAt)}</div>
                      <div className="col-span-4 pr-2">
                        <span className="font-semibold text-base text-[#14243A] group-hover:text-[#426B93] transition-colors block">{item.post.title}</span>
                        <span className="text-xs text-[#67717C]">{item.post.hospitalName}{typeof (item.post as typeof item.post & { hourlyPay?: number }).hourlyPay === 'number' ? ` · 시급 ${formatMoney((item.post as typeof item.post & { hourlyPay: number }).hourlyPay)}` : ''}</span>
                      </div>
                      <div className="col-span-2 text-center text-sm text-[#202832] tabular-nums">{formatHours(item.post.escortHours)}</div>
                      <div className="col-span-2 text-right font-bold text-base text-[#14243A] tabular-nums">{formatMoney(item.payoutAmount)}</div>
                      <div className="col-span-1 text-center"><StatusBadge status={item.status} /></div>
                      <div className="col-span-1 text-center">
                        {item.status === 'PENDING' ? (
                          <button type="button" disabled={requesting !== null} onClick={(event) => { event.stopPropagation(); void handleRequestPayout(item.id); }} className="h-[36px] px-3 rounded-lg bg-[#14243A] text-white text-xs font-semibold hover:bg-[#1f3757] shadow-xs disabled:opacity-60 whitespace-nowrap">{requesting === item.id ? '요청 중…' : '정산 요청'}</button>
                        ) : (
                          <button type="button" onClick={(event) => { event.stopPropagation(); setSelectedSettlementId(item.id); }} className="h-[36px] px-3 rounded-lg border border-[#E1E5E8] text-xs font-semibold text-[#14243A] hover:bg-white">명세서</button>
                        )}
                      </div>
                    </div>
                    <div className="min-[1100px]:hidden space-y-2">
                      <div className="flex items-center justify-between text-xs"><span className="text-[#67717C] tabular-nums">{formatDate(item.post.escortStartAt)}</span><StatusBadge status={item.status} /></div>
                      <h3 className="font-semibold text-base text-[#14243A] group-hover:text-[#426B93]">{item.post.title}</h3>
                      <div className="flex items-center justify-between text-xs pt-1"><span className="text-[#67717C]">{item.post.hospitalName} ({formatHours(item.post.escortHours)})</span><span className="text-sm font-bold text-[#14243A] tabular-nums">{formatMoney(item.payoutAmount)}</span></div>
                      {item.status === 'PENDING' && <button type="button" disabled={requesting !== null} onClick={(event) => { event.stopPropagation(); void handleRequestPayout(item.id); }} className="h-[36px] px-3 rounded-lg bg-[#14243A] text-white text-xs font-semibold hover:bg-[#1f3757] disabled:opacity-60">{requesting === item.id ? '요청 중…' : '정산 요청'}</button>}
                    </div>
                    {requestError?.id === item.id && <p role="alert" className="mt-2 text-xs text-[#B4413C]">{requestError.message}</p>}
                  </div>
                ))}
              </div>
            )}
          </div>

          {(result?.totalPages ?? 0) > 1 && !loading && (
            <div className="flex justify-center items-center gap-4 text-sm text-[#67717C]">
              <button type="button" disabled={page === 0} onClick={() => setPage((prev) => Math.max(0, prev - 1))} className="px-4 py-2 rounded-lg border border-[#E1E5E8] bg-white disabled:opacity-40">이전</button>
              <span>{page + 1} / {result?.totalPages} 페이지</span>
              <button type="button" disabled={page + 1 >= (result?.totalPages ?? 1)} onClick={() => setPage((prev) => prev + 1)} className="px-4 py-2 rounded-lg border border-[#E1E5E8] bg-white disabled:opacity-40">다음</button>
            </div>
          )}

          {selectedSettlementId !== null && (
            <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4" onMouseDown={(event) => { if (event.target === event.currentTarget) setSelectedSettlementId(null); }}>
              <div role="dialog" aria-modal="true" aria-labelledby="settlement-detail-title" className="bg-white rounded-2xl max-w-lg w-full p-6 sm:p-8 space-y-6 shadow-xl animate-in fade-in zoom-in-95 duration-150 max-h-[calc(100vh-32px)] overflow-y-auto">
                <div className="flex items-center justify-between pb-4 border-b border-[#E1E5E8]">
                  <div className="flex items-center gap-2"><Receipt className="w-5 h-5 text-[#426B93]" /><h3 id="settlement-detail-title" className="text-xl font-semibold text-[#14243A]">정산 상세 명세서</h3></div>
                  <button type="button" aria-label="정산 상세 닫기" onClick={() => setSelectedSettlementId(null)} className="p-1 text-[#67717C] hover:text-[#14243A]"><X className="w-5 h-5" /></button>
                </div>

                {!activeDetail ? <p className="py-10 text-center text-sm text-[#67717C]">정산 상세 내역을 불러오는 중입니다.</p> : activeDetail.error ? <p role="alert" className="py-10 text-center text-sm text-[#B4413C]">{activeDetail.error}</p> : row ? (
                  <>
                    <div className="p-4 bg-[#F7F8F8] rounded-xl border border-[#E1E5E8] flex justify-between items-center text-xs gap-3">
                      <div><span className="text-[#67717C] block">공고명</span><span className="font-semibold text-sm text-[#14243A] mt-0.5 block">{row.post.title}</span></div>
                      <div><StatusBadge status={row.status} /></div>
                    </div>
                    <div className="space-y-3 text-xs">
                      <div className="flex justify-between gap-3"><span className="text-[#67717C]">방문 병원</span><span className="font-medium text-[#14243A] text-right">{row.post.hospitalName}</span></div>
                      <div className="flex justify-between gap-3"><span className="text-[#67717C]">동행 활동 일자</span><span className="font-medium text-[#14243A] tabular-nums">{formatDate(row.post.escortStartAt)}</span></div>
                      <div className="flex justify-between gap-3"><span className="text-[#67717C]">기본 금액 (시급 × 시간)</span><span className="font-semibold text-[#14243A] tabular-nums text-right">{typeof hourlyPay === 'number' ? `${formatMoney(hourlyPay)} × ${formatHours(row.post.escortHours)} = ` : ''}{formatMoney(originalPrice)}</span></div>
                      <div className="flex justify-between gap-3 text-[#67717C]"><span>플랫폼 이용 수수료</span><span className="tabular-nums">-{formatMoney(row.platformFee)}</span></div>
                      {row.penaltyAmount > 0 && <div className="flex justify-between gap-3 text-[#B4413C] font-semibold bg-[#B4413C]/5 p-2 rounded-lg"><span>노쇼 패널티 차감</span><span className="tabular-nums">-{formatMoney(row.penaltyAmount)}</span></div>}
                      <div className="pt-3 border-t border-[#E1E5E8] flex justify-between items-baseline gap-3 text-sm"><span className="font-bold text-[#14243A]">최종 정산 금액</span><span className="text-2xl font-bold text-[#14243A] tabular-nums">{formatMoney(row.payoutAmount)}</span></div>
                      <div className="flex justify-between gap-3 text-[#67717C] pt-1"><span>정산 기준일</span><span className="font-medium text-[#14243A]">{formatDate(row.settledAt)}</span></div>
                    </div>
                    <div className="bg-[#EDF0F2] rounded-xl p-4 text-xs text-[#202832] space-y-1.5 border border-[#E1E5E8]">
                      <div className="flex items-center gap-1.5 text-[#426B93] font-semibold"><ShieldCheck className="w-4 h-4" /><span>노쇼 관리 및 패널티 정책</span></div>
                      <p className="text-[#67717C] leading-relaxed">상호 신뢰를 위해 정당한 사유 없는 일방 취소나 노쇼 발생 시 서비스 운영 정책에 따른 페널티 및 유효 등급 하향 조치가 적용됩니다.</p>
                    </div>
                  </>
                ) : null}
                <div className="flex justify-end pt-2"><button type="button" onClick={() => setSelectedSettlementId(null)} className="h-[44px] px-6 rounded-xl bg-[#14243A] text-white text-xs font-semibold hover:bg-[#1f3757]">닫기</button></div>
              </div>
            </div>
          )}
        </div>
      </div>
    </AppShell>
  );
}
