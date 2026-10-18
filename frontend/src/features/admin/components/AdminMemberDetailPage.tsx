'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { ArrowLeft, Award, Clock, Edit2, History, ShieldAlert, UserX } from 'lucide-react';
import { useRequireAuth } from '@/features/auth';
import { MEMBERS } from '../model';
import AdminShell from './AdminShell';
import { AdminRoleBadge } from './AdminMemberBadges';

const card = 'rounded-xl border border-[#E1E5E8] bg-white shadow-xs';
const caption = 'mb-1 block text-xs text-[#67717C]';

function DetailField({ label, value, mono = false }: { label: string; value?: string; mono?: boolean }) {
  return (
    <div>
      <span className={caption}>{label}</span>
      <span className={`${mono ? 'font-mono' : ''} text-sm font-semibold text-[#14243A]`}>{value?.trim() || '—'}</span>
    </div>
  );
}

/**
 * 관리자 회원 상세: Signature Care AdminUserDetailView의 레이아웃 구조.
 * 현재 회원 목록이 Mock이므로 목록에 실제 존재하는 필드만 표시합니다.
 * 가짜 정지/탈퇴/수정 성공 처리 및 다른 회원에게 공통 허위 프로필 값 사용 금지.
 */
export default function AdminMemberDetailPage() {
  const { loading, user } = useRequireAuth('ADMIN');
  const params = useParams<{ memberId: string }>();
  const id = typeof params.memberId === 'string' ? decodeURIComponent(params.memberId) : '';
  const member = MEMBERS.find((item) => item.id === id);

  if (loading) return <div className="min-h-screen bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]">불러오는 중입니다...</div>;
  if (!user || user.role !== 'ADMIN') return null;

  return (
    <AdminShell>
      {!member ? (
        <div className={`${card} px-6 py-14 text-center`}>
          <h1 className="text-xl font-bold text-[#14243A]">회원을 찾을 수 없습니다.</h1>
          <p className="mt-2 text-sm text-[#67717C]">회원 관리 목록에서 다시 확인해 주세요.</p>
          <Link href="/admin/members" className="mt-6 inline-flex items-center gap-2 rounded-lg border border-[#E1E5E8] px-4 py-2 text-sm font-semibold text-[#14243A] transition-colors hover:bg-[#F7F8F8]">
            <ArrowLeft className="size-4" /> 회원 목록으로
          </Link>
        </div>
      ) : (
        <div className="space-y-6">
          {/* 원본 상단: 뒤로가기 / 회원명 / 상태 / 처리 버튼 */}
          <section className={`${card} flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between`}>
            <div className="flex min-w-0 items-start gap-4 sm:items-center">
              <Link href="/admin/members" aria-label="회원 목록으로" title="회원 목록으로"
                className="flex size-9 shrink-0 items-center justify-center rounded-lg border border-[#E1E5E8] text-[#14243A] transition-colors hover:bg-[#F7F8F8]">
                <ArrowLeft className="size-5" />
              </Link>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2.5">
                  <h1 className="text-2xl font-bold tracking-tight text-[#14243A]">{member.name}</h1>
                  <span className="font-mono text-sm text-[#67717C]">({member.id})</span>
                  <span className="rounded bg-[#F7F8F8] px-2 py-0.5 text-xs font-medium text-[#67717C]">상태 미확인</span>
                </div>
                <p className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-[#67717C]">
                  <span>역할:</span><AdminRoleBadge role={member.role === 'manager' ? 'escort' : 'client'} />
                  <span>· 가입일: {member.joinedAt}</span>
                </p>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <button type="button" disabled title="실제 회원 수정 API 연동 후 사용할 수 있습니다"
                className="inline-flex cursor-not-allowed items-center gap-1.5 rounded-lg border border-[#E1E5E8] px-3.5 py-2 text-xs font-semibold text-[#14243A] opacity-65">
                <Edit2 className="size-3.5 text-[#426B93]" /> 정보 수정
              </button>
              <button type="button" disabled title="회원 상태 API 연동 후 사용할 수 있습니다"
                className="inline-flex cursor-not-allowed items-center gap-1.5 rounded-lg border border-[#9A6B12]/30 bg-[#9A6B12]/10 px-3.5 py-2 text-xs font-semibold text-[#9A6B12] opacity-65">
                <Clock className="size-3.5" /> 기간 정지
              </button>
              <button type="button" disabled title="회원 제재 API 연동 후 사용할 수 있습니다"
                className="inline-flex cursor-not-allowed items-center gap-1.5 rounded-lg border border-[#B4413C]/30 bg-[#B4413C]/10 px-3.5 py-2 text-xs font-semibold text-[#B4413C] opacity-65">
                <ShieldAlert className="size-3.5" /> 영구정지
              </button>
              <button type="button" disabled title="회원 탈퇴 API 연동 후 사용할 수 있습니다"
                className="inline-flex cursor-not-allowed items-center gap-1.5 rounded-lg border border-[#E1E5E8] px-3 py-2 text-xs font-semibold text-[#67717C] opacity-65">
                <UserX className="size-3.5" /> 회원 탈퇴
              </button>
            </div>
          </section>

          {/* 원본 1번 카드: 3열 기본 정보 */}
          <section className={`${card} p-6`}>
            <h2 className="mb-4 border-b border-[#E1E5E8] pb-4 text-base font-bold text-[#14243A]">기본 정보</h2>
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
              <DetailField label="아이디" value={member.id} mono />
              <DetailField label="이름" value={member.name} />
              <DetailField label="이메일" value={member.email} mono />
              <DetailField label="전화번호" value={member.phone} mono />
              <DetailField label="성별" />
              <DetailField label="생년월일" />
              <DetailField label="거주 지역" />
              <DetailField label="회원 가입일" value={member.joinedAt} mono />
              <DetailField label="역할" value={member.role === 'manager' ? '동행 매니저' : '의뢰인'} />
              <DetailField label="회원 상태" />
            </div>
            <p className="mt-6 text-xs text-[#67717C]">현재 목록은 시연용 회원 데이터입니다. 제공되지 않은 상세 항목은 표시하지 않았습니다.</p>
          </section>

          {/* 원본 매니저 프로필 카드: 데이터 미제공 상태는 빈 표기로 유지 */}
          {member.role === 'manager' && (
            <section className={`${card} p-6`}>
              <h2 className="mb-4 flex items-center gap-2 border-b border-[#E1E5E8] pb-4 text-base font-bold text-[#14243A]">
                <Award className="size-5 text-[#426B93]" /> 동행 매니저 프로필
              </h2>
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                {['등급', '동행 완료 건수', '평균 평점', '노쇼 횟수'].map((label) => (
                  <div key={label} className="rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] p-4">
                    <span className={caption}>{label}</span>
                    <span className="text-xl font-bold text-[#14243A]">—</span>
                  </div>
                ))}
              </div>
              <div className="mt-4">
                <span className={caption}>자기소개</span>
                <p className="rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] p-3 text-sm text-[#67717C]">프로필 API 연동 후 표시됩니다.</p>
              </div>
            </section>
          )}

          {/* 원본 하단: 상태 변경 이력 */}
          <section className={`${card} overflow-hidden`}>
            <div className="flex items-center justify-between border-b border-[#E1E5E8] px-6 py-4">
              <h2 className="flex items-center gap-2 text-base font-bold text-[#14243A]">
                <History className="size-4 text-[#426B93]" /> 상태 변경 이력
              </h2>
              <span className="text-xs text-[#67717C]">제재 이력 API 미연동</span>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[610px] text-left text-sm">
                <thead className="border-b border-[#E1E5E8] bg-[#F7F8F8] text-xs font-bold text-[#67717C]">
                  <tr>
                    <th className="px-5 py-3">처리 일시</th>
                    <th className="px-5 py-3">이전 상태 → 변경 상태</th>
                    <th className="px-5 py-3">사유</th>
                    <th className="px-5 py-3">처리 관리자</th>
                  </tr>
                </thead>
                <tbody><tr><td colSpan={4} className="px-5 py-14 text-center text-sm text-[#67717C]">상태 변경 이력은 백엔드 연동 후 표시됩니다.</td></tr></tbody>
              </table>
            </div>
          </section>
        </div>
      )}
    </AdminShell>
  );
}
