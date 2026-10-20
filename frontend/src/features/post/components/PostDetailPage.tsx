'use client';

import Link from 'next/link';
import { usePathname, useParams, useRouter } from 'next/navigation';
import { useEffect, useState, type ReactNode } from 'react';
import { AppShell } from '@/components/layout';
import { AlertCircle, ArrowRight, Building2, ChevronLeft, FileCheck, LoaderCircle, Lock, Shield, ShieldAlert, MapPin, Users } from 'lucide-react';
import { applyToPost, fetchMyApplications } from '@/features/application';
import { useCurrentUser, useRequireAuth, type CurrentUser } from '@/features/auth';
import PostGuestNotice from './PostGuestNotice';
import { useChatWidget } from '@/features/chat';
// 배럴(@/features/education)로 가져오면 education 화면 → @/features/post → 이 파일로 순환 import 가 생겨서 api 모듈을 직접 가져옵니다.
import { isEducationRequiredError } from '@/features/education/api';
import {
  cancelPayment,
  fetchPaymentsByPost,
  isCancelablePayment,
  type PaymentDto,
  type PaymentStatus,
} from '@/features/payment';
import { fetchRidesByPost, type RideDto } from '@/features/ride';
import { cn } from '@/lib/cn';
import { deletePost, fetchPost } from '../api';
import { daysFromNow, formatFullDate, formatTime, parseDateTime } from '../lib/date';
import { canDeletePost, canEditPost, postStatusLabel, toPostStatusKey } from '../model/status';
import type { PostDetail, PostStatusKey } from '../types';
import { useActionDialog } from '@/components/ui/useActionDialog';

const CARD = 'rounded-2xl border border-[#E1E5E8] bg-white shadow-xs';
const CARD_TITLE = 'text-xl font-semibold text-[#14243A]';
const BUTTON =
  'flex items-center justify-center rounded-[10px] px-5 font-semibold transition-colors duration-200 disabled:cursor-not-allowed disabled:opacity-50';

/** 공고 상세에서만 사용하는 로딩·오류·접근 제한 안내. 목록 이동과 권한 판별은 기존 로직을 그대로 유지합니다. */
function PostDetailFeedback({
  kind,
  title,
  description,
  listHref,
  listLabel = '목록으로',
  detail,
}: {
  kind: 'loading' | 'error' | 'forbidden';
  title: string;
  description: string;
  listHref: string;
  listLabel?: string;
  detail?: string;
}) {
  const isLoading = kind === 'loading';
  const Icon = isLoading ? LoaderCircle : kind === 'forbidden' ? ShieldAlert : AlertCircle;

  return (
    <AppShell>
      <section className="flex min-h-[calc(100svh-72px)] items-center bg-[#F7F8F8] px-5 py-14 sm:px-8 lg:py-20">
        <div className="mx-auto w-full max-w-[1240px] lg:px-16">
          <div className="mx-auto w-full max-w-[680px] rounded-2xl border border-[#E1E5E8] bg-white px-6 py-12 text-center shadow-[0_4px_20px_rgba(30,39,57,0.045)] sm:px-12 sm:py-14">
            <span className="inline-flex items-center rounded-md bg-[#EDF0F2] px-3 py-1.5 text-xs font-semibold tracking-[0.08em] text-[#426B93]">
              {isLoading ? 'LOADING' : kind === 'forbidden' ? 'ACCESS RESTRICTED' : 'NOTICE'}
            </span>
            <div className="mx-auto mt-7 flex h-16 w-16 items-center justify-center rounded-2xl bg-[#EDF0F2] text-[#426B93]">
              <Icon className={`h-8 w-8 ${isLoading ? 'animate-spin' : ''}`} strokeWidth={1.8} aria-hidden="true" />
            </div>
            <h1 role={isLoading ? 'status' : undefined} className="mt-6 text-2xl font-semibold tracking-tight text-[#14243A] sm:text-[28px]">
              {title}
            </h1>
            <p className="mx-auto mt-3 max-w-[460px] text-sm leading-7 text-[#67717C] sm:text-base">
              {description}
            </p>
            {detail && (
              <p role="alert" className="mx-auto mt-4 max-w-[480px] break-words rounded-lg bg-[#F7F8F8] px-4 py-3 text-sm leading-6 text-[#67717C]">
                {detail}
              </p>
            )}
            <Link
              href={listHref}
              className="group mx-auto mt-8 inline-flex h-12 items-center justify-center gap-2 rounded-[10px] border border-[#DDE3EA] bg-white px-6 text-sm font-semibold text-[#14243A] transition-colors duration-200 hover:border-[#A5B7CA] hover:bg-[#F7F8F8] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93]"
            >
              {listLabel}
              <ArrowRight className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-1" aria-hidden="true" />
            </Link>
          </div>
        </div>
      </section>
    </AppShell>
  );
}

/** 결제 취소 사유 길이 제한. 입력한 사유가 그대로 토스 취소 내역으로 넘어가서 넉넉하게만 잡아 둡니다. */
const MAX_CANCEL_REASON_LENGTH = 200;

/** 결제 상태 배지 */
const PAYMENT_LABEL: Record<PaymentStatus, { text: string; className: string }> = {
  READY: { text: '미결제', className: 'border-[#9A6B12]/20 bg-[#9A6B12]/10 text-[#84601B]' },
  IN_PROGRESS: { text: '결제 중', className: 'border-[#426B93]/20 bg-[#426B93]/10 text-[#426B93]' },
  DONE: { text: '결제 완료', className: 'border-[#2F7D5B]/20 bg-[#2F7D5B]/10 text-[#2F7D5B]' },
  CANCELED: { text: '결제 취소', className: 'border-[#B4413C]/20 bg-[#B4413C]/10 text-[#B4413C]' },
  PARTIAL_CANCELED: { text: '부분 취소', className: 'border-[#426B93]/20 bg-[#426B93]/10 text-[#426B93]' },
  DELETED: { text: '공고 삭제', className: 'border-[#E1E5E8] bg-[#EDF0F2] text-[#67717C]' },
};

/** 결제가 아니라 취소 시점을 보여 줘야 하는 상태 (부분 취소는 결제 자체가 살아 있어 결제 일시를 그대로 둡니다) */
const CANCELED_STATUS: PaymentStatus[] = ['CANCELED', 'DELETED'];

type SectionProps = {
  title: string;
  children: ReactNode;
  className?: string;
};

function Section({ title, children, className }: SectionProps) {
  return (
    <section className={cn(CARD, 'p-6 sm:p-8', className)}>
      <h2 className={cn(CARD_TITLE, 'mb-6')}>{title}</h2>
      {children}
    </section>
  );
}

const STATUS_STYLE: Record<PostStatusKey, { className: string; dot: string }> = {
  open: { className: 'border-[#426B93]/35 bg-[#E5EEF7] text-[#305879]', dot: 'bg-[#426B93]' },
  matched: { className: 'border-[#2F7D5B]/35 bg-[#E5F1EA] text-[#286849]', dot: 'bg-[#2F7D5B]' },
  inProgress: { className: 'border-[#14243A]/30 bg-[#E5E9F0] text-[#14243A]', dot: 'bg-[#14243A]' },
  completed: { className: 'border-[#5E697A]/30 bg-[#E9EDF1] text-[#344055]', dot: 'bg-[#67717C]' },
  canceled: { className: 'border-[#B4413C]/25 bg-[#F7ECEB] text-[#99433E]', dot: 'bg-[#B4413C]' },
  expired: { className: 'border-[#9A6B12]/30 bg-[#F7F0DE] text-[#84601B]', dot: 'bg-[#9A6B12]' },
};

function DetailRow({ label, children }: { label: string; children: ReactNode; labelWidth?: number }) {
  return (
    <div className="min-w-0 text-sm">
      <dt className="mb-1 block text-xs font-semibold text-[#67717C]">{label}</dt>
      <dd className="font-medium leading-relaxed text-[#202832] [overflow-wrap:anywhere]">{children}</dd>
    </div>
  );
}

type Props = {
  /** 누가 보는지. 동행 매니저는 "지원하기", 의뢰인(작성자)은 "수정하기·삭제" 버튼이 나옵니다. */
  viewer?: 'common' | 'escort' | 'client';
};

const REQUIRED_ROLE: Record<'escort' | 'client', CurrentUser['role']> = {
  escort: 'ESCORT',
  client: 'CLIENT',
};

/**
 * 공고 상세 — Figma 동행 매니저_공고 상세 225:1146 · 의뢰인_공고 상세 521:2254
 *
 * viewer='common'(/posts/[postId])은 로그인 상태를 확인한 다음 본문을 표시합니다.
 * viewer='escort'·'client' 는 각각 역할 가드를 거친 뒤에만 본문을 그립니다.
 */
export default function PostDetailPage({ viewer = 'common' }: Props) {
  if (viewer === 'common') return <CommonPostDetailGate />;
  return <GuardedPostDetailPage viewer={viewer} />;
}

function CommonPostDetailGate() {
  const pathname = usePathname();
  const { user, loading, unauthenticated, error } = useCurrentUser();

  if (loading) {
    return <AppShell><section className="bg-[#F7F8F8] py-28 text-center text-sm text-[#67717C]">로그인 상태를 확인하는 중입니다.</section></AppShell>;
  }
  if (unauthenticated) {
    return <AppShell><PostGuestNotice nextPath={pathname} /></AppShell>;
  }
  if (!user) {
    return (
      <AppShell>
        <section className="bg-[#F7F8F8] px-5 py-28 text-center">
          <p role="alert" className="mb-4 text-sm text-[#67717C]">{error || '로그인 상태를 확인하지 못했습니다.'}</p>
          <button type="button" onClick={() => window.location.reload()} className="rounded-[10px] bg-[#14243A] px-5 py-3 text-sm font-semibold text-white">다시 확인하기</button>
        </section>
      </AppShell>
    );
  }
  return <PostDetailPageBody viewer="common" />;
}

function GuardedPostDetailPage({ viewer }: { viewer: 'escort' | 'client' }) {
  const { loading, user } = useRequireAuth(REQUIRED_ROLE[viewer]);

  if (loading) {
    return (
      <PostDetailFeedback
        kind="loading"
        title="공고를 불러오는 중입니다."
        description="공고 상세 정보를 준비하고 있어요. 잠시만 기다려 주세요."
        listHref={viewer === 'client' ? '/client/posts' : '/escort/posts'}
      />
    );
  }
  if (!user) return null;

  return <PostDetailPageBody viewer={viewer} />;
}

/**
 * 상세 API(GET /api/v1/posts/{postId})로 조회합니다. 삭제·지원하기도 여기서 연결합니다.
 * 이동수단은 GET /api/v1/rides/posts/{postId}로 별도 조회합니다.
 * ⚠️ 백엔드에 없는 항목(진료과 · 의뢰인 유형/보호자 동행 여부/성별 선호/소개)은 화면에서 뺐습니다.
 */
function PostDetailPageBody({ viewer }: Props) {
  const params = useParams<{ postId: string }>();
  const router = useRouter();
  const pathname = usePathname();
  const { user, unauthenticated } = useCurrentUser();
  const { openChatWith } = useChatWidget();
  const { confirm, dialog } = useActionDialog();
  const postId = Number(params.postId);

  // result.postId 로 "지금 postId 의 결과인지"를 판단합니다. (postId 가 바뀐 직후에는 이전 결과를 버리고 loading 으로 봅니다)
  const [result, setResult] = useState<{ postId: number; data?: PostDetail; error?: string }>();
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string>();
  const [applyState, setApplyState] = useState<'idle' | 'applying' | 'applied'>('idle');
  const [alreadyApplied, setAlreadyApplied] = useState(false);
  const [applyError, setApplyError] = useState<string>();
  /** 교육 미이수로 지원이 막혔는지 (에러 문구 옆에 교육 영상 링크를 보여줍니다) */
  const [educationRequired, setEducationRequired] = useState(false);
  const [rides, setRides] = useState<RideDto[]>([]);
  // 이 공고의 결제 전체(미결제·완료·취소 모두). 공고 조회와 같은 방식으로 postId 를 같이 들고 있습니다.
  const [historyResult, setHistoryResult] = useState<{ postId: number; data: PaymentDto[] }>();
  const [canceling, setCanceling] = useState(false);
  const [cancelError, setCancelError] = useState<string>();
  /** 취소 사유 입력칸을 펼친 결제 번호 (닫혀 있으면 undefined) */
  const [cancelOpenId, setCancelOpenId] = useState<number>();
  const [cancelReason, setCancelReason] = useState('');
  /** 결제를 취소한 뒤 결제 상태를 다시 불러오기 위한 값 (바뀌면 조회 effect 가 다시 돕니다) */
  const [paymentReload, setPaymentReload] = useState(0);

  useEffect(() => {
    let ignore = false;
    fetchPost(postId)
      .then((data) => !ignore && setResult({ postId, data }))
      .catch((error: Error) => !ignore && setResult({ postId, error: error.message }));
    return () => {
      ignore = true;
    };
  }, [postId]);

  useEffect(() => {
    let ignore = false;

    fetchRidesByPost(postId)
        .then((data) => {
          if (!ignore) {
            setRides(data);
          }
        })
        .catch(() => {
          if (!ignore) {
            setRides([]);
          }
        });

    return () => {
      ignore = true;
    };
  }, [postId]);

  useEffect(() => {
    if (user?.role !== 'ESCORT') {
      setAlreadyApplied(false);
      return;
    }

    let ignore = false;

    fetchMyApplications()
        .then((applications) => {
          if (ignore) return;

          setAlreadyApplied(
              applications.some(
                  (application) =>
                      application.postId === postId &&
                      application.applicationStatus !== 'CANCELED'
              )
          );
        })
        .catch(() => {
          if (!ignore) {
            setAlreadyApplied(false);
          }
        });

    return () => {
      ignore = true;
    };
  }, [postId, user?.role]);
  const loading = result?.postId !== postId;
  const post = loading ? undefined : result?.data;
  const loadError = loading ? undefined : result?.error;

  const isClient = user?.role === 'CLIENT' && post?.clientId === user.username;

  /**
   * 작성자에게 "연락하기"를 보여 줄지.
   * 로그인한 의뢰인·동행 매니저에게만, 그리고 자기 공고가 아닐 때만 보여 줍니다
   * (자기 자신과는 채팅방을 만들 수 없고, 관리자는 채팅 대상이 아닙니다).
   */
  const canContactAuthor =
    !!post &&
    (user?.role === 'CLIENT' || user?.role === 'ESCORT') &&
    user.username !== post.clientId;

  const listHref =
    viewer === 'client'
      ? '/client/posts'
      : viewer === 'escort'
        ? '/escort/posts'
        : '/posts';

  // 결제 이력은 내 공고에서만 보여 줍니다. (결제 조회는 의뢰인 본인 로그인이 필요합니다)
  const showPayments = isClient && !!post;

  useEffect(() => {
    if (!showPayments) return;
    let ignore = false;
    fetchPaymentsByPost(postId)
      .then((data) => !ignore && setHistoryResult({ postId, data }))
      // 조회에 실패해도 공고 상세는 그대로 보여 줍니다. (결제 정보 카드만 뜨지 않습니다)
      .catch(() => !ignore && setHistoryResult({ postId, data: [] }));
    return () => {
      ignore = true;
    };
  }, [showPayments, postId, paymentReload]);

  // 다른 공고로 이동한 직후에는 이전 공고의 결제 이력을 쓰지 않습니다.
  const payments: PaymentDto[] = showPayments && historyResult?.postId === postId ? historyResult.data : [];

  // 아직 내지 않은 결제. 목록은 최근 건이 앞이라 find 가 가장 나중에 만들어진 미결제 건입니다.
  // (결제를 취소하면 백엔드가 같은 금액의 READY 건을 새로 만들어 둡니다)
  // 공고별 조회(GET /api/v1/payments/posts/{postId})를 따로 부르지 않고 이미 받아 온 이력에서 뽑습니다.
  const unpaidPayment = payments.find((payment) => payment.paymentStatus === 'READY');
  // 동행이 끝난 뒤의 미결제는 실제 동행 시간이 늘어 생긴 차액이라 "추가 결제"로 구분해 보여 줍니다.
  // (공고 목록 카드 ClientPostCard 와 같은 기준입니다)
  const isExtraPayment = !!post && toPostStatusKey(post.postStatus) === 'completed';

  // 결제 취소는 아직 아무도 매칭되지 않은 공고에서만 보여 줍니다.
  // 백엔드(PaymentService.cancel)도 모집 중·마감 기한 초과만 허용합니다(InvalidException 46) —
  // 매칭·진행 중인 공고의 결제를 취소하면 동행 매니저가 보수 없이 동행을 하게 되고,
  // 동행이 끝난 건은 이미 정산 데이터가 만들어져 있기 때문입니다.
  const postAllowsCancel = !!post && ['open', 'expired'].includes(toPostStatusKey(post.postStatus));

  const handleDelete = async () => {
    if (!await confirm({ title: '이 공고를 삭제할까요?', description: '삭제한 공고는 되돌릴 수 없습니다.', tone: 'danger', confirmLabel: '삭제하기' })) return;
    setDeleting(true);
    setDeleteError(undefined);
    try {
      // TODO: 삭제 API(DELETE /api/v1/posts/{postId})는 로그인 쿠키(작성자 본인)가 있어야 합니다.
      await deletePost(postId);
      router.push(listHref);
    } catch (error) {
      setDeleteError(error instanceof Error ? error.message : '삭제에 실패했습니다.');
      setDeleting(false);
    }
  };

  /** 사유 입력칸을 접습니다. 입력하던 사유와 실패 문구도 같이 버립니다. */
  const closeCancelForm = () => {
    setCancelOpenId(undefined);
    setCancelReason('');
    setCancelError(undefined);
  };

  const handleCancelPayment = async (paymentId: number) => {
    // 취소 사유는 백엔드 PaymentCancelRequest 의 필수 본문이고, 토스 취소 내역에도 그대로 남습니다.
    const reason = cancelReason.trim();
    if (!reason) return;

    setCanceling(true);
    setCancelError(undefined);
    try {
      await cancelPayment(paymentId, reason);
      // 취소하면 백엔드가 같은 금액의 READY 결제를 새로 만들어 둡니다(= 다시 결제할 수 있는 "미결제" 상태).
      // 공고의 대기 중인 지원자도 서버가 모두 거절하므로 결제 이력을 다시 불러옵니다.
      setHistoryResult(undefined);
      setCancelOpenId(undefined);
      setCancelReason('');
      setPaymentReload((count) => count + 1);
    } catch (error) {
      setCancelError(error instanceof Error ? error.message : '결제 취소에 실패했습니다.');
    } finally {
      setCanceling(false);
    }
  };

  const handleApply = async () => {
    // 인증 상태가 만료된 경우에는 로그인 화면으로 이동합니다.
    if (unauthenticated) {
      router.push(`/login?next=${encodeURIComponent(pathname)}`);
      return;
    }
    if (!await confirm({ title: '이 공고에 지원하시겠습니까?', confirmLabel: '지원하기' })) return;
    // TODO: 지원 API(POST /api/v1/applications/{postId})는 동행 매니저(ESCORT) 로그인 쿠키가 있어야 합니다.
    setApplyState('applying');
    setApplyError(undefined);
    setEducationRequired(false);
    try {
      await applyToPost(postId);
      setApplyState('applied');
      setAlreadyApplied(true);
    } catch (error) {
      setApplyState('idle');
      setApplyError(error instanceof Error ? error.message : '지원에 실패했습니다.');
      setEducationRequired(isEducationRequiredError(error));
    }
  };

  if (!post) {
    return (
      <PostDetailFeedback
        kind={loading ? 'loading' : 'error'}
        title={loading ? '공고를 불러오는 중입니다.' : '공고를 불러오지 못했습니다.'}
        description={loading ? '공고 상세 정보를 준비하고 있어요. 잠시만 기다려 주세요.' : '일시적인 오류가 발생했거나 공고가 삭제되었을 수 있어요. 목록에서 다시 확인해 주세요.'}
        detail={loadError}
        listHref={listHref}
      />
    );
  }

  // /client/posts/[postId] 는 CLIENT 로그인이면 누구든 들어올 수 있어서(useRequireAuth 는 역할만 봅니다),
  // 공고 작성자 본인이 아니면 여기서 막습니다. (GET /posts/{id} 는 로그인한 회원이면 볼 수 있고, 환자 특이사항만 서버가 가립니다)
  if (viewer === 'client' && post.clientId !== user?.username) {
    return (
      <PostDetailFeedback
        kind="forbidden"
        title="본인이 작성한 공고만 볼 수 있습니다."
        description="다른 의뢰인이 작성한 공고는 내 공고 상세 화면에서 확인할 수 없어요."
        listHref={listHref}
        listLabel="내 공고 목록으로"
      />
    );
  }

  // 의뢰인은 자기 공고의 진행 상태(모집 중 · 매칭 완료 · 진행 중 · 동행 완료 …)를 봅니다.
  // 동행 매니저·비로그인은 "지원할 수 있는지"가 중요해서 신규/오늘 마감 같은 모집 배지를 그대로 씁니다.
  const label = postStatusLabel(post.postStatus);
  const statusKey = toPostStatusKey(post.postStatus);
  const statusStyle = STATUS_STYLE[statusKey];
  // 작성자·승인된 동행인·관리자 여부는 서버가 판단해 내려 줍니다 (가려진 공고는 patientNote 가 비어 옵니다)
  const canViewSensitive = post.canViewSensitive;
  const date = formatFullDate(daysFromNow(post.startsInDays));
  const duration = `약 ${post.hours}시간`;
  const pay = `시급 ${post.hourlyPay.toLocaleString()}원`;
  const location = `${post.region} ${post.district}`;
  const toHospitalRide = rides.find((ride) => ride.direction === 'TO_HOSPITAL');
  const toHomeRide = rides.find((ride) => ride.direction === 'TO_HOME');

  const rideLabel: Record<NonNullable<RideDto['selected']>, string> = {
    WALK: '도보',
    BUS: '대중교통',
    TAXI: '택시',
    OWN_CAR: '자가용',
  };

  const toHospitalTransport = toHospitalRide?.selected
      ? rideLabel[toHospitalRide.selected]
      : '미정';

  const toHomeTransport = toHomeRide?.selected
      ? rideLabel[toHomeRide.selected]
      : '미정';

  const applyClosed = post.badge === 'closed';
  // 지원은 동행 매니저만 할 수 있으므로 다른 역할에는 지원 버튼을 비활성화합니다.
  const applyBlockedByRole = user?.role !== 'ESCORT';
  const applyLabel = applyBlockedByRole
      ? '지원 불가'
      : alreadyApplied || applyState === 'applied'
          ? '지원완료'
          : applyState === 'applying'
              ? '지원 중…'
              : applyClosed
                  ? '지원 불가'
                  : '지원하기';

  const applyDisabled =
      applyBlockedByRole ||
      alreadyApplied ||
      applyClosed ||
      applyState !== 'idle';

  const editHref = `/client/posts/${post.id}/edit`;

  // 결제 화면(토스 위젯)은 공고 등록 직후의 최초 결제와 같은 화면을 씁니다. 공고 목록 카드와 같은 주소입니다.
  // flow=extra 는 결제 후 공고 등록 완료 화면이 아니라 이 공고 상세로 돌아오기 위한 표시입니다.
  const unpaidHref = unpaidPayment
    ? `/client/posts/new/payment?${new URLSearchParams({
        postId: String(post.id),
        paymentId: String(unpaidPayment.id),
        pay: String(post.hourlyPay),
        ...(isExtraPayment ? { flow: 'extra' } : {}),
      })}`
    : '';

  const payLabel = isExtraPayment ? '추가 결제' : '결제하기';

  // 수정·삭제는 백엔드가 공고 상태로 막습니다. 눌러도 실패할 버튼은 아예 보여 주지 않습니다.
  // 수정은 "모집 중 + 모집 시작 전", 삭제는 "모집 중이거나 마감 기한 초과"일 때만 됩니다.
  // (조건이 서로 달라서 따로 계산합니다 — 모집이 시작된 공고는 삭제만 됩니다)
  const canEdit = isClient && canEditPost(post.postStatus, post.recruitStarted);
  const canDelete = isClient && canDeletePost(post.postStatus);

  const estimatedTotal = Math.round(post.hourlyPay * post.hours);

  const actionErrors = deleteError || cancelError || applyError;

  return (
    <AppShell>
      <section className="w-full bg-[#F7F8F8] py-12 lg:py-16">
      <div className="max-w-[1240px] mx-auto px-5 sm:px-8 lg:px-16">
        {/* Back Link */}
        <Link
          href={listHref}
          className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#67717C] hover:text-[#14243A] mb-8 sc-focus"
        >
          <ChevronLeft className="w-4 h-4" />
          <span>목록으로 돌아가기</span>
        </Link>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-start">
          {/* Main content column (col-span-8) */}
          <div className="lg:col-span-8 space-y-8">
            {/* 1. 공고 요약 */}
            <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs">
              <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
                <div className="flex items-center gap-2">
                  <span className={`px-2.5 py-1 rounded text-xs font-semibold inline-flex items-center gap-1.5 ${statusStyle.className}`}>
                    <span className={`w-1.5 h-1.5 rounded-full ${statusStyle.dot}`} aria-hidden="true" />
                    {label.text}
                  </span>
                  {post.badge === 'closing' && <span className="text-xs font-semibold text-[#426B93] bg-[#EDF0F2] px-2.5 py-1 rounded">오늘 마감</span>}
                  {post.badge === 'new' && <span className="text-xs font-semibold text-[#426B93] bg-[#EDF0F2] px-2.5 py-1 rounded">신규</span>}
                </div>
                <div className="text-xs text-[#67717C]">
                  등록일: {post.postedAt} · 의뢰인: <span className="font-semibold text-[#202832]">{post.clientId}</span>
                </div>
              </div>

              <h1 className="text-2xl sm:text-3xl font-semibold text-[#14243A] tracking-tight mb-4">
                {post.title}
              </h1>

              <div className="flex flex-wrap items-center gap-4 text-sm text-[#67717C] pt-2 border-t border-[#E1E5E8]">
                <div className="flex items-center gap-1.5">
                  <Building2 className="w-4 h-4 text-[#426B93]" />
                  <span className="font-medium text-[#202832]">{post.hospitalName}</span>
                </div>
                <span>·</span>
                <div>{location}</div>
                <span>·</span>
                <div>동행 시간: <span className="text-[#202832] font-medium">{duration}</span></div>
              </div>
            </div>

            {/* 2. 기본 정보 */}
            <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs space-y-6">
              <h2 className="text-xl font-semibold text-[#14243A]">기본 정보</h2>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 text-sm">
                <div>
                  <span className="block text-xs font-semibold text-[#67717C] mb-1">방문 병원</span>
                  <p className="font-medium text-[#202832]">{post.hospitalName}</p>
                  <p className="text-xs text-[#67717C] mt-0.5">{post.hospitalAddress}</p>
                </div>

                <div>
                  <span className="block text-xs font-semibold text-[#67717C] mb-1">동행 일시</span>
                  <p className="font-medium text-[#202832] tabular-nums">{date} {post.startTime}</p>
                  <p className="text-xs text-[#67717C] mt-0.5">예상 소요 시간: 약 {post.hours}시간</p>
                </div>

                <div>
                  <span className="block text-xs font-semibold text-[#67717C] mb-1">이동 수단</span>
                  <p className="text-[#202832]">갈 때: <span className="font-medium">{toHospitalTransport}</span></p>
                  <p className="text-[#202832] mt-0.5">올 때: <span className="font-medium">{toHomeTransport}</span></p>
                </div>

                <div>
                  <span className="block text-xs font-semibold text-[#67717C] mb-1">시간당 시급</span>
                  <p className="text-lg font-bold text-[#14243A] tabular-nums">
                    {post.hourlyPay.toLocaleString()}원
                    <span className="text-xs font-normal text-[#67717C] ml-1">/ 시간당</span>
                  </p>

                </div>
              </div>
            </div>

            {/* 3. 모집 정보 */}
            <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs space-y-4">
              <h2 className="text-xl font-semibold text-[#14243A]">모집 정보</h2>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
                <div className="bg-[#F7F8F8] p-4 rounded-xl border border-[#E1E5E8]">
                  <span className="text-xs text-[#67717C] block mb-1">모집 마감 기한</span>
                  <span className="font-medium text-[#202832]">{post.recruitPeriod}</span>
                </div>
                <div className="bg-[#F7F8F8] p-4 rounded-xl border border-[#E1E5E8]">
                  <span className="text-xs text-[#67717C] block mb-1">진료 보고서 작성 요청</span>
                  <span className="font-medium text-[#426B93] flex items-center gap-1.5">
                    <FileCheck className="w-4 h-4" />
                    {post.reportRequired ? '동행 후 보고서 작성을 요청합니다' : '보고서 작성 요청이 없습니다'}
                  </span>
                </div>
              </div>
            </div>

            {/* 4. 공고 설명 */}
            <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs space-y-4">
              <h2 className="text-xl font-semibold text-[#14243A]">공고 상세 설명</h2>
              <p className="text-base text-[#202832] leading-relaxed whitespace-pre-line">
                {post.details.length > 0 ? post.details.join('\n') : '등록된 상세 설명이 없습니다.'}
              </p>
            </div>

            {/* 5. 민감 정보 (보호자 연락처 / 어르신 특이사항: 매칭된 매니저 또는 작성자만 열람) */}
            <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 sm:p-8 shadow-xs space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-xl font-semibold text-[#14243A] flex items-center gap-2">
                  <Shield className="w-5 h-5 text-[#426B93]" />
                  <span>보호자 정보 및 환자 특이사항</span>
                </h2>
                {!canViewSensitive && (
                  <span className="text-xs text-[#B4413C] font-medium flex items-center gap-1">
                    <Lock className="w-3.5 h-3.5" />
                    비공개 보안 정보
                  </span>
                )}
              </div>

              {canViewSensitive ? (
                <div className="bg-[#EDF0F2] rounded-xl p-5 border border-[#E1E5E8] space-y-4">
                  <div className="flex items-center gap-3">
                    <MapPin className="w-4 h-4 text-[#426B93]" />
                    <span className="text-sm text-[#67717C]">상세 출발지:</span>
                    <span className="text-sm font-semibold text-[#14243A]">{post.pickupAddress}</span>
                  </div>
                  <div className="pt-3 border-t border-[#E1E5E8] space-y-1">
                    <span className="text-xs font-semibold text-[#14243A] block">환자 특이사항:</span>
                    <p className="text-sm text-[#202832] leading-relaxed">
                      {post.patientNote.length > 0 ? post.patientNote.join('\n') : '등록된 특이사항이 없습니다.'}
                    </p>
                  </div>
                </div>
              ) : (
                <div className="bg-[#F7F8F8] rounded-xl p-6 border border-[#E1E5E8] text-center space-y-2">
                  <Lock className="w-6 h-6 text-[#67717C] mx-auto" />
                  <p className="text-sm font-medium text-[#14243A]">
                    환자 특이사항과 상세 출발지는 매칭된 매니저에게만 공개됩니다.
                  </p>
                  <p className="text-xs text-[#67717C]">
                    개인정보 보호를 위해 지원 승인 전까지 상세 정보가 표시되지 않습니다.
                  </p>
                </div>
              )}
            </div>
            {/* 결제 정보 — 이 공고의 결제 전부(미결제·완료·취소). 최근 건이 위에 옵니다. */}
            {payments.length > 0 && (
              <Section title="결제 정보">
                <ul className="flex flex-col">
                  {payments.map((payment, index) => {
                    const paymentLabel = PAYMENT_LABEL[payment.paymentStatus];
                    // 백엔드가 취소를 받아 주는 조건(결제 DONE + 공고 모집 중·마감 초과)일 때만 버튼을 답니다.
                    const cancelable = postAllowsCancel && isCancelablePayment(payment);
                    const formOpen = cancelOpenId === payment.id;
                    // 취소된 건은 결제 시점이 아니라 취소 시점을 보여 줍니다.
                    const canceled = CANCELED_STATUS.includes(payment.paymentStatus);
                    const dateValue = canceled ? payment.canceledAt : payment.approvedAt;

                    return (
                      <li
                        key={payment.id}
                        className={cn('flex flex-col gap-[11px]', index > 0 && 'mt-5 border-t border-[#E1E5E8] pt-5')}
                      >
                        <div className="flex flex-wrap items-center justify-between gap-3">
                          <span className={cn('inline-flex items-center whitespace-nowrap rounded-md border px-2.5 py-1 text-xs font-semibold', paymentLabel.className)}>
                            {paymentLabel.text}
                          </span>
                          {cancelable && !formOpen && (
                            <button
                              type="button"
                              onClick={() => setCancelOpenId(payment.id)}
                              className={cn(BUTTON, 'h-9 border border-[#B4413C]/30 bg-white px-4 text-xs text-[#B4413C] hover:bg-[#F7F8F8]')}
                            >
                              결제 취소
                            </button>
                          )}
                        </div>
                        <dl className="flex flex-col gap-[3px]">
                          <DetailRow label="결제 금액" labelWidth={140}>{`${payment.amount.toLocaleString()}원`}</DetailRow>
                          {/* 아직 내지 않은(READY) 건은 승인 시각이 없습니다. */}
                          <DetailRow label={canceled ? '취소 일시' : '결제 일시'} labelWidth={140}>
                            {dateValue ? `${formatFullDate(parseDateTime(dateValue))} ${formatTime(dateValue)}` : '-'}
                          </DetailRow>
                        </dl>

                        {/* 아직 내지 않은 건에는 공고 목록 카드와 같은 결제 버튼을 답니다.
                            동행 완료 공고(추가 결제)는 바로 아래 "추가 결제 안내" 카드가 같은 버튼을 들고 있어 생략합니다. */}
                        {payment.id === unpaidPayment?.id && !isExtraPayment && (
                          <Link href={unpaidHref} className={cn(BUTTON, 'mx-auto h-11 w-full max-w-sm bg-[#14243A] text-sm text-white hover:bg-[#426B93]')}>
                            {`${payment.amount.toLocaleString()}원 결제하기`}
                          </Link>
                        )}

                        {cancelable && formOpen && (
                          <>
                            <p className="text-base leading-6 font-medium text-[#67717C]">
                              결제를 취소하면 이 공고는 다시 미결제 상태가 되어 공개 목록에서 내려가고,
                              대기 중인 지원자는 모두 거절됩니다. 같은 금액으로 다시 결제할 수 있습니다.
                            </p>
                            <label htmlFor="cancel-reason" className="px-0.5 text-base leading-5 font-semibold text-[#14243A]">
                              결제 취소 사유
                            </label>
                            <textarea
                              id="cancel-reason"
                              value={cancelReason}
                              onChange={(event) => setCancelReason(event.target.value)}
                              maxLength={MAX_CANCEL_REASON_LENGTH}
                              disabled={canceling}
                              autoFocus
                              placeholder="예) 병원 예약이 취소되어 동행이 필요하지 않습니다."
                              className="h-[99px] w-full resize-none rounded-xl border border-[#E1E5E8] bg-white p-4 text-sm leading-5 text-[#14243A] placeholder:text-[#67717C]"
                            />
                            <div className="flex w-full flex-wrap justify-end gap-3">
                              <button
                                type="button"
                                onClick={closeCancelForm}
                                disabled={canceling}
                                className={cn(BUTTON, 'h-11 min-w-[100px] border border-[#E1E5E8] bg-white text-sm text-[#14243A] hover:bg-[#F7F8F8]')}
                              >
                                닫기
                              </button>
                              {/* 사유를 적지 않으면 서버가 빈 사유를 그대로 토스 취소 내역에 남기게 되므로 버튼을 막아 둡니다. */}
                              <button
                                type="button"
                                onClick={() => handleCancelPayment(payment.id)}
                                disabled={canceling || !cancelReason.trim()}
                                className={cn(BUTTON, 'h-11 min-w-[160px] border border-[#E1E5E8] bg-white text-sm text-[#B4413C] hover:bg-[#F7F8F8]')}
                              >
                                {canceling ? '결제 취소 중…' : `${payment.amount.toLocaleString()}원 결제 취소`}
                              </button>
                            </div>
                          </>
                        )}
                      </li>
                    );
                  })}
                </ul>
              </Section>
            )}

            {/* 추가 결제 안내 — 실제 동행 시간이 예상보다 길어져 차액이 남았을 때만 */}
            {isExtraPayment && unpaidPayment && (
              <section className={cn(CARD, 'border-[#426B93]/30 p-6 sm:p-8')}>
                <h2 className={cn(CARD_TITLE, 'mb-5')}>추가 결제 안내</h2>
                <p className="mb-5 text-base leading-6 font-medium text-[#14243A]">
                  실제 동행 시간이 예상보다 길어져 차액이 발생했습니다.
                  아래 금액을 결제하시면 동행 건이 최종 정산됩니다.
                </p>
                <dl className="mb-5 flex flex-col gap-[3px]">
                  <DetailRow label="추가 결제 금액" labelWidth={140}>{`${unpaidPayment.amount.toLocaleString()}원`}</DetailRow>
                  <DetailRow label="실제 동행 시간" labelWidth={140}>{`약 ${unpaidPayment.hours}시간`}</DetailRow>
                  <DetailRow label="적용 시급" labelWidth={140}>{`${unpaidPayment.hourlyPaySnapshot.toLocaleString()}원`}</DetailRow>
                </dl>
                <Link href={unpaidHref} className={cn(BUTTON, 'h-11 w-full bg-[#14243A] text-sm text-white hover:bg-[#426B93]')}>
                  {`${unpaidPayment.amount.toLocaleString()}원 추가 결제하기`}
                </Link>
              </section>
            )}


            {actionErrors && <p role="alert" className="text-sm font-medium text-[#B4413C]">{actionErrors}</p>}
            {educationRequired && (
              <Link href="/mypage/education" className={cn(BUTTON, 'h-11 w-full bg-[#14243A] text-sm text-white hover:bg-[#426B93]')}>
                교육 영상 보러 가기
              </Link>
            )}
          </div>

          {/* Sidebar / Sticky Action Area (col-span-4) */}
          <div className="lg:col-span-4 space-y-6 lg:sticky lg:top-28">
            {/* Pay & Policy Calculation Card */}
            <div className="bg-white rounded-2xl border border-[#E1E5E8] p-6 shadow-xs space-y-4">
              <span className="text-xs font-semibold text-[#67717C] uppercase tracking-wider block">
                정산 및 결제 요약
              </span>

              <div className="space-y-3 pb-4 border-b border-[#E1E5E8] text-sm">
                <div className="flex justify-between">
                  <span className="text-[#67717C]">시간당 시급</span>
                  <span className="font-medium text-[#202832] tabular-nums">
                    {post.hourlyPay.toLocaleString()}원
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[#67717C]">예상 활동 시간</span>
                  <span className="font-medium text-[#202832] tabular-nums">
                    {post.hours}시간
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[#67717C]">총 기본 활동비</span>
                  <span className="font-semibold text-[#14243A] tabular-nums">
                    {estimatedTotal.toLocaleString()}원
                  </span>
                </div>
                <div className="flex justify-between text-xs text-[#67717C]">
                  <span>플랫폼 중개 수수료</span>
                  <span className="tabular-nums">정산 내역에서 확인</span>
                </div>
              </div>

              <div className="flex items-baseline justify-between pt-1">
                <span className="text-sm font-semibold text-[#14243A]">
                  예상 활동비 합계
                </span>
                <span className="text-2xl font-bold text-[#14243A] tabular-nums">
                  {estimatedTotal.toLocaleString()}
                  <span className="text-sm font-normal text-[#67717C] ml-1">원</span>
                </span>
              </div>

              {/* Policy note */}
              <div className="bg-[#F7F8F8] p-3 rounded-lg text-[12px] text-[#67717C] space-y-1">
                <p>• 실제 결제 및 정산 금액은 결제·정산 내역을 기준으로 합니다.</p>
                <p>• 지원 및 취소는 서비스 운영 정책에 따라 처리됩니다.</p>
              </div>

              <div className="pt-2 space-y-3">
                {isClient ? (
                  <>
                    <Link href={`/client/posts/${post.id}/applicants`} className="w-full h-[52px] rounded-[10px] bg-[#14243A] text-white font-semibold text-base hover:bg-[#1f3757] transition-colors sc-focus flex items-center justify-center gap-2">
                      <Users className="w-5 h-5" aria-hidden="true" />
                      <span>지원자 확인</span>
                    </Link>
                    <div className="space-y-2">
                      <div className="flex items-center gap-2">
                        {canEdit ? (
                          <Link href={editHref} className="flex-1 h-[44px] rounded-[10px] border border-[#E1E5E8] text-sm font-semibold text-[#14243A] hover:bg-[#F7F8F8] flex items-center justify-center">수정하기</Link>
                        ) : (
                          <button type="button" disabled aria-describedby="post-edit-unavailable" className="flex-1 h-[44px] rounded-[10px] border border-[#E1E5E8] bg-[#F7F8F8] text-sm font-semibold text-[#8793A0] cursor-not-allowed">수정하기</button>
                        )}
                        {canDelete && <button type="button" onClick={handleDelete} disabled={deleting} className="h-[44px] px-4 rounded-[10px] border border-[#B4413C]/30 text-sm font-semibold text-[#B4413C] hover:bg-[#B4413C]/5 disabled:opacity-50">{deleting ? '삭제 중…' : '삭제하기'}</button>}
                      </div>
                      {!canEdit && (
                        <p id="post-edit-unavailable" className="text-xs text-[#67717C]">
                          {statusKey === 'open' ? '모집이 시작된 공고는 수정할 수 없습니다.' : '모집 중이며 모집 시작 전인 공고만 수정할 수 있습니다.'}
                        </p>
                      )}
                    </div>
                    {unpaidPayment && <Link href={unpaidHref} className="w-full h-[52px] rounded-[10px] bg-[#426B93] text-white font-semibold text-base hover:bg-[#345778] transition-colors flex items-center justify-center">{unpaidPayment.amount.toLocaleString()}원 {payLabel}</Link>}
                  </>
                ) : user?.role === 'ESCORT' ? (
                  <button type="button" onClick={handleApply} disabled={applyDisabled} className="w-full h-[52px] rounded-[10px] bg-[#426B93] text-white font-semibold text-base hover:bg-[#345778] transition-colors disabled:bg-[#EDF0F2] disabled:text-[#67717C] disabled:cursor-not-allowed">
                    {applyLabel === '지원하기' ? '이 공고에 지원하기' : applyLabel}
                  </button>
                ) : null}
                {canContactAuthor && (
                  <button type="button" onClick={() => openChatWith(post.clientUserId)} className="w-full h-[52px] rounded-[10px] border border-[#E1E5E8] text-sm font-semibold text-[#14243A] hover:bg-[#F7F8F8] transition-colors">의뢰인에게 연락하기</button>
                )}
                {user?.role === 'ESCORT' && <p className="flex items-center gap-2 text-xs text-[#67717C]"><Shield className="w-4 h-4 text-[#426B93] shrink-0" /> 지원 후 의뢰인 승인 시 매칭이 진행됩니다.</p>}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
    {dialog}
  </AppShell>
  );
}
