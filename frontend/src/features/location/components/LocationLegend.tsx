import { LIVE_MAP_ROLE_STYLE } from '@/lib/kakaoMap';
import type { LocationRole } from '../types';

/**
 * 지도 아래에 두는 범례. 지도 마커와 같은 색·이름을 써서 누가 누구인지 한눈에 알 수 있게 합니다.
 * 위치를 보내 온 사람의 색 점은 채우고, 아직 위치를 모르는 사람은 흐리게 보여줍니다.
 */
export default function LocationLegend({
  me,
  escortKnown,
  clientKnown,
}: {
  me: LocationRole;
  escortKnown: boolean;
  clientKnown: boolean;
}) {
  const items: { role: LocationRole; known: boolean }[] = [
    { role: 'escort', known: escortKnown },
    { role: 'client', known: clientKnown },
  ];

  return (
    <ul className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-[#67717C]" aria-label="지도 마커 안내">
      {items.map(({ role, known }) => (
        <li key={role} className="inline-flex items-center gap-1.5">
          <span
            aria-hidden="true"
            className="h-3 w-3 rounded-full border-2 border-white shadow-sm"
            style={{ background: LIVE_MAP_ROLE_STYLE[role].color, opacity: known ? 1 : 0.35 }}
          />
          <span className={known ? 'font-semibold text-[#14243A]' : ''}>
            {LIVE_MAP_ROLE_STYLE[role].label}
            {role === me ? ' (나)' : ''}
            {known ? '' : ' · 위치 대기 중'}
          </span>
        </li>
      ))}
    </ul>
  );
}
