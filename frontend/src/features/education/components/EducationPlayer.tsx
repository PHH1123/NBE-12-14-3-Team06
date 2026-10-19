'use client';

import Link from 'next/link';
import { useRef } from 'react';
import { CheckCircle2, CircleAlert, Info } from 'lucide-react';
import { formatClock } from '../lib/time';
import { useWatchTracker } from '../lib/useWatchTracker';
import { watchPercent } from '../model/status';
import type { EducationVideoDto } from '../types';

export const RULES = [
  '재생 중 5초마다 시청 기록이 자동 저장되며, 다음에 들어오면 이어서 볼 수 있습니다.',
  '아직 시청하지 않은 구간으로 건너뛰거나 배속으로 시청하면 인정되지 않습니다.',
  '다른 화면(탭)으로 이동하면 영상이 일시정지됩니다.',
];

type Props = {
  video: EducationVideoDto;
  verified: boolean;
  compact?: boolean;
};

/** 실제 영상 및 백엔드 시청 기록을 사용하는 재생 화면입니다. */
export default function EducationPlayer({ video, verified, compact = false }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const tracker = useWatchTracker(videoRef, {
    videoId: video.videoId,
    initialMaxWatchedSec: video.maxWatchedSec,
    initialCompleted: video.completed,
    initialVerified: verified,
  });

  const percent = watchPercent({ ...video, maxWatchedSec: tracker.maxWatchedSec, completed: tracker.completed });
  const justCompleted = tracker.completed && !video.completed;
  const replayOnly = verified && !tracker.completed;

  if (compact) {
    return (
      <div className="space-y-4">
        <div className="aspect-video bg-[#14243A] rounded-xl relative overflow-hidden border border-white/10">
          <video
            ref={videoRef}
            src={video.url}
            controls
            playsInline
            preload="metadata"
            disablePictureInPicture
            controlsList="nodownload noplaybackrate noremoteplayback"
            onContextMenu={(event) => event.preventDefault()}
            className="h-full w-full bg-[#14243A]"
          >
            브라우저가 영상 재생을 지원하지 않습니다.
          </video>
        </div>
        <div className="text-xs text-white/70 leading-relaxed">
          <div className="flex justify-between gap-3">
            <span>{video.title}</span>
            <span className="tabular-nums shrink-0">{percent}%</span>
          </div>
          <div role="progressbar" aria-label="시청 진행률" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100} className="mt-2 h-1.5 bg-white/20 rounded-full overflow-hidden">
            <div className="h-full bg-[#426B93] transition-[width]" style={{ width: `${percent}%` }} />
          </div>
          {tracker.notice && !tracker.completed && !replayOnly && <p role="status" className="mt-2 text-white/80">{tracker.notice}</p>}
          {tracker.error && <p role="alert" className="mt-2 text-[#F6ACA8]">시청 기록을 저장하지 못했습니다. ({tracker.error})</p>}
          {justCompleted && <p role="status" className="mt-2">{tracker.verified ? '교육 이수가 완료되었습니다!' : '영상 시청을 완료했습니다!'}</p>}
        </div>
      </div>
    );
  }

  return (
    <div className="flex w-full max-w-[910px] flex-col gap-6">
      <div className="overflow-hidden rounded-2xl border border-[#E1E5E8] bg-[#171C23] shadow-[0_1px_2px_rgba(20,36,58,0.08)]">
        <video
          ref={videoRef}
          src={video.url}
          controls
          playsInline
          preload="metadata"
          disablePictureInPicture
          controlsList="nodownload noplaybackrate noremoteplayback"
          onContextMenu={(event) => event.preventDefault()}
          className="aspect-video w-full bg-[#14243A]"
        >
          브라우저가 영상 재생을 지원하지 않습니다.
        </video>
      </div>

      <section aria-label="시청 진행 상황" className="rounded-2xl border border-[#E1E5E8] bg-white p-6 shadow-[0_1px_2px_rgba(20,36,58,0.05)]">
        <div className="mb-3 flex items-center justify-between gap-2 text-sm font-semibold text-[#14243A]">
          <span>시청 진행률</span><span className="tabular-nums">{percent}%</span>
        </div>
        <div role="progressbar" aria-label="시청 진행률" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent} className="h-2 w-full overflow-hidden rounded-full bg-[#EDF0F2]">
          <div className={`h-full rounded-full transition-[width] duration-300 ${tracker.completed ? 'bg-[#2F7D5B]' : 'bg-[#426B93]'}`} style={{ width: `${percent}%` }} />
        </div>
        <p className="mt-3 text-xs font-medium leading-5 text-[#67717C]">
          {tracker.completed
            ? '시청을 완료한 영상입니다. 원하는 구간을 자유롭게 다시 볼 수 있습니다.'
            : replayOnly
              ? '교육 이수를 완료해 시청 기록이 더 저장되지 않습니다. 원하는 구간을 자유롭게 볼 수 있습니다.'
              : `인정된 시청 위치 ${formatClock(tracker.maxWatchedSec)} / ${formatClock(video.durationSec)}`}
        </p>
        {tracker.notice && !tracker.completed && !replayOnly && (
          <p role="status" className="mt-3 flex items-start gap-2 rounded-lg bg-[#EDF0F2] px-4 py-3 text-xs leading-5 text-[#14243A]">
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-[#426B93]" aria-hidden="true" />{tracker.notice}
          </p>
        )}
        {tracker.error && (
          <p role="alert" className="mt-3 flex items-start gap-2 rounded-lg bg-[#F7ECEB] px-4 py-3 text-xs leading-5 text-[#B4413C]">
            <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />시청 기록을 저장하지 못했습니다. ({tracker.error})
          </p>
        )}
      </section>

      {justCompleted && (
        <section role="status" className="flex flex-col gap-4 rounded-2xl border border-[#2F7D5B]/30 bg-[#2F7D5B]/10 p-6 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#2F7D5B] text-white">
              <CheckCircle2 className="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <h2 className="text-base font-bold text-[#2F7D5B]">
                {tracker.verified ? '교육 이수가 완료되었습니다!' : '영상 시청을 완료했습니다!'}
              </h2>
              <p className="mt-1 text-xs leading-5 text-[#202832]">
                {tracker.verified ? '이제 공고에 지원할 수 있습니다.' : '남은 필수 영상을 모두 시청하면 교육 이수가 완료됩니다.'}
              </p>
            </div>
          </div>
          <Link href={tracker.verified ? '/escort/posts' : '/mypage/education'} className="inline-flex h-[40px] shrink-0 items-center justify-center rounded-xl bg-[#2F7D5B] px-5 text-xs font-semibold text-white transition-colors hover:bg-[#256348]">
            {tracker.verified ? '공고 찾아보기' : '교육 영상 목록'}
          </Link>
        </section>
      )}

      <section aria-label="시청 안내" className="rounded-2xl border border-[#E1E5E8] bg-white p-6">
        <h2 className="mb-3 text-base font-semibold text-[#14243A]">시청 안내</h2>
        <ul className="flex list-disc flex-col gap-1.5 pl-5 text-xs leading-5 text-[#67717C]">
          {RULES.map((rule) => <li key={rule}>{rule}</li>)}
        </ul>
      </section>
    </div>
  );
}
