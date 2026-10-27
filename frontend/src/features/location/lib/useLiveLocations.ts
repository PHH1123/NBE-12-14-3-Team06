'use client';

import { useEffect, useState } from 'react';
import { fetchLocation } from '../api';
import { toLiveLocations } from '../model/mapper';
import type { LatLng, LiveLocations } from '../types';

/** 서로의 위치를 서버에 물어보는 간격(밀리초) */
const POLL_INTERVAL_MS = 5000;
/** 지나온 경로 선에 남겨 둘 점의 최대 개수 (오래 열어 둬도 메모리가 늘지 않게) */
const MAX_TRAIL_POINTS = 300;

type PollState = {
  /** 이 상태가 어느 지원(동행)의 것인지. 다른 동행으로 옮겨가면 이전 값을 쓰지 않습니다. */
  key?: number;
  locations?: LiveLocations;
  /** 이 화면을 열어 둔 동안 받은 동행 매니저의 위치를 이은 경로 */
  trail: LatLng[];
  error?: string;
};

const EMPTY_TRAIL: LatLng[] = [];

/**
 * 동행 중인 의뢰인과 동행 매니저의 최근 위치, 그리고 두 사람이 가까운지(nearby)를 일정 간격으로 물어봅니다. (폴링)
 * 의뢰인 화면과 동행 매니저 화면이 같이 씁니다.
 *
 * enabled 가 true 인 동안(동행 중)에만 요청합니다. 화면이 가려져 있는 동안은 요청을 건너뛰고,
 * 다시 보이는 순간 바로 한 번 물어봅니다.
 */
export function useLiveLocations(applicationId: number, enabled: boolean) {
  const [state, setState] = useState<PollState>({ trail: EMPTY_TRAIL });

  useEffect(() => {
    if (!enabled || !Number.isFinite(applicationId)) return;

    let ignore = false;
    let inFlight = false;

    const poll = () => {
      // 이전 요청이 아직 안 끝났거나, 화면이 가려져 있으면 이번 차례는 건너뜁니다.
      if (inFlight || document.visibilityState === 'hidden') return;
      inFlight = true;

      fetchLocation(applicationId)
        .then((dto) => {
          if (ignore) return;
          setState((prev) => {
            const base: PollState = prev.key === applicationId ? prev : { key: applicationId, trail: EMPTY_TRAIL };
            // 공유 중인 동행이 아닙니다(404). 이전 값은 그대로 두고 오류만 지웁니다.
            if (!dto) return { ...base, error: undefined };

            const locations = toLiveLocations(dto);
            const escortPosition = locations.escort?.position;
            const last = base.trail[base.trail.length - 1];
            const moved = !!escortPosition && (!last || last.lat !== escortPosition.lat || last.lng !== escortPosition.lng);
            const trail = moved ? [...base.trail, escortPosition].slice(-MAX_TRAIL_POINTS) : base.trail;
            return { key: applicationId, locations, trail, error: undefined };
          });
        })
        .catch((error: unknown) => {
          if (ignore) return;
          const message = error instanceof Error ? error.message : '위치를 불러오지 못했습니다.';
          setState((prev) => ({
            ...(prev.key === applicationId ? prev : { trail: EMPTY_TRAIL }),
            key: applicationId,
            error: message,
          }));
        })
        .finally(() => {
          inFlight = false;
        });
    };

    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') poll();
    };

    poll();
    const timer = window.setInterval(poll, POLL_INTERVAL_MS);
    document.addEventListener('visibilitychange', onVisibilityChange);

    return () => {
      ignore = true;
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [applicationId, enabled]);

  const active = enabled && state.key === applicationId;
  const locations = active ? state.locations : undefined;
  return {
    escort: locations?.escort,
    client: locations?.client,
    /** 두 사람이 가까운지. 서버가 계산한 값이며, 위치를 모르거나 오래됐으면 false */
    nearby: locations?.nearby ?? false,
    distanceMeters: locations?.distanceMeters,
    trail: active ? state.trail : EMPTY_TRAIL,
    error: active ? state.error : undefined,
  };
}
