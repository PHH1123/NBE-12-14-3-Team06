/**
 * 카카오맵 JavaScript SDK 로더 + 장소 검색.
 *
 * 공고 작성 폼에서 병원·출발지를 검색해 정확한 주소와 위도·경도를 받기 위해 씁니다.
 * 키는 .env.local 의 NEXT_PUBLIC_KAKAO_MAP_KEY (브라우저에 노출되는 값이라 NEXT_PUBLIC_ 접두사가 필요합니다).
 */

/** SDK 가 실제로 쓰는 부분만 최소로 적은 타입입니다. (공식 타입 패키지를 새로 설치하지 않았습니다) */
type KakaoPlaceResult = {
  place_name: string;
  /** 지번 주소 */
  address_name: string;
  /** 도로명 주소 (없을 수 있음) */
  road_address_name: string;
  /** 경도(문자열로 내려옵니다) */
  x: string;
  /** 위도(문자열로 내려옵니다) */
  y: string;
};

type KakaoPlacesSearchStatus = 'OK' | 'ZERO_RESULT' | 'ERROR';

/** kakao.maps.LatLng 인스턴스. 안쪽 구조는 쓰지 않아 불투명하게 둡니다. */
type KakaoLatLng = object;

/** kakao.maps.LatLngBounds 인스턴스. extend 로 점을 넣으면 그 점들을 모두 감싸는 범위가 됩니다. */
type KakaoBoundsInstance = {
  extend: (latlng: KakaoLatLng) => void;
};

type KakaoMapInstance = {
  setCenter: (latlng: KakaoLatLng) => void;
  panTo: (latlng: KakaoLatLng) => void;
  setBounds: (
    bounds: KakaoBoundsInstance,
    paddingTop?: number,
    paddingRight?: number,
    paddingBottom?: number,
    paddingLeft?: number,
  ) => void;
  relayout: () => void;
};

type KakaoCustomOverlayInstance = {
  setPosition: (latlng: KakaoLatLng) => void;
  setMap: (map: KakaoMapInstance | null) => void;
};

type KakaoPolylineInstance = {
  setPath: (path: KakaoLatLng[]) => void;
  setMap: (map: KakaoMapInstance | null) => void;
};

type KakaoMapsNamespace = {
  load: (callback: () => void) => void;
  LatLng: new (lat: number, lng: number) => KakaoLatLng;
  LatLngBounds: new () => KakaoBoundsInstance;
  Map: new (container: HTMLElement, options: { center: KakaoLatLng; level: number }) => KakaoMapInstance;
  CustomOverlay: new (options: {
    position: KakaoLatLng;
    content: HTMLElement;
    xAnchor?: number;
    yAnchor?: number;
    zIndex?: number;
    map?: KakaoMapInstance;
  }) => KakaoCustomOverlayInstance;
  Polyline: new (options: {
    map?: KakaoMapInstance;
    path: KakaoLatLng[];
    strokeWeight: number;
    strokeColor: string;
    strokeOpacity: number;
    strokeStyle: string;
  }) => KakaoPolylineInstance;
  services: {
    Places: new () => {
      keywordSearch: (
        keyword: string,
        callback: (results: KakaoPlaceResult[], status: KakaoPlacesSearchStatus) => void,
      ) => void;
    };
  };
};

declare global {
  interface Window {
    kakao?: { maps: KakaoMapsNamespace };
  }
}

let loadPromise: Promise<void> | null = null;

/** SDK 스크립트를 한 번만 넣고, 준비되면 끝나는 프로미스를 돌려줍니다. */
export function loadKakaoMaps(): Promise<void> {
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('브라우저에서만 사용할 수 있습니다.'));
  }
  if (window.kakao?.maps?.services) {
    return Promise.resolve();
  }
  if (loadPromise) {
    return loadPromise;
  }

  const key = process.env.NEXT_PUBLIC_KAKAO_MAP_KEY;
  if (!key) {
    return Promise.reject(new Error('카카오 지도 키(NEXT_PUBLIC_KAKAO_MAP_KEY)가 설정되지 않았습니다.'));
  }

  loadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${key}&autoload=false&libraries=services`;
    script.async = true;
    script.onload = () => window.kakao!.maps.load(() => resolve());
    script.onerror = () => {
      loadPromise = null;
      reject(new Error('카카오 지도 스크립트를 불러오지 못했습니다.'));
    };
    document.head.appendChild(script);
  });
  return loadPromise;
}

/** 위도·경도 한 점 */
export type LatLngLiteral = { lat: number; lng: number };

/** 지도에 표시하는 사람. 마커의 색과 이름표가 달라집니다. */
export type LiveMapRole = 'escort' | 'client';

/** 지도 마커와 범례가 같이 쓰는 색·이름. (카카오T 의 기사/승객처럼 한눈에 구분되게 색을 크게 달리합니다) */
export const LIVE_MAP_ROLE_STYLE: Record<LiveMapRole, { color: string; label: string }> = {
  escort: { color: '#2563EB', label: '동행인' },
  client: { color: '#F08A1C', label: '의뢰인' },
};

/** 누구의 위치를 어디에 표시할지. 위치를 모르는 사람은 비워 둡니다. */
export type LiveMapMarkers = Partial<Record<LiveMapRole, LatLngLiteral>>;

/** 실시간 위치 지도를 조작하는 손잡이. SDK 타입을 밖으로 내보내지 않으려고 이 모양으로 감쌌습니다. */
export type LiveMapController = {
  /**
   * 사람별 프로필 마커를 옮깁니다. 두 사람이 모두 있으면 둘이 한 화면에 보이게 지도 범위를 맞추고,
   * 한 사람만 있으면 그 사람을 따라갑니다. trail 이 있으면 지나온 경로 선도 같이 그립니다.
   */
  update: (markers: LiveMapMarkers, trail?: LatLngLiteral[]) => void;
  /** 지도 영역의 크기가 바뀌었을 때(처음 보일 때 등) 다시 맞춥니다. */
  relayout: () => void;
};

/** 위치를 아직 모를 때 지도를 먼저 띄워 둘 중심 (서울시청) */
const DEFAULT_CENTER: LatLngLiteral = { lat: 37.5665, lng: 126.978 };
/** 카카오 지도 확대 수준 (숫자가 작을수록 더 확대됩니다) */
const DEFAULT_LEVEL = 3;
/** 두 사람을 한 화면에 맞출 때 가장자리에 남겨 둘 여백(px). 마커가 잘리지 않게 넉넉히 둡니다. */
const BOUNDS_PADDING = 80;

const SVG_NS = 'http://www.w3.org/2000/svg';

/** 프로필 안에 들어가는 흰색 사람 모양 아이콘 */
function createPersonIcon(): SVGElement {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('width', '20');
  svg.setAttribute('height', '20');
  svg.setAttribute('fill', '#ffffff');
  svg.setAttribute('aria-hidden', 'true');

  const head = document.createElementNS(SVG_NS, 'circle');
  head.setAttribute('cx', '12');
  head.setAttribute('cy', '8');
  head.setAttribute('r', '4');
  const body = document.createElementNS(SVG_NS, 'path');
  body.setAttribute('d', 'M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8z');

  svg.append(head, body);
  return svg;
}

/**
 * 카카오T 처럼 이름표 + 동그란 프로필 + 아래로 향한 꼬리 모양의 핀. 외부 이미지 없이 스타일만으로 그립니다.
 * 꼬리 끝이 실제 위치를 가리키고, "나"의 핀은 테두리 고리로 한 번 더 강조합니다.
 */
function createProfilePin(role: LiveMapRole, isMe: boolean): HTMLElement {
  const { color, label } = LIVE_MAP_ROLE_STYLE[role];

  const pin = document.createElement('div');
  pin.setAttribute('aria-label', `${label}의 현재 위치${isMe ? ' (나)' : ''}`);
  pin.style.display = 'flex';
  pin.style.flexDirection = 'column';
  pin.style.alignItems = 'center';
  pin.style.pointerEvents = 'none';

  const tag = document.createElement('div');
  tag.textContent = isMe ? `${label} (나)` : label;
  tag.style.background = color;
  tag.style.color = '#ffffff';
  tag.style.font = '700 11px/1 sans-serif';
  tag.style.padding = '5px 9px';
  tag.style.borderRadius = '999px';
  tag.style.whiteSpace = 'nowrap';
  tag.style.boxShadow = '0 1px 4px rgba(0, 0, 0, 0.3)';
  tag.style.marginBottom = '4px';

  const avatar = document.createElement('div');
  avatar.style.width = '38px';
  avatar.style.height = '38px';
  avatar.style.borderRadius = '50%';
  avatar.style.background = color;
  avatar.style.border = '3px solid #ffffff';
  avatar.style.boxShadow = isMe
    ? `0 0 0 4px ${color}55, 0 2px 6px rgba(0, 0, 0, 0.3)`
    : '0 2px 6px rgba(0, 0, 0, 0.3)';
  avatar.style.display = 'flex';
  avatar.style.alignItems = 'center';
  avatar.style.justifyContent = 'center';
  avatar.appendChild(createPersonIcon());

  const tail = document.createElement('div');
  tail.style.width = '0';
  tail.style.height = '0';
  tail.style.borderLeft = '7px solid transparent';
  tail.style.borderRight = '7px solid transparent';
  tail.style.borderTop = `10px solid ${color}`;
  tail.style.marginTop = '-2px';

  pin.append(tag, avatar, tail);
  return pin;
}

/**
 * container 안에 지도를 만들고, 의뢰인·동행인 프로필 마커와 이동 경로를 갱신할 수 있는 손잡이를 돌려줍니다.
 * me 는 이 화면을 보는 사람입니다. 그 사람의 핀에는 "(나)" 이름표와 강조 고리가 붙습니다.
 * SDK 를 불러오지 못하면(키 없음·도메인 미등록 등) 프로미스가 실패합니다.
 */
export async function createLiveMap(container: HTMLElement, me?: LiveMapRole): Promise<LiveMapController> {
  await loadKakaoMaps();

  const { maps } = window.kakao!;
  const toLatLng = (point: LatLngLiteral) => new maps.LatLng(point.lat, point.lng);

  const map = new maps.Map(container, { center: toLatLng(DEFAULT_CENTER), level: DEFAULT_LEVEL });
  const overlays: Partial<Record<LiveMapRole, InstanceType<typeof maps.CustomOverlay>>> = {};
  let line: InstanceType<typeof maps.Polyline> | undefined;
  let centered = false;

  return {
    update(markers, trail) {
      const shown: ReturnType<typeof toLatLng>[] = [];

      for (const role of ['escort', 'client'] as const) {
        const point = markers[role];
        const overlay = overlays[role];

        if (!point) {
          // 위치를 모르게 된 사람의 핀은 지도에서 뺍니다.
          if (overlay) {
            overlay.setMap(null);
            delete overlays[role];
          }
          continue;
        }

        const latlng = toLatLng(point);
        shown.push(latlng);
        if (overlay) {
          overlay.setPosition(latlng);
        } else {
          overlays[role] = new maps.CustomOverlay({
            position: latlng,
            content: createProfilePin(role, role === me),
            xAnchor: 0.5,
            yAnchor: 1, // 핀의 꼬리 끝이 위치에 오도록 아래쪽 가운데를 기준으로 둡니다.
            zIndex: role === 'escort' ? 2 : 1,
            map,
          });
        }
      }

      if (shown.length >= 2) {
        // 두 사람이 한 화면에 들어오도록 범위를 맞춥니다.
        const bounds = new maps.LatLngBounds();
        shown.forEach((latlng) => bounds.extend(latlng));
        map.setBounds(bounds, BOUNDS_PADDING, BOUNDS_PADDING, BOUNDS_PADDING, BOUNDS_PADDING);
        centered = true;
      } else if (shown.length === 1) {
        // 첫 위치는 부드럽게 움직이지 않고 바로 그 자리로 보냅니다.
        if (centered) map.panTo(shown[0]);
        else map.setCenter(shown[0]);
        centered = true;
      }

      if (trail && trail.length > 1) {
        const path = trail.map(toLatLng);
        if (!line) {
          line = new maps.Polyline({
            map,
            path,
            strokeWeight: 4,
            strokeColor: LIVE_MAP_ROLE_STYLE.escort.color,
            strokeOpacity: 0.7,
            strokeStyle: 'solid',
          });
        } else {
          line.setPath(path);
        }
      }
    },
    relayout() {
      map.relayout();
    },
  };
}

/** 화면에서 쓰기 좋게 다듬은 검색 결과 한 줄 */
export type PlaceSearchResult = {
  /** 장소 이름 (병원명 등). 주소만 검색했으면 빈 문자열일 수 있습니다. */
  name: string;
  /** 도로명 주소가 있으면 그것, 없으면 지번 주소 */
  address: string;
  lat: number;
  lng: number;
};

/** 키워드(병원명·주소 등)로 장소를 찾습니다. 결과가 없으면 빈 배열을 돌려줍니다. */
export async function searchPlaces(keyword: string): Promise<PlaceSearchResult[]> {
  const trimmed = keyword.trim();
  if (!trimmed) return [];

  await loadKakaoMaps();

  return new Promise((resolve, reject) => {
    const places = new window.kakao!.maps.services.Places();
    places.keywordSearch(trimmed, (results, status) => {
      if (status === 'OK') {
        resolve(
          results.map((place) => ({
            name: place.place_name,
            address: place.road_address_name || place.address_name,
            lat: Number(place.y),
            lng: Number(place.x),
          })),
        );
      } else if (status === 'ZERO_RESULT') {
        resolve([]);
      } else {
        reject(new Error('주소 검색에 실패했습니다.'));
      }
    });
  });
}
