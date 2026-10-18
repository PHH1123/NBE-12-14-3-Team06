'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { CheckCircle2, ChevronLeft } from 'lucide-react';
import { AppShell } from '@/components/layout';
import { useRequireAuth } from '@/features/auth';
import { fetchReport, isReportNotFoundError } from '@/features/report';

type VerifyState = 'loading' | 'confirmed' | 'missing' | 'error';

export default function ReportDonePage() {
  const { loading: authLoading, user } = useRequireAuth('ESCORT');
  const params = useParams<{ applicationId: string }>();
  const applicationId = Number(params.applicationId);
  const [result, setResult] = useState<{ id?: number; state: VerifyState }>({ state: 'loading' });

  useEffect(() => {
    if (authLoading || !user || user.role !== 'ESCORT' || !Number.isInteger(applicationId) || applicationId <= 0) return;
    let ignore = false;
    fetchReport(applicationId)
      .then(() => { if (!ignore) setResult({ id: applicationId, state: 'confirmed' }); })
      .catch((error: unknown) => {
        if (ignore) return;
        setResult({ id: applicationId, state: isReportNotFoundError(error) ? 'missing' : 'error' });
      });
    return () => { ignore = true; };
  }, [applicationId, authLoading, user]);

  if (authLoading || (user && Number.isInteger(applicationId) && applicationId > 0 && (result.id !== applicationId || result.state === 'loading'))) {
    return (
      <AppShell>
        <div className="w-full bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]">보고서 제출 내역을 확인하는 중입니다.</div>
      </AppShell>
    );
  }
  if (!user || user.role !== 'ESCORT') return null;
  if (!Number.isInteger(applicationId) || applicationId <= 0) {
    return <AppShell><div className="w-full bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]">잘못된 동행 번호입니다.</div></AppShell>;
  }

  return (
    <AppShell>
      <div className="w-full bg-[#F7F8F8] py-12 lg:py-16">
        <div className="max-w-[1240px] mx-auto px-5 sm:px-8 lg:px-16">
          <div className="max-w-2xl mx-auto space-y-8">
            <Link href="/mypage/applications" className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] hover:text-[#14243A]">
              <ChevronLeft className="w-4 h-4" />
              <span>신청 목록으로 돌아가기</span>
            </Link>

            <div>
              <span className="text-xs font-semibold text-[#426B93] uppercase tracking-wider block mb-2">Post-Escort Documentation</span>
              <h1 className="text-3xl font-semibold text-[#14243A] tracking-tight">진료 보고서 작성</h1>
            </div>

            {result.state === 'confirmed' ? (
              
              <div className="bg-white rounded-2xl border border-[#E1E5E8] p-8 text-center shadow-xs space-y-6">
                <div className="w-16 h-16 rounded-full bg-[#2F7D5B]/10 text-[#2F7D5B] flex items-center justify-center mx-auto">
                  <CheckCircle2 className="w-8 h-8" />
                </div>
                <div className="space-y-1">
                  <h2 className="text-2xl font-semibold text-[#14243A]">보고서 등록이 완료되었습니다</h2>
                  <p className="text-sm text-[#67717C]">작성하신 진료 보고서가 정상적으로 저장되었습니다.</p>
                </div>
                <div className="pt-2 flex flex-col sm:flex-row gap-3">
                  <Link href={`/escort/${applicationId}/report`} className="flex-1 min-h-[50px] bg-[#14243A] text-white font-semibold text-sm rounded-[10px] hover:bg-[#1f3757] flex items-center justify-center px-4 transition-colors">
                    등록된 진료 보고서 확인
                  </Link>
                  <Link href="/mypage/settlements" className="min-h-[50px] px-6 border border-[#E1E5E8] text-[#14243A] font-semibold text-sm rounded-[10px] hover:bg-[#F7F8F8] flex items-center justify-center transition-colors">
                    내 정산 가기
                  </Link>
                </div>
              </div>
            ) : (
              <div className="bg-white rounded-2xl border border-[#E1E5E8] p-8 text-center shadow-xs space-y-6">
                <h2 className="text-xl font-semibold text-[#14243A]">
                  {result.state === 'missing' ? '아직 제출된 진료 보고서가 없습니다.' : '제출 내역을 확인하지 못했습니다.'}
                </h2>
                <p className="text-sm text-[#67717C]">실제 저장 여부를 확인할 수 없어 제출 완료로 표시하지 않았습니다.</p>
                <Link href={`/escort/${applicationId}`} className="inline-flex h-[50px] px-6 items-center justify-center rounded-[10px] bg-[#14243A] font-semibold text-sm text-white hover:bg-[#1f3757] transition-colors">동행 현황으로 돌아가기</Link>
              </div>
            )}
          </div>
        </div>
      </div>
    </AppShell>
  );
}
