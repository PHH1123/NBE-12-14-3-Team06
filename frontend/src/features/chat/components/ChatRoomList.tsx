'use client';

import { useMemo, useState } from 'react';
import { MessageCircle, Search, X } from 'lucide-react';
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

/** 검색은 이미 조회한 실제 채팅방 목록만 필터링합니다. */
export default function ChatRoomList({ rooms, onSelect }: Props) {
  const [query, setQuery] = useState('');
  const filtered = useMemo(() => {
    const term = query.trim().toLocaleLowerCase();
    if (!term) return rooms;
    return rooms.filter((room) =>
      room.partnerName.toLocaleLowerCase().includes(term) ||
      ROLE_LABEL[room.partnerRole].toLocaleLowerCase().includes(term) ||
      room.lastMessage.toLocaleLowerCase().includes(term),
    );
  }, [rooms, query]);

  return (
    <div className="flex min-h-0 flex-1 flex-col bg-white">
      <div className="shrink-0 border-b border-[#E1E5E8] px-5 pb-4 pt-4">
        <div className="mb-3 flex items-baseline justify-between gap-3">
          <h3 className="text-[14px] font-bold tracking-[-0.02em] text-[#14243A]">대화 목록</h3>
          <span className="text-[11px] font-medium tabular-nums text-[#8B949E]">{rooms.length}개의 대화</span>
        </div>
        <div className="flex h-10 items-center gap-2.5 rounded-xl border border-[#E1E5E8] bg-[#F7F8F8] px-3 transition-[background-color,border-color] duration-200 focus-within:border-[#426B93] focus-within:bg-white">
          <Search className="h-[17px] w-[17px] shrink-0 text-[#8B949E]" aria-hidden="true" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="이름·최근 메시지 검색"
            aria-label="채팅방 검색"
            className="min-w-0 flex-1 bg-transparent text-[12.5px] text-[#14243A] outline-none placeholder:text-[#9AA3AC]"
          />
          {query && (
            <button type="button" onClick={() => setQuery('')} aria-label="검색어 지우기" className="rounded-md p-1 text-[#8B949E] transition-colors hover:bg-[#EDF0F2] hover:text-[#14243A]">
              <X className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          )}
        </div>
      </div>

      {rooms.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center px-9 text-center">
          <div className="mb-4 grid h-14 w-14 place-items-center rounded-2xl bg-[#EDF2F6] text-[#426B93]">
            <MessageCircle className="h-6 w-6" strokeWidth={1.6} aria-hidden="true" />
          </div>
          <p className="text-sm font-bold text-[#14243A]">아직 대화가 없어요</p>
          <p className="mt-2 max-w-[240px] text-xs leading-[1.7] text-[#67717C]">
            공고 또는 동행 현황에서 상대방에게 메시지를 보내면 이곳에 대화가 표시됩니다.
          </p>
        </div>
      ) : filtered.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center px-8 text-center">
          <Search className="mb-3 h-6 w-6 text-[#A6B0B9]" strokeWidth={1.5} aria-hidden="true" />
          <p className="text-[13px] font-semibold text-[#14243A]">검색 결과가 없어요</p>
          <p className="mt-1.5 text-xs text-[#67717C]">다른 검색어로 다시 찾아보세요.</p>
          <button type="button" onClick={() => setQuery('')} className="mt-3 text-xs font-semibold text-[#426B93] hover:text-[#14243A]">검색 초기화</button>
        </div>
      ) : (
        <ul className="min-h-0 flex-1 overflow-y-auto overscroll-contain pb-2">
          {filtered.map((room) => (
            <li key={room.roomId}>
              <button
                type="button"
                onClick={() => onSelect(room.roomId)}
                className="group flex w-full items-center gap-3.5 border-b border-[#F0F2F3] px-5 py-4 text-left transition-colors duration-200 hover:bg-[#F3F7FA] focus-visible:bg-[#F3F7FA] focus-visible:outline-2 focus-visible:outline-inset focus-visible:outline-[#426B93]"
              >
                <span aria-hidden="true" className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-[#14243A] text-base font-bold text-white">
                  {room.partnerName.slice(0, 1)}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="mb-1 flex min-w-0 items-center gap-2">
                    <span className="truncate text-[13.5px] font-bold text-[#14243A]">{room.partnerName}</span>
                    <span className="shrink-0 rounded-md bg-[#EDF2F6] px-1.5 py-[3px] text-[10px] font-semibold leading-none text-[#426B93]">{ROLE_LABEL[room.partnerRole]}</span>
                  </span>
                  <span className={`block truncate text-[12px] leading-5 ${room.unreadCount > 0 ? 'font-semibold text-[#344055]' : 'text-[#7B858F]'}`}>
                    {room.lastMessage || '아직 메시지가 없어요'}
                  </span>
                </span>
                <span className="flex shrink-0 flex-col items-end gap-2 self-start pt-[3px]">
                  <time className="text-[10px] tabular-nums text-[#8B949E]" dateTime={room.lastSentAt ?? undefined}>{formatRoomTime(room.lastSentAt)}</time>
                  {room.unreadCount > 0 && (
                    <span aria-label={`안 읽은 메시지 ${room.unreadCount}개`} className="grid min-h-[19px] min-w-[19px] place-items-center rounded-full bg-[#426B93] px-1.5 text-[10px] font-bold text-white">
                      {room.unreadCount > 99 ? '99+' : room.unreadCount}
                    </span>
                  )}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="shrink-0 border-t border-[#E1E5E8] px-5 py-2.5 text-center text-[10.5px] text-[#8B949E]">
        대화 내용은 상대방과의 소통을 위해 사용됩니다.
      </div>
    </div>
  );
}
