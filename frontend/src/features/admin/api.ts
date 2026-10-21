import { api, apiDelete, apiPatch, apiPost, type SpringPage } from '@/lib/api';

/** 2026-10-20 백엔드 AdminUserResponse */
export type AdminUserDto = {
  id: number;
  username: string;
  email: string;
  name: string;
  role: 'CLIENT' | 'ESCORT' | 'ADMIN';
  gender: 'MALE' | 'FEMALE' | null;
  birthDate: string | null;
  phoneNum: string | null;
  region: string | null;
  createdAt: string;
  deletedAt: string | null;
  userStatus: 'ACTIVE' | 'SUSPENDED' | 'BANNED';
  suspendEndAt: string | null;
  statusReason: string | null;
  deleted: boolean;
};
export type AdminEscortProfileDto = {
  userId: number;
  name: string;
  region: string | null;
  intro: string | null;
  averageRating: number | null;
  completedCount: number | null;
  grade: string | null;
  verified: boolean | null;
  bankName: string | null;
  accountHolder: string | null;
  accountNumber: string | null;
};
export type AdminUserUpdate = Pick<AdminUserDto, 'email' | 'name'> & {
  birthDate: string; phoneNum: string; region: string;
};
export type AdminPaymentAnalytics = { paidAmount: number; canceledAmount: number; counts: number };
export type AdminPaymentDaily = { approvedAt: string; paidAmount: number; counts: number };
export type AdminSettlementAnalytics = {
  totalSettlementAmount: number; settlementCount: number;
  totalSettledAmount: number; settledCount: number;
  totalUnSettledAmount: number; unSettledCount: number;
  totalPlatformFee: number; platformFeeCount: number;
  totalDonationAmount: number; donationCount: number;
  totalErrorSettlementAmount: number; errorSettlementCount: number;
};

/** 공통 api가 쿠키 인증/토큰 갱신/RsData 해제를 담당합니다. */
export function fetchAdminUsersPage(page = 0, size = 100, signal?: AbortSignal) {
  return api<SpringPage<AdminUserDto>>(`/api/v1/admin/users?page=${page}&size=${size}`, { signal });
}
export async function fetchAllAdminUsers(signal?: AbortSignal): Promise<AdminUserDto[]> {
  const size = 100;
  const first = await fetchAdminUsersPage(0, size, signal);
  if (!Array.isArray(first.content) || !Number.isSafeInteger(first.totalPages) || first.totalPages < 0) {
    throw new Error('회원 목록 응답 형식을 확인할 수 없습니다.');
  }
  const result = [...first.content];
  for (let start = 1; start < first.totalPages; start += 4) {
    const pages = await Promise.all(Array.from({ length: Math.min(4, first.totalPages - start) },
      (_, offset) => fetchAdminUsersPage(start + offset, size, signal)));
    for (const page of pages) {
      if (!Array.isArray(page.content)) throw new Error('회원 목록 응답 형식을 확인할 수 없습니다.');
      result.push(...page.content);
    }
  }
  return result;
}
export const fetchAdminUser = (id: number, signal?: AbortSignal) =>
  api<AdminUserDto>(`/api/v1/admin/users/${id}`, { signal });
export const fetchAdminEscortProfile = (id: number, signal?: AbortSignal) =>
  api<AdminEscortProfileDto>(`/api/v1/admin/users/${id}/profile/escort`, { signal });
export const updateAdminUser = (id: number, body: AdminUserUpdate) =>
  apiPatch<AdminUserDto>(`/api/v1/admin/users/${id}`, body);
export const deleteAdminUser = (id: number) => apiDelete<void>(`/api/v1/admin/users/${id}`);
export type AdminSanctionAction = 'suspension' | 'ban' | 'release';
export const changeAdminUserStatus = (id: number, action: AdminSanctionAction, reason: string) =>
  apiPost<unknown>(`/api/v1/admin/users/${id}/${action}`, { reason });
const dateQuery = (startDate: string, endDate: string) =>
  `?startDate=${encodeURIComponent(startDate)}&endDate=${encodeURIComponent(endDate)}`;
export const fetchAdminPaymentAnalytics = (start: string, end: string, signal?: AbortSignal) =>
  api<AdminPaymentAnalytics>(`/api/v1/admin/payments/analytics${dateQuery(start, end)}`, { signal });
export const fetchAdminDailyPayments = (start: string, end: string, signal?: AbortSignal) =>
  api<AdminPaymentDaily[]>(`/api/v1/admin/payments/analytics/daily${dateQuery(start, end)}`, { signal });
export const fetchAdminSettlementAnalytics = (start: string, end: string, signal?: AbortSignal) =>
  api<AdminSettlementAnalytics>(`/api/v1/admin/settlements/analytics${dateQuery(start, end)}`, { signal });


/** 관리자 결제·정산 목록: Spring Page 응답을 그대로 사용합니다. */
export type AdminPaymentStatus = 'READY' | 'IN_PROGRESS' | 'DONE' | 'CANCELED' | 'PARTIAL_CANCELED' | 'DELETED';
export type AdminPaymentRow = {
  id: number;
  amount: number;
  balanceAmount: number;
  hourlyPaySnapshot: number;
  hours: number;
  orderId: string | null;
  paymentStatus: AdminPaymentStatus;
  approvedAt: string | null;
  canceledAt: string | null;
  cancelReason: string | null;
  postId: number | null;
  postTitle: string | null;
  userId: number | null;
  name: string | null;
};
export type AdminSettlementStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type AdminSettlementRow = {
  id: number;
  payoutAmount: number;
  platformFee: number;
  penaltyAmount: number;
  status: AdminSettlementStatus;
  settledAt: string | null;
  applicationId: number | null;
  userId: number | null;
  name: string | null;
};

/** 백엔드 날짜 구간의 endDate는 해당 날짜 00시이므로 UI 종료일의 다음 날을 전달합니다. */
export function adminEndDateExclusive(date: string) {
  const next = new Date(`${date}T00:00:00Z`);
  next.setUTCDate(next.getUTCDate() + 1);
  return next.toISOString().slice(0, 10);
}
export function fetchAdminPaymentsPage(page: number, startDate: string, endDate: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: '20', startDate, endDate: adminEndDateExclusive(endDate) });
  return api<SpringPage<AdminPaymentRow>>(`/api/v1/admin/payments?${params}`, { signal });
}
/** 해당 API는 JSON body가 아닌 status 쿼리 파라미터를 받습니다. */
export function updateAdminPaymentStatus(paymentId: number, status: AdminPaymentStatus) {
  return api<void>(`/api/v1/admin/payments/${paymentId}/status?status=${encodeURIComponent(status)}`, { method: 'PATCH' });
}
export function fetchAdminSettlementsPage(page: number, startDate: string, endDate: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: '20', startDate, endDate: adminEndDateExclusive(endDate) });
  return api<SpringPage<AdminSettlementRow>>(`/api/v1/admin/settlements?${params}`, { signal });
}
/** POST /{id}는 실제 정산 요청을 시도하므로 확인 후 호출해야 합니다. */
export function requestAdminSettlement(settlementId: number) {
  return apiPost<void>(`/api/v1/admin/settlements/${settlementId}`);
}
