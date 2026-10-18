import { api, type SpringPage } from '@/lib/api';

/** 백엔드 AdminUserResponse. 회원 상태(userStatus)는 현재 반환되지 않습니다. */
export type AdminUserDto = {
  id: number;
  username: string;
  email: string;
  name: string;
  role: 'CLIENT' | 'ESCORT' | 'ADMIN';
  phoneNum: string | null;
  createdAt: string;
  deletedAt: string | null;
  deleted: boolean;
  // 향후 백엔드 응답이 확대될 때만 활용합니다. 현재 응답에서 임의 추론하지 않습니다.
  userStatus?: 'ACTIVE' | 'SUSPENDED' | 'BANNED';
};

/** GET /api/v1/admin/users?page&size — 쿠키 인증 및 RsData 해제는 공용 api에서 처리 */
export function fetchAdminUsersPage(page = 0, size = 100, signal?: AbortSignal) {
  return api<SpringPage<AdminUserDto>>(
    `/api/v1/admin/users?page=${page}&size=${size}`,
    { signal },
  );
}

/** 검색/역할 필터가 전체 회원에 적용되도록 서버의 모든 페이지를 조회합니다. */
export async function fetchAllAdminUsers(signal?: AbortSignal): Promise<AdminUserDto[]> {
  const size = 100;
  const first = await fetchAdminUsersPage(0, size, signal);
  if (!Array.isArray(first.content) || !Number.isSafeInteger(first.totalPages) || first.totalPages < 0) {
    throw new Error('회원 목록 응답 형식을 확인할 수 없습니다.');
  }
  const result = [...first.content];
  // 큰 회원 목록도 동시에 과도한 요청이 발생하지 않도록 최대 4페이지씩 조회
  for (let start = 1; start < first.totalPages; start += 4) {
    const pages = await Promise.all(
      Array.from({ length: Math.min(4, first.totalPages - start) }, (_, offset) =>
        fetchAdminUsersPage(start + offset, size, signal),
      ),
    );
    for (const page of pages) {
      if (!Array.isArray(page.content)) throw new Error('회원 목록 응답 형식을 확인할 수 없습니다.');
      result.push(...page.content);
    }
  }
  return result;
}
