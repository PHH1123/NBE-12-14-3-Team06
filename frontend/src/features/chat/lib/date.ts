const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

/**
 * 백엔드 날짜 문자열("2026-09-22T12:30:00", 초 뒤 마이크로초가 붙기도 함)을 Date 로 바꿉니다.
 * 시간대 표시가 없는 문자열은 이 브라우저의 현지 시간(한국)으로 읽습니다.
 */
function parseDateTime(value: string): Date {
  return new Date(value.replace(/(\.\d{3})\d+/, '$1'));
}

/** 말풍선 옆 시각 — "오전 9:00" · "오후 12:30" */
export function formatMessageTime(value: string): string {
  const date = parseDateTime(value);
  const hour = date.getHours();
  const minute = String(date.getMinutes()).padStart(2, '0');
  return `${hour < 12 ? '오전' : '오후'} ${hour % 12 || 12}:${minute}`;
}

/** 메시지 사이 날짜 구분선 — "2026년 9월 22일 (화)" */
export function formatMessageDate(value: string): string {
  const date = parseDateTime(value);
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일 (${WEEKDAYS[date.getDay()]})`;
}

/** 두 메시지 사이에 날짜 구분선을 넣어야 하는지 (날짜가 바뀌었으면 true) */
export function isNewDay(current: string, previous: string | undefined): boolean {
  if (!previous) return true;
  return parseDateTime(current).toDateString() !== parseDateTime(previous).toDateString();
}

/**
 * 방 목록의 오른쪽 위 시각 — 가까울수록 짧게 보여 줍니다.
 * "방금" · "3분 전" · "오후 2:10" · "어제" · "9월 22일"
 */
export function formatRoomTime(value: string | null): string {
  if (!value) return '';

  const date = parseDateTime(value);
  const minutes = Math.floor((Date.now() - date.getTime()) / 60_000);

  if (minutes < 1) return '방금';
  if (minutes < 60) return `${minutes}분 전`;

  const today = new Date();
  if (date.toDateString() === today.toDateString()) return formatMessageTime(value);

  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) return '어제';

  return `${date.getMonth() + 1}월 ${date.getDate()}일`;
}
