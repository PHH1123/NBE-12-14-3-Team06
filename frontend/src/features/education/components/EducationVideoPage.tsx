'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { ChevronLeft, Clock, Video } from 'lucide-react';
import { AppShell } from '@/components/layout';
import { useRequireAuth } from '@/features/auth';
import { fetchMyEscortProfile } from '@/features/mypage/api';
import { fetchEducationVideo } from '../api';
import { formatDuration } from '../lib/time';
import type { EducationVideoDto } from '../types';
import EducationPlayer from './EducationPlayer';

export default function EducationVideoPage() {
  const { loading: authLoading, user } = useRequireAuth('ESCORT');
  const params = useParams<{ videoId: string }>();
  const videoId = Number(params.videoId);
  const [result, setResult] = useState<{ videoId: number; video?: EducationVideoDto; verified?: boolean; error?: string }>();

  useEffect(() => {
    if (authLoading || user?.role !== 'ESCORT' || !Number.isInteger(videoId) || videoId <= 0) return;
    let canceled = false;
    Promise.all([fetchEducationVideo(videoId), fetchMyEscortProfile()])
      .then(([video, profile]) => {
        if (!canceled) setResult({ videoId, video, verified: profile.verified });
      })
      .catch((error: unknown) => {
        if (!canceled) setResult({ videoId, error: error instanceof Error ? error.message : '교육 영상을 불러오지 못했습니다.' });
      });
    return () => { canceled = true; };
  }, [authLoading, user?.role, user?.username, videoId]);

  if (authLoading) {
    return <AppShell><section className="min-h-[55vh] bg-[#F7F8F8] py-24 text-center text-sm font-medium text-[#67717C]">영상을 불러오는 중입니다.</section></AppShell>;
  }
  if (!user) return null;

  const current = result?.videoId === videoId ? result : undefined;
  const invalidId = !Number.isInteger(videoId) || videoId <= 0;
  const video = current?.video;

  return (
    <AppShell>
      <div className="min-h-[75vh] bg-[#F7F8F8] py-12 lg:py-16">
        <div className="mx-auto max-w-[1240px] space-y-8 px-5 sm:px-8 lg:px-16">
          <Link href="/mypage/education" className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] transition-colors hover:text-[#14243A]">
            <ChevronLeft className="h-4 w-4" aria-hidden="true" /> 교육 영상 목록으로 돌아가기
          </Link>

          <div>
            <span className="mb-1 block text-xs font-semibold uppercase tracking-wider text-[#426B93]">Mandatory Manager Training</span>
            {video ? (
              <>
                <div className="flex flex-wrap items-center gap-2">
                  <span className={`rounded px-2 py-0.5 text-xs font-semibold ${video.required ? 'bg-[#B4413C]/10 text-[#B4413C]' : 'bg-[#EDF0F2] text-[#67717C]'}`}>
                    {video.required ? '필수' : '선택'}
                  </span>
                  <span className="inline-flex items-center gap-1 text-xs font-medium text-[#67717C]"><Clock className="h-3.5 w-3.5" aria-hidden="true" /> {formatDuration(video.durationSec)}</span>
                </div>
                <h1 className="mt-3 text-3xl font-semibold tracking-tight text-[#14243A]">{video.title}</h1>
              </>
            ) : (
              <h1 className="text-3xl font-semibold tracking-tight text-[#14243A]">교육 영상 시청</h1>
            )}
          </div>

          {video ? (
            <EducationPlayer key={video.videoId} video={video} verified={current?.verified ?? false} />
          ) : (
            <div className="rounded-2xl border border-[#E1E5E8] bg-white px-6 py-12 text-center text-sm text-[#67717C]" role={invalidId || current?.error ? 'alert' : 'status'}>
              <Video className="mx-auto mb-3 h-7 w-7 text-[#426B93]" aria-hidden="true" />
              {invalidId ? '존재하지 않는 교육 영상입니다.' : current?.error ? `영상을 불러오지 못했습니다. (${current.error})` : '영상을 불러오는 중입니다.'}
            </div>
          )}
        </div>
      </div>
    </AppShell>
  );
}
