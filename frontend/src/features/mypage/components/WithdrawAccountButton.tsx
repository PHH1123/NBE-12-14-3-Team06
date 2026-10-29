'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { ChevronRight, UserX } from 'lucide-react';
import { useAuth } from '@/features/auth';
import { useActionDialog } from '@/components/ui/useActionDialog';
import { withdrawMyAccount } from '../api';

/** 의뢰인·동행매니저 마이페이지에서 사용하는 본인 회원 탈퇴 동작. */
export default function WithdrawAccountButton({ name }: { name: string }) {
  const router = useRouter();
  const { signOut } = useAuth();
  const { confirm, notice, dialog } = useActionDialog();
  const [busy, setBusy] = useState(false);

  const handleWithdraw = async () => {
    if (busy) return;
    const agreed = await confirm({
      title: '정말 회원 탈퇴하시겠습니까?',
      description: `${name}님의 계정과 프로필을 탈퇴 처리합니다.\n진행 중인 동행·미결제·미정산 건이 있으면 탈퇴할 수 없습니다.`,
      confirmLabel: '회원 탈퇴',
      tone: 'danger',
    });
    if (!agreed) return;
    setBusy(true);
    try {
      await withdrawMyAccount();
      await notice({ title: '회원 탈퇴가 완료되었습니다.', description: '그동안 GAJI를 이용해 주셔서 감사합니다.' });
      // 탈퇴 응답에서 이미 쿠키가 제거된다. 공통 인증 상태도 정리한 뒤 홈으로 이동한다.
      await signOut();
      router.replace('/');
    } catch (error: unknown) {
      await notice({
        title: '회원 탈퇴를 완료하지 못했습니다.',
        description: error instanceof Error ? error.message : '잠시 후 다시 시도해 주세요.',
        tone: 'danger',
      });
    } finally {
      setBusy(false);
    }
  };

  return <>
    {dialog}
    <button
      type="button"
      disabled={busy}
      onClick={handleWithdraw}
      className="flex w-full items-center justify-between rounded-xl px-4 py-3 text-xs font-semibold text-[#B4413C] transition-colors hover:bg-[#B4413C]/5 disabled:cursor-not-allowed disabled:opacity-50"
    >
      <span className="flex items-center gap-2.5"><UserX className="h-4 w-4" aria-hidden="true" />{busy ? '탈퇴 처리 중...' : '회원 탈퇴'}</span>
      <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
    </button>
  </>;
}
