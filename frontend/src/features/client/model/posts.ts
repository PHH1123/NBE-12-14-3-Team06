import { toPostStatusKey, type PostSummary } from '@/features/post';
import { daysFromNow, formatMonthDay } from '../lib/date';
import type { ClientPost, ClientPostStatus } from '../types';

/**
 * 백엔드 PostSummary(내 공고 목록에서 걸러낸 것) → 카드용 ClientPost.
 * accepted 는 매칭 완료 이후 상태에서만 넘어옵니다(모집 중인 공고는 지원자 조회를 하지 않으므로 undefined).
 * unpaid 는 이 공고에 남은 READY 결제(PaymentDto)입니다. 결제할 게 없으면 undefined 입니다.
 */
export function toClientPost(
  summary: PostSummary,
  accepted?: { applicationId: number; escortName: string },
  unpaid?: { id: number; amount: number },
): ClientPost {
  return {
    id: summary.id,
    title: summary.title,
    hospitalName: summary.hospitalName,
    location: `${summary.region} ${summary.district}`,
    dateLabel: formatMonthDay(daysFromNow(summary.startsInDays)),
    timeLabel: summary.startTime,
    durationLabel: `약 ${summary.hours}시간`,
    payLabel: `시급 ${summary.hourlyPay.toLocaleString()}원`,
    description: summary.description,
    status: toPostStatusKey(summary.postStatus),
    managerName: accepted?.escortName,
    applicationId: accepted?.applicationId,
    // 시급은 결제 화면이 쓰는 pay 값입니다. 결제 건에는 없어서(hourlyPaySnapshot 은 결제 당시 값) 공고에서 가져옵니다.
    unpaid: unpaid ? { paymentId: unpaid.id, amount: unpaid.amount, hourlyPay: summary.hourlyPay } : undefined,
  };
}

export const SORT_OPTIONS = [
  { value: 'latest', label: '최신 순' },
  { value: 'oldest', label: '오래된 순' },
] as const;

export type ClientPostSort = (typeof SORT_OPTIONS)[number]['value'];

/** 탭 · 검색어 · 정렬을 적용한 목록 */
export function filterClientPosts(
  posts: ClientPost[],
  status: 'all' | ClientPostStatus,
  keyword: string,
  sort: ClientPostSort,
): ClientPost[] {
  const word = keyword.trim();
  const result = posts.filter(
    (post) =>
      (status === 'all' || post.status === status) &&
      (!word || [post.title, post.hospitalName, post.location].some((text) => text.includes(word))),
  );
  return sort === 'latest' ? result : result.reverse();
}