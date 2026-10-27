import type { LiveLocations, LiveLocationsDto, LocationPointDto, LocationView } from '../types';

/** 시각을 "오후 3:25:10" 처럼 보여줍니다. 읽을 수 없는 값이면 빈 문자열. */
export function formatClock(value: Date | string): string {
  const date = typeof value === 'string' ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit', second: '2-digit' });
}

/** 서버가 준 한 사람의 위치(LocationPointDto)를 화면용(LocationView)으로 바꿉니다. */
export function toLocationView(dto: LocationPointDto): LocationView {
  return {
    position: { lat: dto.lat, lng: dto.lng },
    updatedAtLabel: formatClock(dto.updatedAt),
  };
}

/** 서버가 준 두 사람의 위치(LiveLocationsDto)를 화면용(LiveLocations)으로 바꿉니다. */
export function toLiveLocations(dto: LiveLocationsDto): LiveLocations {
  return {
    escort: dto.escort ? toLocationView(dto.escort) : undefined,
    client: dto.client ? toLocationView(dto.client) : undefined,
    nearby: dto.nearby,
    distanceMeters: dto.distanceMeters ?? undefined,
  };
}
