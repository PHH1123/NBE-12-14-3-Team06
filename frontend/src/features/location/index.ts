/**
 * location(실시간 위치) 도메인의 공개 API.
 * 이 도메인 바깥(app/, 다른 features/)에서는 반드시 여기를 통해서만 import 합니다.
 */
export { sendLocation, fetchLocation } from './api';
export { useShareLocation } from './lib/useShareLocation';
export { useLiveLocations } from './lib/useLiveLocations';
export { default as LiveMap } from './components/LiveMap';
export { default as LocationLegend } from './components/LocationLegend';
export type {
  LiveLocationsDto,
  LocationPointDto,
  SendLocationRequest,
  LiveLocations,
  LocationView,
  LocationRole,
  LatLng,
  ShareStatus,
} from './types';
