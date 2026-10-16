/**
 * 백엔드 날짜 문자열("2026-09-22T12:30:00", 초 뒤 마이크로초가 붙기도 함)을 Date 로 바꿉니다.
 * 시간대 표시가 없는 문자열은 이 브라우저의 현지 시간(한국)으로 읽습니다.
 */
function parseDateTime(value: string): Date {
  return new Date(value.replace(/(\.\d{3})\d+/, '$1'));
}

/**
 * 알림 목록의 시각 — 가까울수록 짧게 보여 줍니다.
 * "방금" · "3분 전" · "5시간 전" · "어제" · "9월 22일"
 */
export function formatNotificationTime(value: string | null): string {
  if (!value) return '';

  const date = parseDateTime(value);
  const minutes = Math.floor((Date.now() - date.getTime()) / 60_000);

  if (minutes < 1) return '방금';
  if (minutes < 60) return `${minutes}분 전`;

  const today = new Date();
  if (date.toDateString() === today.toDateString()) return `${Math.floor(minutes / 60)}시간 전`;

  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) return '어제';

  return `${date.getMonth() + 1}월 ${date.getDate()}일`;
}
