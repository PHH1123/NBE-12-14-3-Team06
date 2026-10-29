export type AdminMemberRole = 'client' | 'escort' | 'admin';
export type AdminMemberStatus = 'active' | 'suspended' | 'banned' | 'withdrawn';

// AdminUsersView의 배지 색상 / 글자 크기 / 패딩
const roleMap = {
  client: { text: '의뢰인', bg: 'rgba(66, 107, 147, 0.10)', fg: '#426B93' },
  escort: { text: '동행 매니저', bg: 'rgba(47, 125, 91, 0.10)', fg: '#2F7D5B' },
  admin: { text: '관리자', bg: '#14243A', fg: '#FFFFFF' },
} as const;
const statusMap = {
  active: { text: '활성', bg: 'rgba(47, 125, 91, 0.10)', fg: '#2F7D5B' },
  suspended: { text: '기간 정지', bg: 'rgba(154, 107, 18, 0.15)', fg: '#9A6B12' },
  banned: { text: '영구 정지', bg: 'rgba(180, 65, 60, 0.10)', fg: '#B4413C' },
  withdrawn: { text: '탈퇴', bg: 'rgba(103, 113, 124, 0.15)', fg: '#555E67' },
} as const;

export function AdminRoleBadge({ role, compact = false }: { role: AdminMemberRole; compact?: boolean }) {
  const item = roleMap[role];
  return <span className={`inline-flex w-fit items-center rounded border-0 font-semibold whitespace-nowrap ${compact ? 'px-1.5 py-0.5 text-[11px] font-medium' : 'px-2 py-0.5 text-xs'}`}
    style={{ backgroundColor: item.bg, color: item.fg, border: 'none' }}>{item.text}</span>;
}

export function AdminStatusBadge({ status }: { status: AdminMemberStatus }) {
  const item = statusMap[status];
  return <span className={`inline-flex w-fit items-center rounded border-0 px-2 py-0.5 text-xs whitespace-nowrap ${status === 'active' || status === 'withdrawn' ? 'font-semibold' : 'font-bold'}`}
    style={{ backgroundColor: item.bg, color: item.fg, border: 'none' }}>{item.text}</span>;
}
