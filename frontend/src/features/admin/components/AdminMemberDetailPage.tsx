'use client';

import { useEffect, useState, type FormEvent } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { ArrowLeft, Award, Clock, Edit2, History, ShieldAlert, UserX, Unlock, X } from 'lucide-react';
import { useRequireAuth } from '@/features/auth';
import { useActionDialog } from '@/components/ui/useActionDialog';
import {
  changeAdminUserStatus, deleteAdminUser, fetchAdminEscortProfile, fetchAdminUser, updateAdminUser,
  type AdminEscortProfileDto, type AdminUserDto, type AdminUserUpdate, type AdminSanctionAction,
} from '../api';
import AdminShell from './AdminShell';
import PageFeedback from '@/components/ui/PageFeedback';
import { AdminRoleBadge, AdminStatusBadge, type AdminMemberRole } from './AdminMemberBadges';

const card = 'rounded-xl border border-[#E1E5E8] bg-white shadow-xs';
const caption = 'mb-1 block text-xs text-[#67717C]';
const inputClass = 'h-10 w-full rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] px-3 text-sm text-[#14243A] outline-none focus:border-[#426B93]';
const actionBtn = 'inline-flex items-center gap-1.5 rounded-lg border px-3.5 py-2 text-xs font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-40';
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; };
function DetailField({ label, value, mono = false }: { label: string; value?: string | number | null; mono?: boolean }) {
  return <div><span className={caption}>{label}</span><span className={`${mono ? 'font-mono' : ''} text-sm font-semibold text-[#14243A]`}>{value === null || value === undefined || value === '' ? '—' : String(value)}</span></div>;
}
const getRole = (role: AdminUserDto['role']): AdminMemberRole => role.toLowerCase() as AdminMemberRole;
const getGender = (gender: AdminUserDto['gender']) => gender === 'MALE' ? '남성' : gender === 'FEMALE' ? '여성' : '—';
const getRoleText = (role: AdminUserDto['role']) => role === 'CLIENT' ? '의뢰인' : role === 'ESCORT' ? '동행 매니저' : '관리자';
const getDate = (date: string | null | undefined) => date?.slice(0, 10) || '—';
const initialForm = (member: AdminUserDto): AdminUserUpdate => ({
  email: member.email, name: member.name, birthDate: member.birthDate ?? '',
  phoneNum: member.phoneNum ?? '', region: member.region ?? '',
});
type Action = AdminSanctionAction | 'delete';
const actionNames: Record<Action,string> = { suspension: '기간 정지', ban: '영구 정지', release: '정지 해제', delete: '회원 탈퇴' };

export default function AdminMemberDetailPage() {
  const { loading, user } = useRequireAuth('ADMIN');
  const params = useParams<{ memberId: string }>();
  const rawId = typeof params.memberId === 'string' ? params.memberId : '';
  const id = Number(rawId);
  const validId = Number.isSafeInteger(id) && id > 0;
  const { confirm, notice, dialog } = useActionDialog();
  const [member, setMember] = useState<AdminUserDto | null>(null);
  const [escort, setEscort] = useState<AdminEscortProfileDto | null>(null);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState('');
  const [feedback, setFeedback] = useState('');
  const [reload, setReload] = useState(0);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<AdminUserUpdate>({email:'',name:'',birthDate:'',phoneNum:'',region:''});
  const [action, setAction] = useState<Action | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (loading || user?.role !== 'ADMIN') return;
    if (!validId) { setFetching(false); setError('올바르지 않은 회원 ID입니다.'); return; }
    const ctrl = new AbortController();
    setFetching(true);
    setError('');
    fetchAdminUser(id, ctrl.signal).then(async (data) => {
      if (ctrl.signal.aborted) return;
      setMember(data);
      setForm(initialForm(data));
      if (data.role === 'ESCORT' && !data.deleted) {
        try { const profile = await fetchAdminEscortProfile(id, ctrl.signal); if (!ctrl.signal.aborted) setEscort(profile); }
        catch { if (!ctrl.signal.aborted) setEscort(null); }
      } else setEscort(null);
    }).catch((e: unknown) => {
      if (!ctrl.signal.aborted) { setMember(null); setError(e instanceof Error ? e.message : '회원 조회에 실패했습니다.'); }
    }).finally(() => { if (!ctrl.signal.aborted) setFetching(false); });
    return () => ctrl.abort();
  }, [loading, user?.role, user?.username, id, validId, reload]);

  const refresh = () => { setEditing(false); setAction(null); setFeedback(''); setReload(v => v + 1); };
  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!member || busy) return;
    if (!form.name.trim() || !form.email.trim() || !form.phoneNum.trim() || !form.region.trim() ||
      !form.birthDate || form.birthDate >= today()) {
      setFeedback('이름·이메일·전화번호·거주 지역을 입력하고 생년월일을 확인해 주세요.'); return;
    }
    const approved = await confirm({ title: '회원 정보를 수정하시겠습니까?', description: `${member.name}님의 기본 정보를 변경합니다.`, confirmLabel: '수정하기' });
    if (!approved) return;
    setBusy(true); setFeedback('');
    try {
      await updateAdminUser(member.id, { ...form, name: form.name.trim(), email: form.email.trim(), phoneNum: form.phoneNum.trim(), region: form.region.trim() });
      setEditing(false); refresh();
      await notice({ title: '회원 정보가 수정되었습니다.' });
    } catch (e: unknown) { setFeedback(e instanceof Error ? e.message : '정보 수정에 실패했습니다.'); }
    finally { setBusy(false); }
  };
  const performAction = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!member || !action || busy) return;
    if (action !== 'delete' && !reason.trim()) { setFeedback('제재 또는 해제 사유를 입력해 주세요.'); return; }
    const approved = await confirm({
      title: `${actionNames[action]} 처리하시겠습니까?`,
      description: action === 'delete' ? `${member.name}님의 계정을 탈퇴 처리합니다.\n진행 중인 동행·결제·정산 상황에 따라 서버에서 거부될 수 있습니다.` :
        `${member.name}님 계정에 ${actionNames[action]} 처리를 적용합니다.\n공고와 지원에 영향이 있을 수 있습니다.`,
      confirmLabel: `${actionNames[action]} 처리`, tone: action === 'ban' || action === 'delete' ? 'danger' : 'default',
    });
    if (!approved) return;
    setBusy(true); setFeedback('');
    try {
      if (action === 'delete') await deleteAdminUser(member.id);
      else await changeAdminUserStatus(member.id, action, reason.trim());
      const completed = actionNames[action];
      setReason(''); setAction(null); refresh();
      await notice({ title: `${completed} 처리가 완료되었습니다.` });
    } catch (e: unknown) { setFeedback(e instanceof Error ? e.message : '처리 중 오류가 발생했습니다.'); }
    finally { setBusy(false); }
  };

  if (loading) return <PageFeedback kind="loading" title="관리자 정보를 확인하는 중입니다." />;
  if (!user || user.role !== 'ADMIN') return null;
  const locked = busy || !!member?.deleted;

  return <AdminShell>{dialog}
    {fetching ? <div role="status" className={`${card} px-6 py-14 text-center text-sm text-[#67717C]`}>회원 정보를 불러오는 중입니다...</div> : !member ? (
      <div className={`${card} px-6 py-14 text-center`}>
        <h1 className="text-xl font-bold text-[#14243A]">회원을 조회할 수 없습니다.</h1>
        <p className="mt-2 text-sm text-[#67717C]">{error || '회원 관리 목록에서 다시 확인해 주세요.'}</p>
        <Link href="/admin/members" className="mt-6 inline-flex items-center gap-2 rounded-lg border border-[#E1E5E8] px-4 py-2 text-sm font-semibold text-[#14243A] hover:bg-[#F7F8F8]"><ArrowLeft className="size-4" /> 회원 목록으로</Link>
      </div>
    ) : <div className="space-y-6">
      <section className={`${card} flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between`}>
        <div className="flex min-w-0 items-start gap-4 sm:items-center">
          <Link href="/admin/members" aria-label="회원 목록으로" className="flex size-9 shrink-0 items-center justify-center rounded-lg border border-[#E1E5E8] text-[#14243A] hover:bg-[#F7F8F8]"><ArrowLeft className="size-5" /></Link>
          <div className="min-w-0"><div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-2xl font-bold tracking-tight text-[#14243A]">{member.name}</h1>
            <span className="font-mono text-sm text-[#67717C]">({member.username})</span>
            <AdminStatusBadge status={member.deleted ? 'withdrawn' : member.userStatus.toLowerCase() as 'active'|'suspended'|'banned'} />
          </div><p className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-[#67717C]">역할: <AdminRoleBadge role={getRole(member.role)}/> · 가입일: {getDate(member.createdAt)}</p></div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" disabled={locked} onClick={() => {setEditing(v=>!v);setAction(null);setFeedback('');setForm(initialForm(member));}} className={`${actionBtn} border-[#E1E5E8] text-[#14243A] hover:bg-[#F7F8F8]`}><Edit2 className="size-3.5 text-[#426B93]"/> 정보 수정</button>
          {member.role !== 'ADMIN' && !member.deleted && <>
            {member.userStatus === 'ACTIVE' && <button type="button" disabled={busy} onClick={()=>{setAction('suspension');setEditing(false);setFeedback('');}} className={`${actionBtn} border-[#9A6B12]/30 bg-[#9A6B12]/10 text-[#9A6B12] hover:bg-[#9A6B12]/20`}><Clock className="size-3.5"/> 기간 정지</button>}
            {member.userStatus !== 'BANNED' && <button type="button" disabled={busy} onClick={()=>{setAction('ban');setEditing(false);setFeedback('');}} className={`${actionBtn} border-[#B4413C]/30 bg-[#B4413C]/10 text-[#B4413C] hover:bg-[#B4413C]/20`}><ShieldAlert className="size-3.5"/> 영구 정지</button>}
            {member.userStatus !== 'ACTIVE' && <button type="button" disabled={busy} onClick={()=>{setAction('release');setEditing(false);setFeedback('');}} className={`${actionBtn} border-[#2F7D5B]/30 bg-[#2F7D5B]/10 text-[#2F7D5B] hover:bg-[#2F7D5B]/20`}><Unlock className="size-3.5"/> 정지 해제</button>}
          </>}
          {!member.deleted && <button type="button" disabled={busy} onClick={()=>{setAction('delete');setEditing(false);setFeedback('');}} className={`${actionBtn} border-[#E1E5E8] text-[#67717C] hover:bg-[#F7F8F8]`}><UserX className="size-3.5"/> 회원 탈퇴</button>}
        </div>
      </section>
      {feedback && <div role="alert" className="rounded-lg border border-[#B4413C]/25 bg-[#B4413C]/5 px-4 py-3 text-sm text-[#B4413C]">{feedback}</div>}
      {editing && !member.deleted && <section className={`${card} p-6`} aria-label="회원 정보 수정">
        <div className="mb-4 flex justify-between"><h2 className="text-base font-bold text-[#14243A]">회원 정보 수정</h2><button aria-label="수정 취소" onClick={()=>setEditing(false)}><X className="size-5 text-[#67717C]" /></button></div>
        <form onSubmit={save} className="grid gap-4 sm:grid-cols-2">
          {([['name','이름','text'],['email','이메일','email'],['birthDate','생년월일','date'],['phoneNum','전화번호','tel'],['region','거주 지역','text']] as const).map(([key,label,type]) => <label key={key} className="space-y-1.5"><span className="text-xs font-semibold text-[#67717C]">{label}</span><input required max={type === 'date' ? today() : undefined} type={type} className={inputClass} value={form[key]} onChange={e=>setForm(v=>({...v,[key]:e.target.value}))}/></label>)}
          <div className="flex items-end gap-2"><button type="submit" disabled={busy} className="h-10 rounded-lg bg-[#14243A] px-5 text-xs font-semibold text-white hover:bg-[#426B93] disabled:opacity-50">변경사항 저장</button><button type="button" onClick={()=>setEditing(false)} className="h-10 rounded-lg border border-[#E1E5E8] px-4 text-xs text-[#67717C]">취소</button></div>
        </form>
      </section>}
      {action && <section className={`${card} p-6`} aria-label="회원 상태 변경">
        <div className="mb-3 flex justify-between"><h2 className="text-base font-bold text-[#14243A]">{actionNames[action]} 처리</h2><button aria-label="처리 취소" onClick={()=>setAction(null)}><X className="size-5 text-[#67717C]"/></button></div>
        <form onSubmit={performAction} className="space-y-4">
          {action !== 'delete' ? <label className="block space-y-2"><span className="text-xs font-semibold text-[#67717C]">처리 사유 (필수)</span><textarea required maxLength={500} value={reason} onChange={e=>setReason(e.target.value)} className="min-h-24 w-full resize-y rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] p-3 text-sm text-[#14243A] focus:border-[#426B93] focus:outline-none" placeholder="처리 사유를 입력해 주세요."/></label> : <p className="text-sm text-[#67717C]">회원 탈퇴는 되돌리기 어렵습니다. 진행 중인 동행·결제·정산이 있으면 서버 정책에 따라 처리되지 않습니다.</p>}
          <button disabled={busy} type="submit" className={`rounded-lg px-5 py-2.5 text-xs font-bold text-white disabled:opacity-50 ${action === 'ban' || action === 'delete' ? 'bg-[#B4413C] hover:bg-[#9A3530]' : 'bg-[#14243A] hover:bg-[#426B93]'}`}>다음: 처리 확인</button>
        </form>
      </section>}
      <section className={`${card} p-6`}><h2 className="mb-4 border-b border-[#E1E5E8] pb-4 text-base font-bold text-[#14243A]">기본 정보</h2>
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          <DetailField label="회원 ID" value={member.id} mono/><DetailField label="아이디" value={member.username} mono/>
          <DetailField label="이름" value={member.name}/><DetailField label="이메일" value={member.email} mono/><DetailField label="전화번호" value={member.phoneNum} mono/>
          <DetailField label="성별" value={getGender(member.gender)}/><DetailField label="생년월일" value={member.birthDate}/>
          <DetailField label="거주 지역" value={member.region}/><DetailField label="회원 가입일" value={getDate(member.createdAt)} mono/>
          <DetailField label="역할" value={getRoleText(member.role)}/><DetailField label="탈퇴일" value={getDate(member.deletedAt)}/>
        </div>
      </section>
      {member.role === 'ESCORT' && !member.deleted && <section className={`${card} p-6`}>
        <h2 className="mb-4 flex items-center gap-2 border-b border-[#E1E5E8] pb-4 text-base font-bold text-[#14243A]"><Award className="size-5 text-[#426B93]"/> 동행 매니저 프로필</h2>
        {escort ? <><div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {([['등급',escort.grade ?? '—'],['동행 완료 건수',escort.completedCount ?? '—'],['평균 평점',escort.averageRating ?? '—'],['인증 여부',escort.verified ? '인증' : '미인증']] as const).map(([label,value])=><div key={label} className="rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] p-4"><span className={caption}>{label}</span><span className="text-xl font-bold text-[#14243A]">{value}</span></div>)}
        </div><div className="mt-4"><span className={caption}>자기소개</span><p className="rounded-lg border border-[#E1E5E8] bg-[#F7F8F8] p-3 text-sm text-[#67717C]">{escort.intro || '등록된 자기소개가 없습니다.'}</p></div></> : <p className="text-sm text-[#67717C]">동행 매니저 프로필을 조회할 수 없습니다.</p>}
      </section>}
      <section className={`${card} overflow-hidden`}><div className="flex items-center justify-between border-b border-[#E1E5E8] px-6 py-4"><h2 className="flex items-center gap-2 text-base font-bold text-[#14243A]"><History className="size-4 text-[#426B93]"/> 현재 제재 정보</h2></div>
        <div className="grid gap-5 p-6 sm:grid-cols-3">
          <DetailField label="현재 상태" value={member.deleted ? '탈퇴' : member.userStatus === 'ACTIVE' ? '활성' : member.userStatus === 'BANNED' ? '영구 정지' : '기간 정지'}/>
          <DetailField label="현재 제재 사유" value={member.statusReason}/><DetailField label="정지 종료 예정일" value={getDate(member.suspendEndAt)}/>
        </div>
      </section>
    </div>}
  </AdminShell>;
}
