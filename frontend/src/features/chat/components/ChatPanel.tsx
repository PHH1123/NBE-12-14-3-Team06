'use client';

import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, MessageCircle, MoreHorizontal, X } from 'lucide-react';
import { fetchChatMessages, fetchChatRooms, leaveChatRoom } from '../api';
import { useChatSocket } from '../lib/useChatSocket';
import { prependOlder } from '../model/mapper';
import type { ChatMessage, ChatRoomSummary } from '../types';
import ChatComposer from './ChatComposer';
import ChatRoomList from './ChatRoomList';
import ChatRoomView from './ChatRoomView';

type Props = {
  roomId: number | null;
  onSelectRoom: (roomId: number | null) => void;
  onClose: () => void;
};

type RoomsState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; rooms: ChatRoomSummary[] };

type MessagesResult =
  | { status: 'error'; message: string }
  | { status: 'ready'; messages: ChatMessage[]; page: number; hasMore: boolean };

type MessagesState = { key?: number; data?: MessagesResult };
type LeaveState = { roomId?: number; phase?: 'confirm' | 'leaving' | 'error'; message?: string };

const ICON_BUTTON = 'grid h-9 w-9 shrink-0 place-items-center rounded-xl text-white/90 transition-colors duration-200 hover:bg-white/15 hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white';
const ROLE_LABEL: Record<ChatRoomSummary['partnerRole'], string> = {
  ADMIN: '관리자',
  CLIENT: '의뢰인',
  ESCORT: '동행 매니저',
};

/** 목록/메시지 REST, 읽음 처리, STOMP 구독·전송, 나가기 확인을 기존 로직 그대로 사용합니다. */
export default function ChatPanel({ roomId, onSelectRoom, onClose }: Props) {
  const [roomsState, setRoomsState] = useState<RoomsState>({ status: 'loading' });
  const [messagesState, setMessagesState] = useState<MessagesState>({});
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [leaveState, setLeaveState] = useState<LeaveState>({});
  const [actionsOpen, setActionsOpen] = useState(false);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      if (roomId === null) onClose();
      else onSelectRoom(null);
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [roomId, onClose, onSelectRoom]);

  useEffect(() => {
    let ignore = false;
    fetchChatRooms()
      .then((rooms) => !ignore && setRoomsState({ status: 'ready', rooms }))
      .catch((error: unknown) => !ignore && setRoomsState({
        status: 'error',
        message: error instanceof Error ? error.message : '채팅방 목록을 불러오지 못했습니다.',
      }));
    return () => { ignore = true; };
  }, []);

  const room = roomId === null || roomsState.status !== 'ready'
    ? undefined
    : roomsState.rooms.find((item) => item.roomId === roomId);
  const activeRoomId = room?.roomId;
  const activePartnerId = room?.partnerId;

  useEffect(() => {
    if (activeRoomId === undefined || activePartnerId === undefined) return;
    let ignore = false;
    fetchChatMessages(activeRoomId, activePartnerId)
      .then((page) => {
        if (ignore) return;
        setMessagesState({ key: activeRoomId, data: { status: 'ready', messages: page.messages, page: 0, hasMore: page.hasMore } });
        setRoomsState((prev) => prev.status === 'ready'
          ? { status: 'ready', rooms: prev.rooms.map((item) => item.roomId === activeRoomId ? { ...item, unreadCount: 0 } : item) }
          : prev,
        );
      })
      .catch((error: unknown) => !ignore && setMessagesState({
        key: activeRoomId,
        data: { status: 'error', message: error instanceof Error ? error.message : '메시지를 불러오지 못했습니다.' },
      }));
    return () => { ignore = true; };
  }, [activeRoomId, activePartnerId]);

  const messages = messagesState.key === activeRoomId ? messagesState.data : undefined;

  const handleIncoming = useCallback((message: ChatMessage) => {
    setMessagesState((prev) => prev.data?.status === 'ready' && prev.key === activeRoomId
      ? { key: prev.key, data: { ...prev.data, messages: prev.data.messages.some((item) => item.id === message.id) ? prev.data.messages : [...prev.data.messages, message] } }
      : prev,
    );
    setRoomsState((prev) => prev.status === 'ready'
      ? { status: 'ready', rooms: prev.rooms.map((item) => item.roomId === activeRoomId
        ? { ...item, lastMessage: message.content, lastSentAt: message.sentAt }
        : item,
      ) }
      : prev,
    );
  }, [activeRoomId]);

  const loadOlder = useCallback(() => {
    if (activeRoomId === undefined || activePartnerId === undefined || loadingOlder) return;
    if (messages?.status !== 'ready' || !messages.hasMore) return;
    const nextPage = messages.page + 1;
    setLoadingOlder(true);
    fetchChatMessages(activeRoomId, activePartnerId, nextPage)
      .then((older) => setMessagesState((prev) => prev.key !== activeRoomId || prev.data?.status !== 'ready'
        ? prev
        : { key: prev.key, data: { status: 'ready', messages: prependOlder(older.messages, prev.data.messages), page: nextPage, hasMore: older.hasMore } },
      ))
      .catch(() => undefined)
      .finally(() => setLoadingOlder(false));
  }, [activeRoomId, activePartnerId, loadingOlder, messages]);

  const leave = leaveState.roomId === activeRoomId ? leaveState.phase : undefined;
  const confirmLeave = useCallback(() => {
    if (activeRoomId === undefined) return;
    setLeaveState({ roomId: activeRoomId, phase: 'leaving' });
    leaveChatRoom(activeRoomId)
      .then(() => {
        setRoomsState((prev) => prev.status === 'ready'
          ? { status: 'ready', rooms: prev.rooms.filter((item) => item.roomId !== activeRoomId) }
          : prev,
        );
        setLeaveState({});
        setActionsOpen(false);
        onSelectRoom(null);
      })
      .catch((error: unknown) => setLeaveState({
        roomId: activeRoomId,
        phase: 'error',
        message: error instanceof Error ? error.message : '채팅방을 나가지 못했습니다.',
      }));
  }, [activeRoomId, onSelectRoom]);

  const socket = useChatSocket({
    enabled: roomsState.status === 'ready',
    roomId: activeRoomId ?? null,
    partnerId: activePartnerId ?? null,
    onMessage: handleIncoming,
  });

  const goBack = () => { setActionsOpen(false); setLeaveState({}); onSelectRoom(null); };

  return (
    <section
      id="gaji-chat-panel"
      role="dialog"
      aria-label={room ? `${room.partnerName}님과의 채팅` : '채팅'}
      className="fixed bottom-[90px] left-3 right-3 z-50 flex h-[min(620px,calc(100dvh-112px))] min-h-0 flex-col overflow-hidden rounded-2xl border border-[#E1E5E8] bg-white shadow-[0_16px_54px_rgba(20,36,58,0.18)] sm:left-auto sm:right-5 sm:h-[min(580px,78dvh,calc(100dvh_-_112px))] sm:w-[400px]"
    >
      <header className="relative flex shrink-0 items-center gap-2 bg-[#14243A] px-4 py-4 text-white">
        {room ? (
          <>
            <button type="button" onClick={goBack} aria-label="채팅 목록으로" className={ICON_BUTTON}>
              <ArrowLeft className="h-[19px] w-[19px]" strokeWidth={1.8} aria-hidden="true" />
            </button>
            <span aria-hidden="true" className="grid h-10 w-10 shrink-0 place-items-center rounded-xl border border-white/20 bg-[#344055] text-[15px] font-bold text-white">
              {room.partnerName.slice(0, 1)}
            </span>
            <div className="min-w-0 flex-1">
              <h2 className="truncate text-[14px] font-bold tracking-[-0.015em]">{room.partnerName}</h2>
              <p className="mt-0.5 text-[11px] text-[#BFCBD6]">{ROLE_LABEL[room.partnerRole]} · 1:1 대화</p>
            </div>
            <div className="relative">
              <button type="button" onClick={() => setActionsOpen((open) => !open)} aria-label="채팅방 메뉴" aria-expanded={actionsOpen} aria-haspopup="menu" className={ICON_BUTTON}>
                <MoreHorizontal className="h-[19px] w-[19px]" aria-hidden="true" />
              </button>
              {actionsOpen && (
                <div role="menu" className="absolute right-0 top-[calc(100%+8px)] z-10 min-w-[138px] rounded-xl border border-[#E1E5E8] bg-white p-1.5 shadow-[0_8px_26px_rgba(20,36,58,0.14)]">
                  <button type="button" role="menuitem" onClick={() => { setActionsOpen(false); setLeaveState({ roomId: room.roomId, phase: 'confirm' }); }} className="w-full rounded-lg px-3 py-2.5 text-left text-xs font-semibold text-[#B4413C] transition-colors hover:bg-[#FDF2F1]">
                    채팅방 나가기
                  </button>
                </div>
              )}
            </div>
          </>
        ) : (
          <>
            <div className="grid h-10 w-10 place-items-center rounded-xl border border-white/15 bg-white/10">
              <MessageCircle className="h-5 w-5" strokeWidth={1.8} aria-hidden="true" />
            </div>
            <div className="min-w-0 flex-1">
              <h2 className="text-[15px] font-bold tracking-[-0.02em]">GAJI 메시지</h2>
              <p className="mt-0.5 text-[11px] text-[#BFCBD6]">편안한 동행을 위한 대화</p>
            </div>
          </>
        )}
        <button type="button" onClick={onClose} aria-label="채팅 닫기" className={ICON_BUTTON}>
          <X className="h-[19px] w-[19px]" strokeWidth={1.8} aria-hidden="true" />
        </button>
      </header>

      {room && leave && (
        <div className="shrink-0 border-b border-[#E1E5E8] bg-[#F7F8F8] px-5 py-4">
          <p role={leave === 'error' ? 'alert' : undefined} className="text-xs leading-[1.7] text-[#344055]">
            {leave === 'error' ? leaveState.message : leave === 'leaving' ? '채팅방에서 나가는 중입니다…' : '채팅방을 나가시겠어요? 상대방에게 나갔다는 안내가 남습니다.'}
          </p>
          <div className="mt-3 flex justify-end gap-2">
            <button type="button" onClick={() => setLeaveState({})} disabled={leave === 'leaving'} className="rounded-lg border border-[#E1E5E8] bg-white px-3.5 py-2 text-[11px] font-semibold text-[#14243A] transition-colors hover:bg-[#EDF0F2] disabled:opacity-50">취소</button>
            <button type="button" onClick={confirmLeave} disabled={leave === 'leaving'} className="rounded-lg bg-[#14243A] px-3.5 py-2 text-[11px] font-semibold text-white transition-colors hover:bg-[#426B93] disabled:opacity-50">{leave === 'error' ? '다시 시도' : '나가기'}</button>
          </div>
        </div>
      )}

      {room ? (
        <>
          {messages?.status === 'ready' ? (
            <ChatRoomView messages={messages.messages} hasMore={messages.hasMore} loadingOlder={loadingOlder} onLoadOlder={loadOlder} />
          ) : (
            <PanelNotice message={messages?.status === 'error' ? messages.message : '메시지를 불러오는 중입니다…'} tone={messages?.status === 'error' ? 'error' : 'muted'} />
          )}
          {socket.status !== 'connected' && (
            <div role={socket.status === 'error' ? 'alert' : 'status'} className="shrink-0 border-t border-[#E1E5E8] bg-[#EDF2F6] px-4 py-2 text-center text-[11px] text-[#426B93]">
              {socket.status === 'error' ? socket.error : '채팅 서버에 연결 중입니다…'}
            </div>
          )}
          <ChatComposer onSend={socket.send} disabled={socket.status !== 'connected' || messages?.status !== 'ready' || leave === 'leaving'} />
        </>
      ) : roomsState.status === 'ready' ? (
        <ChatRoomList rooms={roomsState.rooms} onSelect={(id) => { setActionsOpen(false); setLeaveState({}); onSelectRoom(id); }} />
      ) : (
        <PanelNotice message={roomsState.status === 'loading' ? '채팅방을 불러오는 중입니다…' : roomsState.message} tone={roomsState.status === 'error' ? 'error' : 'muted'} />
      )}
    </section>
  );
}

function PanelNotice({ message, tone }: { message: string; tone: 'muted' | 'error' }) {
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className="flex flex-1 flex-col items-center justify-center bg-[#F7F8F8] px-6 text-center">
      <MessageCircle className="mb-3 h-6 w-6 text-[#A6B0B9]" strokeWidth={1.5} aria-hidden="true" />
      <p className={`text-[12px] leading-[1.7] ${tone === 'error' ? 'font-semibold text-[#B4413C]' : 'text-[#67717C]'}`}>{message}</p>
    </div>
  );
}
