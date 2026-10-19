import type { RideSelect } from '@/features/ride';
import type { PostFormValues } from '../types';

/** 공고 작성 폼의 선택지와 계산 도우미입니다. */

/** 00:00 ~ 23:30, 30분 간격 */
export const TIME_OPTIONS: string[] = Array.from({ length: 48 }, (_, index) => {
  const hour = String(Math.floor(index / 2)).padStart(2, '0');
  return `${hour}:${index % 2 ? '30' : '00'}`;
});

/**
 * 이동수단 선택지. 백엔드 RideSelect 와 1:1 로 맞춰 둡니다 (여기에 없는 값을 보내면 백엔드가 400 을 냅니다).
 * 화면에는 한글 라벨을 보여주고, 서버로 보낼 땐 toRideSelect 로 ENUM 이름을 꺼냅니다.
 */
const RIDE_SELECT_BY_LABEL: Record<string, RideSelect> = {
  도보: 'WALK',
  대중교통: 'BUS',
  택시: 'TAXI',
  자가용: 'OWN_CAR',
};

export const TRANSPORT_OPTIONS = Object.keys(RIDE_SELECT_BY_LABEL);

/** "택시" → "TAXI". 선택지에 없는 값이면 undefined (폼에서 required 로 막고 있습니다) */
export function toRideSelect(label: string): RideSelect | undefined {
  return RIDE_SELECT_BY_LABEL[label];
}

/** "TAXI" → "택시" (수정 폼에 기존 이동수단을 채울 때 씁니다). 없으면(null) 빈 문자열 — 다시 선택해야 합니다. */
export function toRideLabel(select: RideSelect | null | undefined): string {
  if (!select) return '';
  return TRANSPORT_OPTIONS.find((label) => RIDE_SELECT_BY_LABEL[label] === select) ?? '';
}
export const PARTY_OPTIONS = ['1명 (본인만)', '2명 (보호자 동반)', '3명 이상'];

export const EMPTY_FORM: PostFormValues = {
  title: '',
  hospitalName: '',
  hospitalAddress: '',
  hospitalLat: null,
  hospitalLng: null,
  region: '',
  district: '',
  departure: '',
  pickupLat: null,
  pickupLng: null,
  date: '',
  startTime: '',
  endTime: '',
  hourlyPay: '',
  transportOut: '',
  transportBack: '',
  party: '',
  reportRequested: false,
  description: '',
  note: '',
  recruitStartDate: '',
  recruitStartTime: '',
  recruitEndDate: '',
  recruitEndTime: '',
};

function toMinutes(time: string): number {
  const [hour, minute] = time.split(':').map(Number);
  return hour * 60 + minute;
}

/** 시작~종료 시각 사이의 분. 종료가 시작보다 늦지 않으면 0 */
export function diffMinutes(start: string, end: string): number {
  if (!start || !end) return 0;
  return Math.max(0, toMinutes(end) - toMinutes(start));
}

/** 90 → "1시간 30분" */
export function formatMinutes(minutes: number): string {
  if (minutes <= 0) return '';
  const hour = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return [hour ? `${hour}시간` : '', rest ? `${rest}분` : ''].filter(Boolean).join(' ');
}

/** 시급 × 시간 (원, 정수). 시급을 알 수 없으면 0 */
export function estimateAmount(hourlyPay: string, minutes: number): number {
  const pay = Number(hourlyPay.replace(/[^0-9]/g, ''));
  return Math.round((pay * minutes) / 60);
}

/** 숫자만 남겨서 정수로 바꿉니다. "14,000" → 14000 */
export function parsePay(hourlyPay: string): number {
  return Number(hourlyPay.replace(/[^0-9]/g, '')) || 0;
}

/** 등록 가능한 최소 시급. 2026년 최저시급(10,320원) × 플랫폼 수수료 10% 포함 (백엔드 PostWriteRequest.hourlyPay 의 @Min 과 같은 값이어야 합니다) */
export const MIN_HOURLY_PAY = 11352;

/** "2026-09-20" + "10:00" → "2026-09-20T10:00:00" (백엔드 LocalDateTime 포맷) */
export function toIsoDateTime(date: string, time: string): string {
  return `${date}T${time}:00`;
}

/** 오늘 날짜를 <input type="date"> 가 먹는 "YYYY-MM-DD" 로 (로컬 기준) */
export function todayDateString(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** 지금 시각을 TIME_OPTIONS(30분 간격) 형식에 맞춰 30분 단위로 내림한 "HH:mm" */
export function flooredNowTime(): string {
  const now = new Date();
  const hour = String(now.getHours()).padStart(2, '0');
  const minute = now.getMinutes() < 30 ? '00' : '30';
  return `${hour}:${minute}`;
}

/**
 * 선택한 날짜(date)가 오늘이면 "지금 이후"만 고를 수 있도록 막아야 하는 기준 시각을 돌려줍니다.
 * 이 시각 이하(<=)인 TIME_OPTIONS 는 선택할 수 없게 막으면 됩니다.
 * 오늘보다 미래 날짜면 시간 제한이 없어 빈 문자열, 과거 날짜면(날짜 자체를 min 으로 막지만 방어적으로) 전부 막습니다.
 */
export function minSelectableTime(date: string): string {
  const today = todayDateString();
  if (!date || date > today) return '';
  if (date < today) return '23:30';
  return flooredNowTime();
}

/** "2026-09-20" + "10:00" → "2026-09-20T10:00" (비교용 문자열. 날짜·시간 중 하나라도 없으면 빈 문자열) */
export function combineDateTime(date: string, time: string): string {
  return date && time ? `${date}T${time}` : '';
}

/**
 * date 가 boundDate 와 같은 날일 때, boundTime 이하(<=)인 TIME_OPTIONS 를 막습니다 (= boundTime 이후만 선택 가능).
 * 날짜가 다르거나 기준값이 없으면 그 시간만으로는 제한이 없어 빈 배열을 돌려줍니다.
 */
export function disabledTimesAtOrBefore(date: string, boundDate: string, boundTime: string): string[] {
  if (!date || !boundDate || !boundTime || date !== boundDate) return [];
  return TIME_OPTIONS.filter((option) => option <= boundTime);
}

/**
 * date 가 boundDate 와 같은 날일 때, boundTime 이상(>=)인 TIME_OPTIONS 를 막습니다 (= boundTime 이전만 선택 가능).
 */
export function disabledTimesAtOrAfter(date: string, boundDate: string, boundTime: string): string[] {
  if (!date || !boundDate || !boundTime || date !== boundDate) return [];
  return TIME_OPTIONS.filter((option) => option >= boundTime);
}

/**
 * 카카오맵이 돌려준 주소("서울 강남구 역삼동 736-1")에서 시/도 · 구/군을 뽑습니다.
 * 카카오 주소는 백엔드가 실제로 쓰는 짧은 표기("서울" · "경기" 등)와 형식이 같습니다.
 */
export function splitRegion(address: string): { region: string; district: string } {
  const [region = '', district = ''] = address.trim().split(/\s+/);
  return { region, district };
}
