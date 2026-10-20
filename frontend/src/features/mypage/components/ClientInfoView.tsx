'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Activity, ChevronRight, FileText, LogOut, Receipt, User, CheckCircle2 } from 'lucide-react';
import { fetchMyProfile, useAuth, type CurrentUser } from '@/features/auth';
import { fetchMyPosts, type ClientPost } from '@/features/client';
import DropdownSelect from '@/components/ui/DropdownSelect';
import { ApiError } from '@/lib/api';
import { REGIONS } from '@/lib/regions';
import {
  createMyClientProfile,
  fetchMyClientProfile,
  updateMyClientProfile,
  updateMyProfile,
  type ClientProfileWriteRequest,
} from '../api';
import type { ClientProfileDto } from '../types';
import WithdrawAccountButton from './WithdrawAccountButton';

const INPUT = 'w-full h-11 rounded-lg border border-[#E1E5E8] bg-white px-3.5 text-sm text-[#14243A] placeholder:text-[#8B949E] outline-none focus:border-[#426B93] focus:ring-1 focus:ring-[#426B93]';
const FIELD_LABEL = 'mb-1.5 block text-xs font-semibold text-[#14243A]';
const PRIMARY = 'rounded-xl bg-[#14243A] px-5 py-2.5 text-xs font-semibold text-white transition-colors hover:bg-[#426B93] disabled:cursor-not-allowed disabled:opacity-60';
const SECONDARY = 'rounded-xl border border-[#E1E5E8] bg-white px-5 py-2.5 text-xs font-semibold text-[#14243A] transition-colors hover:bg-[#F7F8F8]';
const CARD = 'rounded-2xl border border-[#E1E5E8] bg-white p-6 shadow-xs sm:p-8';

const menuItems = [
  { href: '/client', label: '내 정보', icon: User },
  { href: '/client/posts', label: '작성한 공고', icon: FileText },
  { href: '/client/payments', label: '결제 내역', icon: Receipt },
];

function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.statusCode?.startsWith('404') === true;
}

function ProfileEditor({ user, onSaved, onCancel }: {
  user: CurrentUser;
  onSaved: (next: CurrentUser) => void;
  onCancel: () => void;
}) {
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
      const result = await updateMyProfile({ password, email, name, birthDate, phoneNum, region });
      onSaved(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '기본 정보를 수정하지 못했습니다.');
    } finally {
      setSaving(false);
    }
  }

  const regionOptions = [
    ...(!REGIONS.includes(region as typeof REGIONS[number]) && region ? [{ value: region, label: region }] : []),
    ...REGIONS.map((value) => ({ value, label: value })),
  ];

  return (
    <form onSubmit={submit} className="space-y-4">
      <p className="text-xs text-[#67717C]">아이디와 성별은 변경할 수 없습니다. 수정하려면 현재 비밀번호를 입력해야 합니다.</p>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div><label htmlFor="client-email" className={FIELD_LABEL}>이메일</label><input id="client-email" type="email" autoComplete="email" className={INPUT} value={email} onChange={(event) => setEmail(event.target.value)} required /></div>
        <div><label htmlFor="client-name" className={FIELD_LABEL}>실명</label><input id="client-name" className={INPUT} value={name} onChange={(event) => setName(event.target.value)} required /></div>
        <div><label htmlFor="client-birth" className={FIELD_LABEL}>생년월일</label><input id="client-birth" type="date" className={INPUT} value={birthDate} onChange={(event) => setBirthDate(event.target.value)} required /></div>
        <div><label htmlFor="client-phone" className={FIELD_LABEL}>전화번호</label><input id="client-phone" type="tel" autoComplete="tel" className={INPUT} value={phoneNum} onChange={(event) => setPhoneNum(event.target.value)} required /></div>
        <div><label htmlFor="client-region" className={FIELD_LABEL}>거주 지역</label><DropdownSelect id="client-region" name="region" value={region} onValueChange={setRegion} options={regionOptions} placeholder="지역을 선택해주세요." required size="form" /></div>
        <div><label htmlFor="client-password" className={FIELD_LABEL}>현재 비밀번호</label><input id="client-password" type="password" autoComplete="current-password" className={INPUT} value={password} onChange={(event) => setPassword(event.target.value)} required /></div>
      </div>
      {error && <p role="alert" className="text-xs font-medium text-[#B4413C]">{error}</p>}
      <div className="flex justify-end gap-2"><button type="button" className={SECONDARY} onClick={onCancel}>취소</button><button type="submit" className={PRIMARY} disabled={saving}>{saving ? '저장 중…' : '수정 완료'}</button></div>
    </form>
  );
}

function GuardianEditor({ profile, onSaved, onCancel }: {
  profile: ClientProfileDto | null;
  onSaved: (next: ClientProfileDto) => void;
  onCancel: () => void;
}) {
  const [emergencyContactName, setEmergencyContactName] = useState(profile?.emergencyContactName ?? '');
  const [emergencyContactPhone, setEmergencyContactPhone] = useState(profile?.emergencyContactPhone ?? '');
  const [careNote, setCareNote] = useState(profile?.careNote ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;
    setSaving(true);
    setError('');
    const request: ClientProfileWriteRequest = { emergencyContactName, emergencyContactPhone, careNote };
    try {
      const saved = profile ? await updateMyClientProfile(request) : await createMyClientProfile(request);
      onSaved(saved);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '보호자 정보를 저장하지 못했습니다.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div><label htmlFor="guardian-name" className={FIELD_LABEL}>보호자 실명</label><input id="guardian-name" className={INPUT} maxLength={50} value={emergencyContactName} onChange={(event) => setEmergencyContactName(event.target.value)} required /></div>
        <div><label htmlFor="guardian-phone" className={FIELD_LABEL}>보호자 전화번호</label><input id="guardian-phone" type="tel" className={INPUT} maxLength={20} value={emergencyContactPhone} onChange={(event) => setEmergencyContactPhone(event.target.value)} required /></div>
      </div>
      <div><label htmlFor="care-note" className={FIELD_LABEL}>특이사항</label><textarea id="care-note" className={`${INPUT} min-h-24 resize-y py-3`} maxLength={500} value={careNote} onChange={(event) => setCareNote(event.target.value)} placeholder="거동, 지병 등 동행 시 참고할 내용을 적어주세요." /></div>
      {error && <p role="alert" className="text-xs font-medium text-[#B4413C]">{error}</p>}
      <div className="flex justify-end gap-2"><button type="button" className={SECONDARY} onClick={onCancel}>취소</button><button type="submit" className={PRIMARY} disabled={saving}>{saving ? '저장 중…' : profile ? '수정 완료' : '등록하기'}</button></div>
    </form>
  );
}

function InformationField({ label, children }: { label: string; children: ReactNode }) {
  return <div><dt className="mb-1 text-xs text-[#67717C]">{label}</dt><dd className="break-words text-sm font-semibold text-[#14243A]">{children}</dd></div>;
}

type PageState = {
  user?: CurrentUser;
  profile: ClientProfileDto | null;
  posts: ClientPost[] | null;
  error?: string;
  postsError?: string;
};

export default function ClientInfoView() {
  const router = useRouter();
  const { reload, signOut } = useAuth();
  const [state, setState] = useState<PageState>({ profile: null, posts: null });
  const [editingBase, setEditingBase] = useState(false);
  const [editingGuardian, setEditingGuardian] = useState(false);

  useEffect(() => {
    let canceled = false;
    Promise.all([
      fetchMyProfile(),
      fetchMyClientProfile().catch((error) => { if (isNotFound(error)) return null; throw error; }),
      fetchMyPosts().then((posts) => ({ posts, error: undefined as string | undefined })).catch((error: unknown) => ({ posts: null, error: error instanceof Error ? error.message : '공고 목록을 불러오지 못했습니다.' })),
    ])
      .then(([user, profile, postsResult]) => {
        if (!canceled) setState({ user, profile, posts: postsResult.posts, postsError: postsResult.error });
      })
      .catch((error: Error) => {
        if (!canceled) setState({ profile: null, posts: null, error: error.message });
      });
    return () => { canceled = true; };
  }, []);

  if (!state.user) {
    return (
      <div className="w-full py-12 lg:py-16">
        <div className="mx-auto max-w-[1240px] px-5 sm:px-8 lg:px-16">
          <h1 className="text-3xl font-semibold tracking-tight text-[#14243A]">의뢰인 마이페이지</h1>
          <p role={state.error ? 'alert' : 'status'} className="mt-6 text-sm text-[#67717C]">{state.error ? `프로필을 불러오지 못했습니다. (${state.error})` : '내 정보를 불러오는 중입니다.'}</p>
        </div>
      </div>
    );
  }

  const { user, profile, posts, postsError } = state;
  const stats = posts ? [
    { label: '등록한 공고', count: posts.length, icon: FileText },
    { label: '매칭된 공고', count: posts.filter((post) => post.status === 'matched').length, icon: CheckCircle2 },
    { label: '진행 중', count: posts.filter((post) => post.status === 'inProgress').length, icon: Activity },
  ] : null;

  return (
    <div className="w-full py-12 lg:py-16">
      <div className="mx-auto max-w-[1240px] space-y-8 px-5 sm:px-8 lg:px-16">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight text-[#14243A]">의뢰인 마이페이지</h1>
          <p className="mt-1 text-sm text-[#67717C]">프로필 및 보호자 정보, 작성한 공고와 이용 내역을 관리합니다.</p>
        </div>
        <div className="grid grid-cols-1 items-start gap-8 lg:grid-cols-12">
          <nav aria-label="의뢰인 마이페이지 메뉴" className="space-y-1 rounded-2xl border border-[#E1E5E8] bg-white p-3 shadow-xs lg:col-span-3">
            {menuItems.map((item) => {
              const Icon = item.icon;
              const selected = item.href === '/client';
              return (
                <Link key={item.href} href={item.href} aria-current={selected ? 'page' : undefined} className={`sc-focus flex w-full items-center justify-between rounded-xl px-4 py-3 text-xs font-semibold transition-colors ${selected ? 'bg-[#14243A] text-white shadow-xs' : 'text-[#67717C] hover:bg-[#F7F8F8] hover:text-[#14243A]'}`}>
                  <span className="flex items-center gap-2.5"><Icon aria-hidden="true" className="h-4 w-4" />{item.label}</span>
                  <ChevronRight aria-hidden="true" className={`h-3.5 w-3.5 ${selected ? 'text-white/60' : 'text-[#67717C]'}`} />
                </Link>
              );
            })}
            <button type="button" className="flex w-full items-center justify-between rounded-xl px-4 py-3 text-xs font-semibold text-[#B4413C] transition-colors hover:bg-[#F7F8F8]" onClick={async () => { await signOut(); router.replace('/'); }}>
              <span className="flex items-center gap-2.5"><LogOut aria-hidden="true" className="h-4 w-4" />로그아웃</span><ChevronRight aria-hidden="true" className="h-3.5 w-3.5" />
            </button>
            <div className="my-1 border-t border-[#E1E5E8]" />
            <WithdrawAccountButton name={user.name} />
          </nav>

          <div className="space-y-6 lg:col-span-9">
            <section className={`${CARD} flex flex-wrap items-center justify-between gap-5`}>
              <div className="flex items-center gap-4">
                <div aria-hidden="true" className="flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl bg-[#14243A] text-xl font-bold text-white">{user.name.slice(0, 1)}</div>
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-xl font-bold text-[#14243A]">{user.name}</h2>
                  </div>
                  <p className="mt-1 break-all text-xs text-[#67717C]">{user.email}</p>
                  <p className="mt-1 text-xs text-[#67717C]">{user.region}</p>
                </div>
              </div>
              <div className="border-[#E1E5E8] text-xs sm:border-l sm:pl-6">
                <span className="block text-[11px] text-[#67717C]">등록한 공고</span>
                <span className="mt-1 block text-lg font-bold text-[#14243A]">{posts ? `${posts.length}건` : '조회 불가'}</span>
              </div>
            </section>

            <section className={`${CARD} space-y-6`}>
              <div className="flex items-center justify-between gap-4 border-b border-[#E1E5E8] pb-3">
                <h3 className="text-base font-semibold text-[#14243A]">기본 등록 정보</h3>
                {!editingBase && <button type="button" onClick={() => { setEditingBase(true); setEditingGuardian(false); }} className="text-xs font-semibold text-[#426B93] transition-colors hover:text-[#14243A]">수정하기</button>}
              </div>
              {editingBase ? (
                <ProfileEditor user={user} onCancel={() => setEditingBase(false)} onSaved={(next) => { setState((old) => ({ ...old, user: next })); setEditingBase(false); void reload(); }} />
              ) : (
                <dl className="grid grid-cols-1 gap-6 sm:grid-cols-2">
                  <InformationField label="성명 / 성별">{user.name} ({user.gender === 'FEMALE' ? '여성' : '남성'})</InformationField>
                  <InformationField label="연락처">{user.phoneNum}</InformationField>
                  <InformationField label="아이디">{user.username}</InformationField>
                  <InformationField label="이메일">{user.email}</InformationField>
                  <InformationField label="생년월일">{user.birthDate}</InformationField>
                  <InformationField label="거주 지역">{user.region}</InformationField>
                </dl>
              )}
            </section>

            <section className={`${CARD} space-y-6`}>
              <div className="flex items-center justify-between gap-4 border-b border-[#E1E5E8] pb-3">
                <h3 className="text-base font-semibold text-[#14243A]">보호자 및 추가 정보</h3>
                {!editingGuardian && <button type="button" onClick={() => { setEditingGuardian(true); setEditingBase(false); }} className="text-xs font-semibold text-[#426B93] transition-colors hover:text-[#14243A]">{profile ? '수정하기' : '정보 등록'}</button>}
              </div>
              {editingGuardian ? (
                <GuardianEditor profile={profile} onCancel={() => setEditingGuardian(false)} onSaved={(next) => { setState((old) => ({ ...old, profile: next })); setEditingGuardian(false); }} />
              ) : profile ? (
                <dl className="grid grid-cols-1 gap-6 sm:grid-cols-2">
                  <InformationField label="보호자 실명">{profile.emergencyContactName}</InformationField>
                  <InformationField label="보호자 전화번호">{profile.emergencyContactPhone}</InformationField>
                  <div className="sm:col-span-2"><InformationField label="특이사항"><span className="whitespace-pre-wrap">{profile.careNote || '등록된 특이사항이 없습니다.'}</span></InformationField></div>
                </dl>
              ) : (
                <p className="text-sm text-[#67717C]">등록된 보호자 정보가 없습니다. 동행 시 필요한 보호자 연락처와 참고사항을 등록해 주세요.</p>
              )}
            </section>

            <section className={`${CARD} space-y-5`}>
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#E1E5E8] pb-3">
                <h3 className="text-base font-semibold text-[#14243A]">최근 활동 요약</h3>
                <Link href="/client/posts" className="inline-flex items-center gap-1 text-xs font-semibold text-[#426B93] transition-colors hover:text-[#14243A]">전체 보기<ChevronRight aria-hidden="true" className="h-4 w-4" /></Link>
              </div>
              {stats ? (
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                  {stats.map(({ label, count, icon: Icon }) => (
                    <div key={label} className="rounded-xl border border-[#E1E5E8] bg-[#F7F8F8] p-4">
                      <Icon aria-hidden="true" className="h-4 w-4 text-[#426B93]" />
                      <p className="mt-3 text-xs text-[#67717C]">{label}</p>
                      <p className="mt-1 text-lg font-bold text-[#14243A]">{count}건</p>
                    </div>
                  ))}
                </div>
              ) : (
                <p role="status" className="text-xs text-[#67717C]">공고 활동 내역을 불러오지 못했습니다.{postsError ? ` (${postsError})` : ''}</p>
              )}
            </section>
          </div>
        </div>
      </div>
    </div>
  );
}
