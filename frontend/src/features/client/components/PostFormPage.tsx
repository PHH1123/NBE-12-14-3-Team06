'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { ChevronLeft, Info } from 'lucide-react';
import { AppShell } from '@/components/layout';
import DropdownSelect from '@/components/ui/DropdownSelect';
import { LOGIN_HOME_BY_ROLE, useRequireAuth } from '@/features/auth';
import { createPost, fetchPostRaw, updatePost, type PostDto, type PostWriteRequest } from '@/features/post';
import { fetchRidesByPost, type RideDto } from '@/features/ride';
import type { PlaceSearchResult } from '@/lib/kakaoMap';
import {
  EMPTY_FORM,
  MIN_HOURLY_PAY,
  TIME_OPTIONS,
  TRANSPORT_OPTIONS,
  combineDateTime,
  diffMinutes,
  disabledTimesAtOrAfter,
  disabledTimesAtOrBefore,
  estimateAmount,
  formatMinutes,
  minSelectableTime,
  parsePay,
  splitRegion,
  toIsoDateTime,
  toRideLabel,
  toRideSelect,
  todayDateString,
} from '../model/postForm';
import type { PostFormValues } from '../types';
import AddressSearchField from './form/AddressSearchField';

const FIELD = 'w-full h-[48px] px-3.5 bg-white border border-[#E1E5E8] rounded-xl text-sm text-[#14243A] placeholder:text-[#9AA2AA] focus:border-[#426B93] focus-visible:outline-2 focus-visible:outline-[#426B93] focus-visible:outline-offset-2';
const LABEL = 'block text-xs font-semibold text-[#67717C] mb-1.5';
const ERROR = 'mt-1.5 text-xs font-medium text-[#B4413C]';
const PERSONAL_INFO = /01[0-9]-?[0-9]{3,4}-?[0-9]{4}|\b[0-9]{6}-?[0-9]{7}\b/;
const TRANSPORT_SELECT_OPTIONS = TRANSPORT_OPTIONS.map((label) => ({ value: label, label }));

function Required() {
  return <span className="text-[#B4413C]">*</span>;
}

function FormCard({ title, first = false, children }: { title: string; first?: boolean; children: ReactNode }) {
  return (
    <section className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs space-y-5">
      <div className="border-b border-[#E1E5E8] pb-3">
        <h2 className="text-xl font-semibold text-[#14243A]">{title}</h2>
        {first && <p className="text-xs text-[#67717C] mt-0.5">* 표시는 필수 입력 항목입니다.</p>}
      </div>
      {children}
    </section>
  );
}

function TimeSelect({ id, name, value, onChange, disabledOptions = [], required = false }: {
  id: string;
  name: string;
  value: string;
  onChange: (value: string) => void;
  disabledOptions?: string[];
  required?: boolean;
}) {
  return (
    <DropdownSelect
      id={id}
      name={name}
      value={value}
      onValueChange={onChange}
      placeholder="시간 선택"
      options={TIME_OPTIONS.map((time) => ({
        value: time,
        label: time,
        disabled: disabledOptions.includes(time),
      }))}
      required={required}
    />
  );
}

/** 백엔드 공고 응답 + 이동수단(Ride 2건)을 수정 폼의 초기값으로 바꿉니다. */
function toFormValues(dto: PostDto, rides: RideDto[]): PostFormValues {
  const { region, district } = splitRegion(dto.hospitalAddress);
  const rideToHospital = rides.find((ride) => ride.direction === 'TO_HOSPITAL');
  const rideToHome = rides.find((ride) => ride.direction === 'TO_HOME');
  return {
    title: dto.title,
    hospitalName: dto.hospitalName,
    hospitalAddress: dto.hospitalAddress,
    hospitalLat: dto.hospitalLat,
    hospitalLng: dto.hospitalLng,
    region,
    district,
    departure: dto.pickupAddress,
    pickupLat: dto.pickupLat,
    pickupLng: dto.pickupLng,
    date: dto.escortStartAt.slice(0, 10),
    startTime: dto.escortStartAt.slice(11, 16),
    endTime: dto.escortEndAt.slice(11, 16),
    hourlyPay: dto.hourlyPay.toLocaleString(),
    // ⚠️ 동행인원은 백엔드에 없는 값이라 수정 화면에서 다시 선택해야 합니다.
    transportOut: toRideLabel(rideToHospital?.selected),
    transportBack: toRideLabel(rideToHome?.selected),
    party: '',
    reportRequested: dto.reportRequired,
    description: dto.content,
    note: dto.patientNote ?? '',
    recruitStartDate: dto.recruitStartAt.slice(0, 10),
    recruitStartTime: dto.recruitStartAt.slice(11, 16),
    recruitEndDate: dto.recruitEndAt.slice(0, 10),
    recruitEndTime: dto.recruitEndAt.slice(11, 16),
  };
}

/** 숫자만 남기고 "14,000" 처럼 콤마를 붙입니다. */
function formatPay(value: string): string {
  const digits = value.replace(/[^0-9]/g, '');
  return digits ? Number(digits).toLocaleString() : '';
}

/** 실제 공고 등록·수정 API와 연결된 폼입니다. 병원 및 출발지의 주소와 좌표는 카카오 장소 검색으로 결정됩니다. */
export default function PostFormPage() {
  const params = useParams<{ postId?: string }>();
  const postId = params.postId ? Number(params.postId) : undefined;
  const editing = postId !== undefined;
  const router = useRouter();

  // 로그인 안 됐으면 useRequireAuth 가 알아서 /login 으로 보냅니다.
  // role 은 훅이 하나만 받을 수 있어서(CLIENT|ADMIN 둘 다 허용해야 함) 여기서 직접 확인합니다.
  const { user, loading: authLoading, unauthenticated } = useRequireAuth();
  const authorized = !!user && (user.role === 'CLIENT' || user.role === 'ADMIN');

  useEffect(() => {
    if (authLoading || unauthenticated || !user || authorized) return;
    router.replace(LOGIN_HOME_BY_ROLE[user.role]);
  }, [authLoading, unauthenticated, user, authorized, router]);

  // result.postId 로 "지금 postId 의 결과인지" 판단합니다. 새로 작성하는 경우는 서버에서 가져올 게 없어 바로 채웁니다.
  const [result, setResult] = useState<{ postId?: number; initial?: PostFormValues; error?: string } | undefined>(
    () => (editing ? undefined : { postId, initial: EMPTY_FORM }),
  );

  useEffect(() => {
    if (!editing) return;
    let ignore = false;
    Promise.all([fetchPostRaw(postId), fetchRidesByPost(postId)])
      .then(([dto, rides]) => !ignore && setResult({ postId, initial: toFormValues(dto, rides) }))
      .catch((error: Error) => !ignore && setResult({ postId, error: error.message }));
    return () => {
      ignore = true;
    };
  }, [editing, postId]);

  const initial = result && result.postId === postId ? result.initial : undefined;
  const loadError = result && result.postId === postId ? result.error : undefined;

  // 로그인 확인 중이거나(스피너), 로그인이 안 됐거나(곧 /login 으로 이동), 역할이 안 맞으면(곧 자기 홈으로 이동)
  // 폼을 그리지 않고 기다립니다.
  if (authLoading || unauthenticated || !authorized) {
    return (
      <AppShell>
        <section className="bg-white py-[100px] text-center">
          <p className="text-xl font-semibold text-brand">확인하는 중입니다.</p>
        </section>
      </AppShell>
    );
  }

  if (!initial) {
    return (
      <AppShell>
        <section className="bg-white py-[100px] text-center">
          <p className="text-xl font-semibold text-brand">{loadError ? `불러오지 못했습니다. (${loadError})` : '불러오는 중입니다.'}</p>
          <Link href="/client/posts" className="mx-auto mt-8 flex h-14 w-60 items-center justify-center rounded-[25px] border border-line text-xl font-semibold text-brand">
            작성한 공고로
          </Link>
        </section>
      </AppShell>
    );
  }

  return <PostFormFields key={postId ?? 'new'} postId={postId} initial={initial} />;
}

type FormFieldsProps = {
  /** 있으면 수정, 없으면 새로 작성 */
  postId?: number;
  initial: PostFormValues;
};

function PostFormFields({ postId, initial }: FormFieldsProps) {
  const router = useRouter();
  const editing = postId !== undefined;

  const [hospitalName, setHospitalName] = useState(initial.hospitalName);
  const [hospitalAddress, setHospitalAddress] = useState(initial.hospitalAddress);
  const [hospitalLat, setHospitalLat] = useState(initial.hospitalLat);
  const [hospitalLng, setHospitalLng] = useState(initial.hospitalLng);
  const [region, setRegion] = useState(initial.region);

  const [departure, setDeparture] = useState(initial.departure);
  const [pickupLat, setPickupLat] = useState(initial.pickupLat);
  const [pickupLng, setPickupLng] = useState(initial.pickupLng);

  const [date, setDate] = useState(initial.date);
  const [startTime, setStartTime] = useState(initial.startTime);
  const [endTime, setEndTime] = useState(initial.endTime);
  const [hourlyPay, setHourlyPay] = useState(initial.hourlyPay);
  const [transportOut, setTransportOut] = useState(initial.transportOut);
  const [transportBack, setTransportBack] = useState(initial.transportBack);
  const [recruitStartDate, setRecruitStartDate] = useState(initial.recruitStartDate);
  const [recruitStartTime, setRecruitStartTime] = useState(initial.recruitStartTime);
  const [recruitEndDate, setRecruitEndDate] = useState(initial.recruitEndDate);
  const [recruitEndTime, setRecruitEndTime] = useState(initial.recruitEndTime);
  const [submitting, setSubmitting] = useState(false);
  const [fieldsError, setFieldsError] = useState('');
  const [submitError, setSubmitError] = useState('');

  const minutes = diffMinutes(startTime, endTime);
  const amount = estimateAmount(hourlyPay, minutes);
  const timeError = startTime && endTime && minutes <= 0 ? '종료 시간은 시작 시간보다 늦어야 합니다.' : '';

  // 동행 날짜가 오늘이면 지금 이전 시간은 "시작 시간" 에서 아예 선택 못하게 막습니다.
  const startTimeBound = minSelectableTime(date);
  const startDisabled = startTimeBound ? TIME_OPTIONS.filter((option) => option <= startTimeBound) : [];
  // "예상 종료 시간" 은 시작 시간보다 늦어야 하니, 시작 시간(없으면 위 기준 시각) 이하는 막습니다.
  const endTimeBound = startTime || startTimeBound;
  const endDisabled = endTimeBound ? TIME_OPTIONS.filter((option) => option <= endTimeBound) : [];

  const handleDateChange = (value: string) => {
    setDate(value);
    const bound = minSelectableTime(value);
    const effectiveStartTime = bound && startTime && startTime <= bound ? '' : startTime;
    if (effectiveStartTime !== startTime) setStartTime('');
    if (bound && endTime && endTime <= bound) setEndTime('');

    // 모집 일정은 항상 동행 시작보다 이전이어야 합니다. 동행 날짜가 당겨져 더 이상 안 맞으면 초기화합니다.
    const escortStartAt = combineDateTime(value, effectiveStartTime);
    if (escortStartAt && combineDateTime(recruitEndDate, recruitEndTime) >= escortStartAt) {
      setRecruitEndDate('');
      setRecruitEndTime('');
    }
    if (escortStartAt && combineDateTime(recruitStartDate, recruitStartTime) >= escortStartAt) {
      setRecruitStartDate('');
      setRecruitStartTime('');
      setRecruitEndDate('');
      setRecruitEndTime('');
    }
  };

  const handleStartTimeChange = (value: string) => {
    setStartTime(value);
    if (endTime && endTime <= value) setEndTime('');

    const escortStartAt = combineDateTime(date, value);
    if (escortStartAt && combineDateTime(recruitEndDate, recruitEndTime) >= escortStartAt) {
      setRecruitEndTime('');
    }
    if (escortStartAt && combineDateTime(recruitStartDate, recruitStartTime) >= escortStartAt) {
      setRecruitStartTime('');
      setRecruitEndTime('');
    }
  };

  // 모집 시작: 오늘 이전 날짜·시간은 선택 못하게(백엔드: 모집 시작은 현재 시간보다 이후), 동행 시작보다는 항상 빨라야 합니다.
  const recruitStartTimeBound = minSelectableTime(recruitStartDate);
  const recruitStartDisabled = Array.from(
    new Set([
      ...(recruitStartTimeBound ? TIME_OPTIONS.filter((option) => option <= recruitStartTimeBound) : []),
      ...disabledTimesAtOrAfter(recruitStartDate, date, startTime),
    ]),
  );

  const handleRecruitStartDateChange = (value: string) => {
    setRecruitStartDate(value);
    const bound = minSelectableTime(value);
    const effectiveRecruitStartTime = bound && recruitStartTime && recruitStartTime <= bound ? '' : recruitStartTime;
    if (effectiveRecruitStartTime !== recruitStartTime) setRecruitStartTime('');

    const recruitStartAt = combineDateTime(value, effectiveRecruitStartTime);
    if (recruitStartAt && combineDateTime(recruitEndDate, recruitEndTime) <= recruitStartAt) {
      setRecruitEndDate('');
      setRecruitEndTime('');
    }
  };

  const handleRecruitStartTimeChange = (value: string) => {
    setRecruitStartTime(value);
    const recruitStartAt = combineDateTime(recruitStartDate, value);
    if (recruitStartAt && combineDateTime(recruitEndDate, recruitEndTime) <= recruitStartAt) {
      setRecruitEndTime('');
    }
  };

  // 모집 마감: 모집 시작보다 늦어야 하고, 동행 시작보다는 빨라야 합니다(백엔드: 모집 마감 < 동행 시작).
  const recruitEndDisabled = Array.from(
    new Set([...disabledTimesAtOrBefore(recruitEndDate, recruitStartDate, recruitStartTime), ...disabledTimesAtOrAfter(recruitEndDate, date, startTime)]),
  );

  const handleRecruitEndDateChange = (value: string) => {
    setRecruitEndDate(value);
    if (recruitEndTime) {
      const invalid =
        disabledTimesAtOrBefore(value, recruitStartDate, recruitStartTime).includes(recruitEndTime) ||
        disabledTimesAtOrAfter(value, date, startTime).includes(recruitEndTime);
      if (invalid) setRecruitEndTime('');
    }
  };

  let recruitError = '';
  if (recruitStartDate && recruitStartTime) {
    const bound = minSelectableTime(recruitStartDate);
    if (bound && recruitStartTime <= bound) {
      recruitError = '모집 시작 시간은 현재 시간보다 이후여야 합니다.';
    }
  }
  if (!recruitError && recruitStartDate && recruitEndDate) {
    if (recruitEndDate < recruitStartDate) {
      recruitError = '모집 종료일은 시작일보다 빠를 수 없습니다.';
    } else if (recruitEndDate === recruitStartDate && recruitStartTime && recruitEndTime && recruitEndTime <= recruitStartTime) {
      recruitError = '모집 종료는 모집 시작보다 늦어야 합니다.';
    }
  }
  if (!recruitError && date && recruitEndDate) {
    if (recruitEndDate > date) {
      recruitError = '모집 마감일은 동행 날짜보다 늦을 수 없습니다.';
    } else if (recruitEndDate === date && startTime && recruitEndTime && recruitEndTime >= startTime) {
      recruitError = '모집 마감 시간은 동행 시작 시간보다 빨라야 합니다.';
    }
  }

  const handleHospitalSelect = (place: PlaceSearchResult) => {
    setFieldsError('');
    setHospitalName(place.name || hospitalName);
    setHospitalAddress(place.address);
    setHospitalLat(place.lat);
    setHospitalLng(place.lng);
    setRegion(splitRegion(place.address).region);
  };

  const handleDepartureSelect = (place: PlaceSearchResult) => {
    setFieldsError('');
    setDeparture(place.address);
    setPickupLat(place.lat);
    setPickupLng(place.lng);
  };

  const locationError =
    hospitalLat === null || hospitalLng === null
      ? '병원을 검색 결과에서 선택해주세요.'
      : pickupLat === null || pickupLng === null
        ? '출발지를 검색 결과에서 선택해주세요.'
        : '';

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitError('');
    setFieldsError('');

    if (hospitalLat === null || hospitalLng === null || pickupLat === null || pickupLng === null) {
      setSubmitError(locationError || '병원·출발지의 위치가 확인되지 않았습니다.');
      return;
    }

    // 폼에 이름(name)이 붙은 입력값을 전부 가져옵니다 (state로 관리하지 않는 필드 포함).
    const form = new FormData(event.currentTarget);
    const value = (name: string) => String(form.get(name) ?? '').trim();

    if (PERSONAL_INFO.test(value('description'))) {
      setFieldsError('공고 설명에는 연락처나 주민등록번호 등 개인 정보를 직접 작성하지 말아주세요.');
      return;
    }
    if (timeError || recruitError || !minutes) {
      setFieldsError(timeError || recruitError || '동행 일정과 모집 기간을 확인해주세요.');
      return;
    }
    const recruitStartAt = combineDateTime(recruitStartDate, recruitStartTime);
    const recruitEndAt = combineDateTime(recruitEndDate, recruitEndTime);
    const escortStartAt = combineDateTime(date, startTime);
    if (!recruitStartAt || !recruitEndAt || !escortStartAt || recruitStartAt >= recruitEndAt || recruitEndAt >= escortStartAt) {
      setFieldsError('모집 시작, 모집 마감, 동행 시작 순서로 일정을 설정해주세요.');
      return;
    }
    if (new Date(recruitStartAt).getTime() <= Date.now()) {
      setFieldsError('모집 시작 시간은 현재 시각보다 이후여야 합니다.');
      return;
    }

    // 이동수단은 select 가 required 라 여기까지 오면 값이 있지만, ENUM 에 없는 값이면 백엔드가 400 을 냅니다.
    const rideSelectToHospital = toRideSelect(value('transportOut'));
    const rideSelectToHome = toRideSelect(value('transportBack'));
    if (!rideSelectToHospital || !rideSelectToHome) {
      setSubmitError('이동수단을 선택해주세요.');
      return;
    }

    if (parsePay(hourlyPay) < MIN_HOURLY_PAY) {
      setSubmitError(`시급은 최저시급(10,320원)에 수수료 10%를 더한 ${MIN_HOURLY_PAY.toLocaleString()}원 이상이어야 합니다.`);
      return;
    }

    const payload: PostWriteRequest = {
      title: value('title'),
      content: value('description'),
      region,
      hospitalName,
      hospitalAddress,
      hospitalLat,
      hospitalLng,
      pickupAddress: departure,
      pickupLat,
      pickupLng,
      hourlyPay: parsePay(hourlyPay),
      recruitStartAt: toIsoDateTime(recruitStartDate, recruitStartTime),
      recruitEndAt: toIsoDateTime(recruitEndDate, recruitEndTime),
      escortStartAt: toIsoDateTime(date, startTime),
      escortEndAt: toIsoDateTime(date, endTime),
      rideSelectToHospital,
      rideSelectToHome,
      patientNote: value('note') || null,
      reportRequired: form.get('reportRequested') === 'on',
    };

    setSubmitting(true);
    try {
      if (editing) {
        await updatePost(postId, payload);
        router.push(`/client/posts/${postId}`);
      } else {
        const created = await createPost(payload);
        // paymentId 는 공고 등록 시 함께 생성된 결제(Payment) 의 id 입니다. 결제 승인 때 필요합니다.
        // 결제 금액은 넘기지 않습니다 — 결제 화면이 서버의 Payment.amount 를 직접 받아 씁니다.
        // (여기서 계산한 추정치를 넘기면 서버 계산과 어긋나 승인 단계에서 거절될 수 있습니다)
        const query = new URLSearchParams({
          postId: String(created.id),
          paymentId: String(created.paymentId),
        });
        router.push(`/client/posts/new/payment?${query.toString()}`);
      }
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : editing ? '공고 수정에 실패했습니다.' : '공고 등록에 실패했습니다.');
      setSubmitting(false);
    }
  };

  return (
    <AppShell>
      <section className="w-full bg-[#F7F8F8] py-12 lg:py-16">
        <div className="max-w-[1240px] mx-auto px-5 sm:px-8 lg:px-16">
          <div className="max-w-3xl mx-auto space-y-8">
            <Link
              href={editing ? `/client/posts/${postId}` : '/client/posts'}
              className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] hover:text-[#14243A] focus-visible:outline-2 focus-visible:outline-[#426B93]"
            >
              <ChevronLeft className="w-4 h-4" />
              <span>{editing ? '공고 상세로 돌아가기' : '내 공고 목록으로 돌아가기'}</span>
            </Link>

            <div>
              <h1 className="text-3xl sm:text-4xl font-semibold text-[#14243A] tracking-tight">
                {editing ? '공고 수정' : '공고 작성'}
              </h1>
              <p className="text-sm text-[#67717C] mt-1">
                동행이 필요한 병원과 일정을 입력해 주세요. {editing ? '변경된 내용을 저장합니다.' : '작성이 완료되면 결제 화면으로 이동합니다.'}
              </p>
            </div>

            <form onSubmit={handleSubmit} className="space-y-8">
              <FormCard title="기본 정보" first>
                <div>
                  <label htmlFor="post-title" className={LABEL}>공고 제목 <Required /></label>
                  <input id="post-title" name="title" required defaultValue={initial.title}
                    placeholder="예: 정기 검진 및 수납 보조 동행 매니저님을 모십니다" className={FIELD} />
                </div>

                <div>
                  <label htmlFor="post-hospital" className={LABEL}>병원명 <Required /></label>
                  <AddressSearchField id="post-hospital" variant="compact" placeholder="병원명을 검색해주세요. (예: 서울아산병원)"
                    defaultValue={initial.hospitalName} formatSelected={(place) => place.name || place.address}
                    onSelect={handleHospitalSelect}
                    onClear={() => { setHospitalName(''); setHospitalAddress(''); setHospitalLat(null); setHospitalLng(null); setRegion(''); }} />
                  {hospitalName && hospitalLat !== null && hospitalLng !== null && (
                    <p className="text-xs text-[#2F7D5B] mt-1 font-medium">선택된 병원: {hospitalName} ({hospitalAddress})</p>
                  )}
                </div>

                <div>
                  <label htmlFor="post-departure" className={LABEL}>출발지 <Required /></label>
                  <AddressSearchField id="post-departure" variant="compact" placeholder="출발지를 검색해주세요. (예: 자택 주소)"
                    defaultValue={initial.departure} onSelect={handleDepartureSelect}
                    onClear={() => { setDeparture(''); setPickupLat(null); setPickupLng(null); }} />
                  {departure && pickupLat !== null && pickupLng !== null && (
                    <p className="text-xs text-[#2F7D5B] mt-1 font-medium">선택된 출발지: {departure}</p>
                  )}
                </div>
              </FormCard>

              <FormCard title="모집 일정">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label htmlFor="post-date" className={LABEL}>동행 날짜 <Required /></label>
                    <input id="post-date" name="date" type="date" required min={todayDateString()}
                      value={date} onChange={(event) => handleDateChange(event.target.value)} className={FIELD} />
                  </div>
                  <div>
                    <label htmlFor="post-start" className={LABEL}>동행 시작 시간 <Required /></label>
                    <TimeSelect id="post-start" name="startTime" value={startTime} onChange={handleStartTimeChange} disabledOptions={startDisabled} required />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label htmlFor="post-end" className={LABEL}>예상 종료 시간 <Required /></label>
                    <TimeSelect id="post-end" name="endTime" value={endTime} onChange={setEndTime} disabledOptions={endDisabled} required />
                    {timeError && <p role="alert" className={ERROR}>{timeError}</p>}
                  </div>
                  <div>
                    <label htmlFor="post-duration" className={LABEL}>예상 소요 시간</label>
                    <input id="post-duration" type="text" readOnly tabIndex={-1} value={formatMinutes(minutes)}
                      placeholder="시간을 선택하면 자동으로 계산됩니다" className={FIELD} />
                  </div>
                </div>

                <div className="pb-3">
                  <label htmlFor="post-pay" className={LABEL}>시급 (금액) <Required /></label>
                  <div className="relative">
                    <input id="post-pay" name="hourlyPay" required inputMode="numeric"
                      value={hourlyPay} onChange={(event) => setHourlyPay(formatPay(event.target.value))}
                      placeholder="예: 15,000" className={`${FIELD} h-[52px] pl-4 pr-12 text-base font-bold`} />
                    <span className="absolute right-4 top-1/2 -translate-y-1/2 text-sm text-[#67717C] pointer-events-none">원</span>
                  </div>
                  <p className="text-xs text-[#67717C] mt-1.5">
                    시급은 최저시급(10,320원)에 수수료 10%를 더한 금액({MIN_HOURLY_PAY.toLocaleString()}원) 이상이어야 해요.
                  </p>
                  {amount > 0 && <p className="text-xs font-medium text-[#426B93] mt-1.5">예상 결제 금액: {amount.toLocaleString()}원 (실제 결제 금액은 결제 화면에서 확인)</p>}
                </div>
                <div className="border-t border-[#E1E5E8] pt-8">
                  <div className="mb-4">
                    <h3 className="text-sm font-semibold text-[#14243A]">매니저 모집 기간 <Required /></h3>
                    <p className="mt-1 text-xs text-[#67717C]">모집 시작은 현재 이후, 마감은 동행 시작 전으로 설정해 주세요.</p>
                  </div>

                  <div className="space-y-3">
                    <div className="grid gap-2 sm:grid-cols-[88px_minmax(0,1fr)] sm:items-center sm:gap-4">
                      <p className="text-xs font-semibold text-[#67717C]">모집 시작</p>
                      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                        <input id="recruit-start-date" name="recruitStartDate" type="date" aria-label="모집 시작 날짜" required
                          min={todayDateString()} max={date || undefined}
                          value={recruitStartDate} onChange={(event) => handleRecruitStartDateChange(event.target.value)} className={`${FIELD} min-w-0`} />
                        <label htmlFor="recruit-start-time" className="sr-only">모집 시작 시간</label>
                        <TimeSelect id="recruit-start-time" name="recruitStartTime" value={recruitStartTime}
                          onChange={handleRecruitStartTimeChange} disabledOptions={recruitStartDisabled} required />
                      </div>
                    </div>

                    <div className="grid gap-2 sm:grid-cols-[88px_minmax(0,1fr)] sm:items-center sm:gap-4">
                      <p className="text-xs font-semibold text-[#67717C]">모집 마감</p>
                      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                        <input id="recruit-end-date" name="recruitEndDate" type="date" aria-label="모집 마감 날짜" required
                          min={recruitStartDate || todayDateString()} max={date || undefined}
                          value={recruitEndDate} onChange={(event) => handleRecruitEndDateChange(event.target.value)} className={`${FIELD} min-w-0`} />
                        <label htmlFor="recruit-end-time" className="sr-only">모집 마감 시간</label>
                        <TimeSelect id="recruit-end-time" name="recruitEndTime" value={recruitEndTime}
                          onChange={setRecruitEndTime} disabledOptions={recruitEndDisabled} required />
                      </div>
                    </div>
                  </div>
                  {recruitError && <p role="alert" className={ERROR}>{recruitError}</p>}
                </div>

              </FormCard>

              <FormCard title="상세 내용">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label htmlFor="post-transport-out" className={LABEL}>갈 때 이동수단 <Required /></label>
                    <DropdownSelect
                      id="post-transport-out"
                      name="transportOut"
                      placeholder="선택해주세요"
                      value={transportOut}
                      options={TRANSPORT_SELECT_OPTIONS}
                      onValueChange={setTransportOut}
                      required
                    />
                  </div>
                  <div>
                    <label htmlFor="post-transport-back" className={LABEL}>올 때 이동수단 <Required /></label>
                    <DropdownSelect
                      id="post-transport-back"
                      name="transportBack"
                      placeholder="선택해주세요"
                      value={transportBack}
                      options={TRANSPORT_SELECT_OPTIONS}
                      onValueChange={setTransportBack}
                      required
                    />
                  </div>
                </div>

                <div className="bg-[#F7F8F8] p-4 rounded-xl border border-[#E1E5E8]">
                  <label className="flex items-center gap-3 cursor-pointer">
                    <input type="checkbox" name="reportRequested" defaultChecked={initial.reportRequested}
                      className="w-4 h-4 accent-[#426B93] rounded border-[#E1E5E8]" />
                    <span>
                      <span className="block text-sm font-semibold text-[#14243A]">동행 후 진료 보고서 작성 요청</span>
                      <span className="block text-xs text-[#67717C] mt-1">동행 매니저에게 진료 보고서 작성을 요청합니다.</span>
                    </span>
                  </label>
                </div>

                <div>
                  <label htmlFor="post-note" className={LABEL}>어르신 보행 컨디션 및 주의사항</label>
                  <textarea id="post-note" name="note" rows={2} defaultValue={initial.note}
                    placeholder="예: 지팡이를 사용하시며 계단 이용 시 부축이 필요합니다."
                    className={`${FIELD} h-auto p-3.5 resize-none`} />
                </div>

                <div>
                  <div className="flex flex-wrap items-center justify-between gap-1.5 mb-1.5">
                    <label htmlFor="post-description" className="block text-xs font-semibold text-[#67717C]">공고 설명 <Required /></label>
                    <span className="text-[11px] text-[#426B93] font-medium flex items-center gap-1">
                      <Info className="w-3.5 h-3.5" />
                      개인 정보(연락처, 주민번호 등)는 작성하지 말아주세요.
                    </span>
                  </div>
                  <textarea id="post-description" name="description" required rows={4} defaultValue={initial.description}
                    placeholder="진료 일정과 매니저님께 요청드리고 싶은 내용을 자세히 작성해주세요."
                    className={`${FIELD} h-auto p-3.5 resize-none`} />
                </div>
              </FormCard>

              {(submitError || fieldsError) && <p role="alert" className={ERROR}>{submitError || fieldsError}</p>}

              <div className="flex items-center justify-between gap-4 pt-4">
                <Link href={editing ? `/client/posts/${postId}` : '/client/posts'}
                  className="inline-flex h-[52px] items-center justify-center px-6 rounded-[10px] border border-[#E1E5E8] text-sm font-semibold text-[#67717C] hover:bg-white">
                  취소
                </Link>
                <button type="submit" disabled={submitting}
                  className="h-[52px] px-8 rounded-[10px] bg-[#14243A] text-white text-base font-semibold hover:bg-[#1f3757] focus-visible:outline-2 focus-visible:outline-[#426B93] shadow-xs disabled:cursor-not-allowed disabled:opacity-60">
                  {submitting ? (editing ? '수정 중...' : '등록 중...') : editing ? '수정하기' : '등록하기 (결제로 이동)'}
                </button>
              </div>
            </form>
          </div>
        </div>
      </section>
    </AppShell>
  );
}
