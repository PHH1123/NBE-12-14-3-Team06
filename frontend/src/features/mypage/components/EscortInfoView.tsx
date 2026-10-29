'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState, type FormEvent } from 'react';
import {
  User, FileText, CreditCard, Star, Video, ChevronRight, LogOut,
} from 'lucide-react';
import { fetchMyProfile, useAuth, type CurrentUser } from '@/features/auth';
import { ApiError } from '@/lib/api';
import { REGIONS } from '@/lib/regions';
import {
  createMyEscortProfile,
  fetchMyEscortProfile,
  updateMyEscortProfile,
  updateMyProfile,
  type EscortProfileWriteRequest,
} from '../api';
import type { EscortProfileDto } from '../types';
import type { EscortGrade } from '@/features/application';
import ManagerGradeCharacter from '@/components/ui/ManagerGradeCharacter';
import DropdownSelect from '@/components/ui/DropdownSelect';
import PageFeedback from '@/components/ui/PageFeedback';
import { BANKS } from '@/features/signup/model/banks';
import WithdrawAccountButton from './WithdrawAccountButton';

const GRADE_ORDER: EscortGrade[] = ['SEED', 'SPROUT', 'FLOWER', 'EGGPLANT'];
const GRADE_DETAILS: Record<EscortGrade, { label: string; min: number; next: number | null }> = {
  SEED: { label: '씨앗', min: 0, next: 5 },
  SPROUT: { label: '새싹', min: 5, next: 10 },
  FLOWER: { label: '꽃', min: 10, next: 30 },
  EGGPLANT: { label: '가지', min: 30, next: null },
};
const INPUT = 'w-full h-11 rounded-lg border border-[#E1E5E8] bg-white px-3.5 text-sm text-[#14243A] placeholder:text-[#8B949E] outline-none focus:border-[#426B93] focus:ring-1 focus:ring-[#426B93]';
const FIELD_LABEL = 'mb-1.5 block text-xs font-semibold text-[#14243A]';
const PRIMARY = 'rounded-xl bg-[#14243A] px-5 py-2.5 text-xs font-semibold text-white transition-colors hover:bg-[#26394F] disabled:cursor-not-allowed disabled:opacity-60';
const SECONDARY = 'rounded-xl border border-[#E1E5E8] bg-white px-5 py-2.5 text-xs font-semibold text-[#14243A] transition-colors hover:bg-[#F7F8F8]';

function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && (error.statusCode?.startsWith('404') ?? false);
}
function maskAccount(accountNumber: string | null): string {
  if (!accountNumber) return '미등록';
  const compact = accountNumber.replace(/\s/g, '');
  return compact.length > 4 ? `****${compact.slice(-4)}` : '****';
}
function ageOf(birthDate: string): number | null {
  const [year, month, day] = birthDate.slice(0, 10).split('-').map(Number);
  if (!year || !month || !day) return null;
  const today = new Date();
  const age = today.getFullYear() - year - (today.getMonth() + 1 < month || (today.getMonth() + 1 === month && today.getDate() < day) ? 1 : 0);
  return age >= 0 && age < 130 ? age : null;
}

const menuItems = [
  { href: '/mypage', label: '내 정보', icon: User },
  { href: '/mypage/applications', label: '내가 신청한 공고', icon: FileText },
  { href: '/mypage/settlements', label: '내 정산', icon: CreditCard },
  { href: '/mypage/reviews', label: '받은 리뷰', icon: Star },
  { href: '/mypage/education', label: '교육 영상', icon: Video },
];

function GradeJourney({ name, profile }: { name: string; profile: EscortProfileDto }) {
  const grade = profile.grade;
  const info = GRADE_DETAILS[grade];
  const completed = profile.completedCount;
  const currentIndex = GRADE_ORDER.indexOf(grade);
  const baseIndex = completed >= 30 ? 3 : completed >= 10 ? 2 : completed >= 5 ? 1 : 0;
  const hasPenalty = currentIndex < baseIndex;
  const progress = info.next === null ? 100 : Math.min(100, Math.max(0, ((completed - info.min) / (info.next - info.min)) * 100));
  const rating = profile.averageRating;

  return (
    <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <ManagerGradeCharacter grade={grade} className="w-16 h-16 shrink-0" />
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h2 className="text-xl font-bold text-[#14243A]">{name} 매니저</h2>
              <span className="sr-only">현재 등급: {info.label}</span>

            </div>
            <p className="text-xs text-[#426B93] font-semibold mt-1">
              {info.next === null ? `최고 등급 · 완료 동행 ${completed}건` : `다음 등급까지 ${Math.max(0, info.next - completed)}건 남음 (완료 동행 ${completed}건)`}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-6 text-xs text-[#67717C] pt-2 sm:pt-0 sm:border-l border-[#E1E5E8] sm:pl-6">
          <div>
            <span className="block text-[11px]">평균 평점</span>
            <span className="text-lg font-bold text-[#14243A]">{rating === null ? '평가 없음' : `★ ${rating.toFixed(1)}`}</span>
          </div>
          <div>
            <span className="block text-[11px]">교육 인증</span>
            <span className="text-lg font-bold text-[#14243A]">{profile.verified ? '완료' : '대기 중'}</span>
          </div>
        </div>
      </div>
      <div className="space-y-2">
        <div className="flex justify-between text-xs">
          <span className="text-[#67717C]">{info.next === null ? '최고 등급 달성' : '다음 등급 승급 진행률'}</span>
          <span className="font-bold text-[#14243A] tabular-nums">{Math.round(progress)}%</span>
        </div>
        <div className="w-full h-2.5 bg-[#EDF0F2] rounded-full overflow-hidden">
          <div className="h-full bg-[#426B93] transition-all duration-500 rounded-full" style={{ width: `${progress}%` }} />
        </div>
      </div>
      <div className="grid grid-cols-4 gap-2 pt-2 border-t border-[#E1E5E8] text-center text-xs">
        {GRADE_ORDER.map((item) => {
          const detail = GRADE_DETAILS[item];
          const isCurrent = grade === item;
          return (
            <div key={item} className={`p-2.5 rounded-xl border ${isCurrent ? 'bg-[#426B93]/5 border-[#426B93]' : 'border-transparent'}`}>
              <div className="flex items-center justify-center gap-1">
                <ManagerGradeCharacter grade={item} className={`h-6 w-6 shrink-0 ${isCurrent ? '' : 'grayscale opacity-50'}`} bare />
                <span className={`font-bold ${isCurrent ? 'text-[#426B93]' : 'text-[#8B949E]'}`}>{detail.label}</span>
              </div>
              <span className={`block text-[11px] ${isCurrent ? 'text-[#67717C]' : 'text-[#A3ABB4]'}`}>{detail.min}건 이상</span>
            </div>
          );
        })}
      </div>
      <p className="text-[11px] text-[#67717C] bg-[#F7F8F8] p-3 rounded-lg border border-[#E1E5E8]">
        {hasPenalty ? '완료 동행 횟수에 따른 기본 등급과 실제 등급이 다릅니다. 노쇼 페널티가 반영되었을 수 있습니다.' : '등급은 완료 동행 횟수와 노쇼 페널티 정책을 함께 반영합니다.'}
      </p>
    </div>
  );
}

/** 자기소개와 계좌는 동일한 PUT API를 사용하므로, 수정하지 않은 필드를 함께 보존합니다. */
function EscortProfileEditor({ profile, section, onSaved, onCancel }: {
  profile: EscortProfileDto | null;
  section: 'account' | 'intro';
  onSaved: (value: EscortProfileDto) => void;
  onCancel: () => void;
}) {
  const [intro, setIntro] = useState(profile?.intro ?? '');
  const [bankName, setBankName] = useState(profile?.bankName ?? '');
  const [accountHolder, setAccountHolder] = useState(profile?.accountHolder ?? '');
  const [accountNumber, setAccountNumber] = useState(profile?.accountNumber ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  // 기존 DB에 선택 목록에 없는 은행명이 저장되어 있어도 임의로 바꾸지 않습니다.
  const bankOptions: { value: string; label: string }[] = BANKS.map((bank) => ({ value: bank, label: bank }));
  if (bankName && !BANKS.some((bank) => bank === bankName)) {
    bankOptions.unshift({ value: bankName, label: bankName });
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;
    setSaving(true);
    setError('');
    try {
      const body: EscortProfileWriteRequest = { intro, bankName, accountHolder, accountNumber };
      if (profile) await updateMyEscortProfile(body);
      else await createMyEscortProfile(body);
      // 수정 API 응답에는 grade가 없으므로 전체 프로필을 다시 조회합니다.
      const refreshed = await fetchMyEscortProfile();
      onSaved(refreshed);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '정보를 저장하지 못했습니다.');
    } finally { setSaving(false); }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      {section === 'account' ? (
        <>
          <p className="text-xs text-[#67717C]">실제 정산에 사용할 계좌 정보를 입력해주세요.</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className={FIELD_LABEL} htmlFor="mypage-bank">은행</label>
              <DropdownSelect id="mypage-bank" name="bankName" required value={bankName} onValueChange={setBankName} placeholder="은행을 선택해주세요." options={bankOptions} size="form" />
            </div>
            <div><label className={FIELD_LABEL} htmlFor="mypage-holder">예금주</label><input id="mypage-holder" className={INPUT} maxLength={50} required value={accountHolder} onChange={(event) => setAccountHolder(event.target.value)} /></div>
          </div>
          <div><label className={FIELD_LABEL} htmlFor="mypage-account">계좌번호</label><input id="mypage-account" className={INPUT} inputMode="numeric" maxLength={30} required value={accountNumber} onChange={(event) => setAccountNumber(event.target.value)} /></div>
          {!profile && (
            <div>
              <label className={FIELD_LABEL} htmlFor="mypage-first-intro">자기소개</label>
              <textarea id="mypage-first-intro" className={`${INPUT} h-24 resize-y py-3`} required maxLength={500} value={intro} onChange={(event) => setIntro(event.target.value)} />
              <p className="mt-1 text-[11px] text-[#67717C]">최초 등록 시에는 자기소개도 함께 입력해야 합니다.</p>
            </div>
          )}
        </>
      ) : (
        <div>
          <label className={FIELD_LABEL} htmlFor="mypage-intro">자기소개</label>
          <textarea id="mypage-intro" className={`${INPUT} h-24 resize-y py-3`} required maxLength={500} value={intro} onChange={(event) => setIntro(event.target.value)} />
        </div>
      )}
      {error && <p role="alert" className="text-xs font-medium text-[#B4413C]">{error}</p>}
      <div className="flex justify-end gap-2"><button type="button" className={SECONDARY} onClick={onCancel}>취소</button><button type="submit" className={PRIMARY} disabled={saving}>{saving ? '저장 중…' : '저장'}</button></div>
    </form>
  );
}

function BaseEditor({ user, onSaved, onCancel }: { user: CurrentUser; onSaved: (value: CurrentUser) => void; onCancel: () => void }) {
  const [email, setEmail] = useState(user.email);
  const [name, setName] = useState(user.name);
  const [birthDate, setBirthDate] = useState(user.birthDate.slice(0, 10));
  const [phoneNum, setPhoneNum] = useState(user.phoneNum);
  const [region, setRegion] = useState(user.region);
  const [password, setPassword] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;
    setSaving(true);
    setError('');
    try {
      const saved = await updateMyProfile({ password, email, name, birthDate, phoneNum, region });
      onSaved(saved);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '기본 정보를 수정하지 못했습니다.');
    } finally { setSaving(false); }
  }
  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="space-y-1 text-sm text-[#67717C]">
        <p>아이디와 성별은 변경할 수 없습니다.</p>
        <p>정보 수정 시 비밀번호 입력이 필요하며, 기존 비밀번호를 입력하면 그대로 유지됩니다.</p>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div><label className={FIELD_LABEL} htmlFor="mypage-email">이메일</label><input type="email" id="mypage-email" className={INPUT} required value={email} onChange={(event) => setEmail(event.target.value)} /></div>
        <div><label className={FIELD_LABEL} htmlFor="mypage-name">성명</label><input id="mypage-name" className={INPUT} required value={name} onChange={(event) => setName(event.target.value)} /></div>
        <div><label className={FIELD_LABEL} htmlFor="mypage-birth">생년월일</label><input type="date" id="mypage-birth" className={INPUT} required value={birthDate} onChange={(event) => setBirthDate(event.target.value)} /></div>
        <div><label className={FIELD_LABEL} htmlFor="mypage-phone">전화번호</label><input id="mypage-phone" className={INPUT} required value={phoneNum} onChange={(event) => setPhoneNum(event.target.value)} /></div>
        <div><label className={FIELD_LABEL} htmlFor="mypage-region">거주 지역</label><DropdownSelect id="mypage-region" name="region" required value={region} onValueChange={setRegion} placeholder="지역을 선택해주세요." options={[...(!REGIONS.includes(region as typeof REGIONS[number]) && region ? [{ value: region, label: region }] : []), ...REGIONS.map((value) => ({ value, label: value }))]} size="form" /></div>
        <div><label className={FIELD_LABEL} htmlFor="mypage-password">비밀번호</label><input type="password" id="mypage-password" placeholder="비밀번호를 입력해 주세요." className={INPUT} required value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" /><p className="mt-1.5 text-xs text-[#67717C]">새 비밀번호를 입력하면 계정 비밀번호가 변경됩니다.</p></div>
      </div>
      {error && <p role="alert" className="text-xs font-medium text-[#B4413C]">{error}</p>}
      <div className="flex justify-end gap-2"><button type="button" className={SECONDARY} onClick={onCancel}>취소</button><button type="submit" className={PRIMARY} disabled={saving}>{saving ? '저장 중…' : '수정 완료'}</button></div>
    </form>
  );
}

export default function EscortInfoView() {
  const router = useRouter();
  const { signOut, reload } = useAuth();
  const [state, setState] = useState<{ user?: CurrentUser; profile: EscortProfileDto | null; error?: string }>();
  const [editBase, setEditBase] = useState(false);
  const [editAccount, setEditAccount] = useState(false);
  const [editIntro, setEditIntro] = useState(false);

  useEffect(() => {
    let canceled = false;
    Promise.all([
      fetchMyProfile(),
      fetchMyEscortProfile().catch((error) => {
        if (isNotFound(error)) return null;
        throw error;
      }),
    ])
      .then(([user, profile]) => { if (!canceled) setState({ user, profile }); })
      .catch((error: Error) => { if (!canceled) setState({ profile: null, error: error.message }); });
    return () => { canceled = true; };
  }, []);

  if (!state?.user) {
    return <PageFeedback
      kind={state?.error ? 'error' : 'loading'}
      title={state?.error ? '매니저 프로필을 불러오지 못했습니다.' : '내 정보를 불러오는 중입니다.'}
      description={state?.error}
    />;
  }

  const { user, profile } = state;
  const age = ageOf(user.birthDate);
  const registeredAccount = profile?.accountNumber ? `${profile.bankName || '은행 미등록'} ${maskAccount(profile.accountNumber)} (예금주: ${profile.accountHolder || '-'})` : '등록된 정산 계좌가 없습니다.';

  return (
    <div className="w-full py-12 lg:py-16">
      <div className="max-w-[1240px] mx-auto px-5 sm:px-8 lg:px-16 space-y-8">
        <div>
          <h1 className="text-3xl font-semibold text-[#14243A] tracking-tight">매니저 마이페이지</h1>
          <p className="text-sm text-[#67717C] mt-1">동행 매니저 프로필 및 등급 승급 현황, 계좌 설정을 관리합니다.</p>
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          <nav aria-label="매니저 마이페이지 메뉴" className="lg:col-span-3 bg-white rounded-2xl border border-[#E1E5E8] p-3 shadow-xs space-y-1">
            {menuItems.map((item) => {
              const Icon = item.icon;
              const active = item.href === '/mypage';
              return (
                <Link key={item.href} href={item.href} aria-current={active ? 'page' : undefined} className={`w-full flex items-center justify-between px-4 py-3 rounded-xl text-xs font-semibold transition-colors sc-focus ${active ? 'bg-[#14243A] text-white shadow-xs' : 'text-[#67717C] hover:bg-[#F7F8F8] hover:text-[#14243A]'}`}>
                  <span className="flex items-center gap-2.5"><Icon className="w-4 h-4" /><span>{item.label}</span></span>
                  <ChevronRight className={`w-3.5 h-3.5 ${active ? 'text-white/60' : 'text-[#67717C]'}`} />
                </Link>
              );
            })}
            <button type="button" onClick={async () => { await signOut(); router.replace('/'); }} className="w-full flex items-center justify-between px-4 py-3 rounded-xl text-xs font-semibold transition-colors text-[#B4413C] hover:bg-[#F7F8F8]">
              <span className="flex items-center gap-2.5"><LogOut className="w-4 h-4" />로그아웃</span><ChevronRight className="w-3.5 h-3.5" />
            </button>
            <div className="my-1 border-t border-[#E1E5E8]" />
            <WithdrawAccountButton name={user.name} />
          </nav>
          <div className="lg:col-span-9 space-y-6">
            {profile ? <GradeJourney name={user.name} profile={profile} /> : (
              <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs">
                <h2 className="text-xl font-bold text-[#14243A]">동행 매니저 정보</h2>
                <p className="mt-2 text-sm text-[#67717C]">등록된 추가 프로필이 없습니다. 자기소개 및 정산 계좌를 등록해주세요.</p>
                <button className={`${PRIMARY} mt-4`} onClick={() => { setEditAccount(true); setEditBase(false); setEditIntro(false); }}>추가 정보 등록하기</button>
              </div>
            )}
            <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs space-y-6">
              <div className="flex items-center justify-between border-b border-[#E1E5E8] pb-3 gap-4">
                <h3 className="text-base font-semibold text-[#14243A]">기본 등록 정보</h3>
                {!editBase && <button type="button" onClick={() => { setEditBase(true); setEditAccount(false); setEditIntro(false); }} className="text-xs font-semibold text-[#426B93] hover:text-[#14243A]">수정하기</button>}
              </div>
              {editBase ? (
                <BaseEditor user={user} onCancel={() => setEditBase(false)} onSaved={(next) => { setState((old) => old && { ...old, user: next }); setEditBase(false); void reload(); }} />
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 text-xs">
                  <div><span className="text-[#67717C] block mb-1">성명 / 성별 / 나이</span><span className="font-semibold text-sm text-[#14243A]">{user.name} ({user.gender === 'FEMALE' ? '여성' : '남성'}{age === null ? '' : `, ${age}세`})</span></div>
                  <div><span className="text-[#67717C] block mb-1">연락처</span><span className="font-semibold text-sm text-[#14243A]">{user.phoneNum}</span></div>
                  <div><span className="text-[#67717C] block mb-1">활동 거점 지역</span><span className="font-semibold text-sm text-[#14243A]">{user.region}</span></div>
                  <div>
                    <span className="text-[#67717C] block mb-1">정산 계좌 정보</span>
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                      <span className="font-semibold text-sm text-[#14243A]">{registeredAccount}</span>
                      {!editAccount && <button type="button" onClick={() => { setEditAccount(true); setEditBase(false); setEditIntro(false); }} className="text-xs font-semibold text-[#426B93] hover:text-[#14243A]">{profile ? '계좌 변경' : '계좌 등록'}</button>}
                    </div>
                  </div>
                  <div><span className="text-[#67717C] block mb-1">아이디</span><span className="font-semibold text-sm text-[#14243A]">{user.username}</span></div>
                  <div><span className="text-[#67717C] block mb-1">이메일</span><span className="font-semibold text-sm text-[#14243A] break-all">{user.email}</span></div>
                </div>
              )}
              {editAccount && (
                <div className="pt-4 border-t border-[#E1E5E8] space-y-3">
                  <h4 className="text-sm font-semibold text-[#14243A]">정산 계좌 정보 {profile ? '변경' : '등록'}</h4>
                  <EscortProfileEditor key={profile ? 'account-edit' : 'account-create'} section="account" profile={profile} onCancel={() => setEditAccount(false)} onSaved={(next) => { setState((old) => old && { ...old, profile: next }); setEditAccount(false); }} />
                </div>
              )}
              <div className="pt-4 border-t border-[#E1E5E8] space-y-2 text-xs">
                <div className="flex items-center justify-between gap-4">
                  <span className="font-semibold text-[#14243A] block">교육 이수 인증 및 자기소개</span>
                  {!editIntro && profile && <button type="button" onClick={() => { setEditIntro(true); setEditBase(false); setEditAccount(false); }} className="text-xs font-semibold text-[#426B93] hover:text-[#14243A]">자기소개 수정</button>}
                </div>
                {profile?.verified ? (
                  <span className="inline-flex px-3 py-1 rounded-lg bg-[#2F7D5B]/10 text-[#2F7D5B] border border-[#2F7D5B]/20 font-medium">✓ 필수 교육 인증 완료</span>
                ) : <Link href="/mypage/education" className="inline-flex items-center px-3 py-1 rounded-lg bg-[#F7F8F8] text-[#426B93] border border-[#E1E5E8] font-semibold">필수 교육 이수 확인하기</Link>}
                {editIntro && profile ? (
                  <div className="pt-3"><EscortProfileEditor key="intro-edit" section="intro" profile={profile} onCancel={() => setEditIntro(false)} onSaved={(next) => { setState((old) => old && { ...old, profile: next }); setEditIntro(false); }} /></div>
                ) : (
                  <>
                    <p className="pt-2 text-xs text-[#67717C]">자기소개</p>
                    <p className="text-xs leading-5 text-[#14243A] whitespace-pre-wrap">{profile?.intro || '등록된 자기소개가 없습니다.'}</p>
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
