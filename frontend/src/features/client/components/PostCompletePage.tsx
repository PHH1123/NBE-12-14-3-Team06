'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { Check, ArrowRight, List, LoaderCircle } from 'lucide-react';
import { AppShell } from '@/components/layout';
import { useRequireAuth } from '@/features/auth';
import { fetchPayment, type PaymentDto } from '@/features/payment';
import { fetchPostRaw, type PostDto } from '@/features/post';

type Result = { post: PostDto; payment: PaymentDto };

/** 승인된 결제와 등록된 공고를 서버에서 재조회한 뒤 실제 결과만 표시합니다. */
export default function PostCompletePage() {
  const { loading: authLoading, user } = useRequireAuth('CLIENT');
  const searchParams = useSearchParams();
  const postId = Number(searchParams.get('postId'));
  const paymentId = Number(searchParams.get('paymentId'));
  const validIds = Number.isSafeInteger(postId) && postId > 0 && Number.isSafeInteger(paymentId) && paymentId > 0;
  const [result, setResult] = useState<Result | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (authLoading || !user) return;
    if (!validIds) {
      setError('공고 또는 결제 번호를 확인할 수 없습니다.');
      return;
    }
    let active = true;
    setResult(null);
    setError(null);
    Promise.all([fetchPostRaw(postId), fetchPayment(paymentId)])
      .then(([post, payment]) => {
        if (!active) return;
        if (payment.postId !== post.id) {
          setError('공고와 결제 정보가 일치하지 않습니다.');
          return;
        }
        if (payment.paymentStatus !== 'DONE' && payment.paymentStatus !== 'PARTIAL_CANCELED') {
          setError('아직 결제가 완료되지 않았습니다. 결제 상태를 확인해 주세요.');
          return;
        }
        setResult({ post, payment });
      })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason.message : '등록 결과를 불러오지 못했습니다.');
      });
    return () => { active = false; };
  }, [authLoading, paymentId, postId, user?.username, validIds]);

  if (authLoading || !user) {
    return (
      <AppShell>
        <section className="min-h-[500px] bg-[#F5F6F8] px-5 py-20 text-center text-[#657184]">{authLoading ? '계정을 확인하고 있습니다.' : '로그인이 필요합니다.'}</section>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <section className="min-h-[calc(100vh-90px)] bg-[#F5F6F8] px-5 py-14 text-[#1E2739] sm:py-20">
        <div className="mx-auto w-full max-w-[640px]">
          <div className="rounded-[18px] border border-[#E0E4E9] bg-white px-6 py-10 text-center shadow-[0_2px_3px_rgba(30,39,57,0.04)] sm:px-10 sm:py-11">
            {result ? (
              <>
                <div className="mx-auto flex h-[68px] w-[68px] items-center justify-center rounded-full bg-[#E8EFF7]">
                  <Check size={34} strokeWidth={2.6} className="text-[#55749B]" />
                </div>
                <h1 className="mt-5 text-[26px] font-bold tracking-[-0.035em] sm:text-[28px]">공고 등록이 완료되었습니다</h1>
                <p className="mt-2 text-[14px] text-[#657184]">등록한 공고는 내 공고 목록에서 확인할 수 있습니다.</p>
                <div className="mt-7 rounded-[12px] border border-[#E3E7EC] bg-[#FAFBFC] px-5 py-5 text-left sm:px-6">
                  <p className="mb-4 break-words border-b border-[#E3E7EC] pb-4 text-[15px] font-semibold">{result.post.title}</p>
                  <dl className="space-y-3 text-[14px]">
                    <div className="flex items-center justify-between gap-4"><dt className="text-[#657184]">공고 번호</dt><dd className="font-medium">{result.post.id}</dd></div>
                    <div className="flex items-center justify-between gap-4"><dt className="text-[#657184]">공고 상태</dt><dd className="font-semibold text-[#55749B]">{result.post.postStatus}</dd></div>
                    <div className="flex items-center justify-between gap-4"><dt className="text-[#657184]">시간당 시급</dt><dd className="font-medium tabular-nums">{result.payment.hourlyPaySnapshot.toLocaleString('ko-KR')}원</dd></div>
                    <div className="flex items-center justify-between gap-4 border-t border-[#E3E7EC] pt-4"><dt className="font-semibold text-[#344055]">결제 금액</dt><dd className="text-[20px] font-bold tabular-nums">{result.payment.amount.toLocaleString('ko-KR')}원</dd></div>
                  </dl>
                </div>
                <div className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <Link href="/client/posts" className="flex h-[46px] items-center justify-center gap-2 rounded-[12px] border border-[#DDE2E8] bg-white text-[14px] font-semibold text-[#344055] transition-colors hover:bg-[#F5F6F8]"><List size={17} />내 공고 목록</Link>
                  <Link href={`/client/posts/${result.post.id}`} className="flex h-[46px] items-center justify-center gap-2 rounded-[12px] bg-[#1E2739] text-[14px] font-semibold text-white transition-colors hover:bg-[#344055]">공고 상세 보기<ArrowRight size={17} /></Link>
                </div>
              </>
            ) : error ? (
              <>
                <h1 className="text-[26px] font-bold">등록 결과를 확인할 수 없습니다</h1>
                <p className="mt-4 text-[15px] text-[#657184]">{error}</p>
                <Link href="/client/posts" className="mt-8 inline-flex h-[50px] items-center justify-center rounded-[12px] bg-[#1E2739] px-8 text-[14px] font-semibold text-white transition-colors hover:bg-[#344055]">내 공고 목록으로</Link>
              </>
            ) : (
              <div className="flex flex-col items-center gap-4 py-10 text-[#657184]"><LoaderCircle size={36} className="animate-spin text-[#55749B]" />공고 및 결제 내역을 확인하고 있습니다.</div>
            )}
          </div>
        </div>
      </section>
    </AppShell>
  );
}
