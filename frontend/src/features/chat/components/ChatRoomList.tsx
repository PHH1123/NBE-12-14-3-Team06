import { cn } from '@/lib/cn';
import { formatRoomTime } from '../lib/date';
import type { ChatRoomSummary } from '../types';

type Props = {
  rooms: ChatRoomSummary[];
  onSelect: (roomId: number) => void;
};

const ROLE_LABEL: Record<ChatRoomSummary['partnerRole'], string> = {
  ADMIN: '관리자',
  CLIENT: '의뢰인',
  ESCORT: '동행 매니저',
};

/** 패널의 첫 화면 — 내가 참여한 채팅방 목록 */
export default function ChatRoomList({ rooms, onSelect }: Props) {
  if (rooms.length === 0) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-1.5 px-6 text-center">
        <p className="text-[13.5px] font-semibold text-brand">아직 대화가 없습니다</p>
        <p className="text-[12px] leading-[1.5] text-brand-muted">
          동행 매니저나 의뢰인 화면의 &lsquo;연락하기&rsquo;를 누르면 대화가 시작됩니다.
        </p>
      </div>
    );
  }

  return (
    <ul className="flex-1 overflow-y-auto overscroll-contain">
      {rooms.map((room) => (
        <li key={room.roomId}>
          <button
            type="button"
            onClick={() => onSelect(room.roomId)}
            className="flex w-full items-center gap-3 border-b border-line-soft px-4 py-3 text-left transition-colors hover:bg-line-soft"
          >
            {/* 프로필 이미지가 없는 동안은 이름 첫 글자로 대신합니다. */}
            <span
              aria-hidden="true"
              className="grid size-10 shrink-0 place-items-center rounded-full bg-line-soft text-[15px] font-semibold text-brand"
            >
              {room.partnerName.slice(0, 1)}
            </span>
            <span className="min-w-0 flex-1">
              <span className="flex items-baseline gap-1.5">
                <span className="truncate text-[13.5px] font-semibold text-brand">{room.partnerName}</span>
                <span className="shrink-0 text-[11px] text-brand-muted">{ROLE_LABEL[room.partnerRole]}</span>
              </span>
              <span
                className={cn(
                  'mt-0.5 block truncate text-[12px]',
                  room.lastMessage ? 'text-brand-muted' : 'text-brand-muted italic',
                )}
              >
                {room.lastMessage || '대화를 시작해보세요'}
              </span>
            </span>
            <span className="shrink-0 self-start pt-0.5 text-[10.5px] text-brand-muted">
              {formatRoomTime(room.lastSentAt)}
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}
