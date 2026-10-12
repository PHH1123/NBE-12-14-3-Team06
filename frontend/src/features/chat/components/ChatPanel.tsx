'use client';

import { useCallback, useEffect, useState } from 'react';
import { ChevronLeftIcon, CloseIcon } from '@/components/ui';
import { cn } from '@/lib/cn';
import { fetchChatMessages, fetchChatRooms } from '../api';
import { useChatSocket } from '../lib/useChatSocket';
import { prependOlder } from '../model/mapper';
import type { ChatMessage, ChatRoomSummary } from '../types';
import ChatComposer from './ChatComposer';
import ChatRoomList from './ChatRoomList';
import ChatRoomView from './ChatRoomView';

type Props = {
  /** 열어 둘 방. null 이면 채팅방 목록을 보여 줍니다. */
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
  | {
      status: 'ready';
      messages: ChatMessage[];
      /** 지금까지 불러온 가장 오래된 페이지 번호 (0 = 최신 묶음) */
      page: number;
      /** 더 불러올 과거 메시지가 남았는지 */
      hasMore: boolean;
    };

/** key 가 지금 보는 방과 다르면 아직 그 방의 결과가 안 온 것(=로딩)으로 봅니다. */
type MessagesState = { key?: number; data?: MessagesResult };

const ICON_BUTTON =
  'grid size-8 shrink-0 place-items-center rounded-full text-brand transition-colors hover:bg-line-soft';

/**
 * 우하단에 떠 있는 채팅 패널. 목록 ↔ 방을 한 패널 안에서 오갑니다.
 *
 * 조회는 REST(GET /api/v1/chat …), 송수신은 STOMP 입니다.
 * 보낸 메시지도 서버가 토픽으로 되돌려주므로, 화면에 미리 넣지 않고 수신 쪽에서 한 번만 반영합니다.
 */
export default function ChatPanel({ roomId, onSelectRoom, onClose }: Props) {
  const [roomsState, setRoomsState] = useState<RoomsState>({ status: 'loading' });
  const [messagesState, setMessagesState] = useState<MessagesState>({});
  const [loadingOlder, setLoadingOlder] = useState(false);

  // Esc 로 패널을 닫습니다. 방을 보고 있으면 먼저 목록으로 돌아갑니다.
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      if (roomId === null) onClose();
      else onSelectRoom(null);
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [roomId, onClose, onSelectRoom]);

  // 패널이 열릴 때 방 목록을 한 번 불러옵니다. (패널은 열렸을 때만 렌더링됩니다)
  useEffect(() => {
    let ignore = false;

    fetchChatRooms()
      .then((rooms) => !ignore && setRoomsState({ status: 'ready', rooms }))
      .catch((error: unknown) =>
        !ignore &&
        setRoomsState({
          status: 'error',
          message: error instanceof Error ? error.message : '채팅방 목록을 불러오지 못했습니다.',
        }),
      );

    return () => {
      ignore = true;
    };
  }, []);

  const room = roomId === null || roomsState.status !== 'ready'
    ? undefined
    : roomsState.rooms.find((item) => item.roomId === roomId);

  // 방을 열 때마다 그 방의 메시지를 불러옵니다.
  // partnerId 가 있어야 내 메시지를 가려낼 수 있어서 방 정보를 먼저 찾은 뒤에 호출합니다.
  const activeRoomId = room?.roomId;
  const activePartnerId = room?.partnerId;

  useEffect(() => {
    if (activeRoomId === undefined || activePartnerId === undefined) return;
    let ignore = false;

    fetchChatMessages(activeRoomId, activePartnerId)
      .then((page) => {
        if (ignore) return;
        setMessagesState({
          key: activeRoomId,
          data: { status: 'ready', messages: page.messages, page: 0, hasMore: page.hasMore },
        });
        // 이 조회가 서버에서 읽음 처리까지 하므로(ChatService.getMessages) 배지도 같이 지웁니다.
        setRoomsState((prev) =>
          prev.status === 'ready'
            ? {
                status: 'ready',
                rooms: prev.rooms.map((item) =>
                  item.roomId === activeRoomId ? { ...item, unreadCount: 0 } : item,
                ),
              }
            : prev,
        );
      })
      .catch((error: unknown) =>
        !ignore &&
        setMessagesState({
          key: activeRoomId,
          data: {
            status: 'error',
            message: error instanceof Error ? error.message : '메시지를 불러오지 못했습니다.',
          },
        }),
      );

    return () => {
      ignore = true;
    };
  }, [activeRoomId, activePartnerId]);

  // 다른 방으로 옮기면 key 가 어긋나므로 자연스럽게 로딩 상태가 됩니다.
  const messages = messagesState.key === activeRoomId ? messagesState.data : undefined;

  /** STOMP 로 새 메시지가 왔을 때 — 지금 보고 있는 방이면 말풍선을, 목록이면 마지막 대화를 갱신합니다. */
  const handleIncoming = useCallback((message: ChatMessage) => {
    setMessagesState((prev) =>
      prev.data?.status === 'ready'
        ? { key: prev.key, data: { ...prev.data, messages: [...prev.data.messages, message] } }
        : prev,
    );
    setRoomsState((prev) =>
      prev.status === 'ready'
        ? {
            status: 'ready',
            rooms: prev.rooms.map((item) =>
              item.roomId === activeRoomId
                ? { ...item, lastMessage: message.content, lastSentAt: message.sentAt }
                : item,
            ),
          }
        : prev,
    );
  }, [activeRoomId]);

  /**
   * 위로 스크롤했을 때 과거 메시지 한 페이지를 더 붙입니다.
   *
   * 서버가 offset 페이징이라, 읽는 동안 새 메시지가 오면 창이 밀려 이미 받은 메시지가
   * 다음 페이지에 또 딸려옵니다 — prependOlder 가 id 로 걸러냅니다.
   */
  const loadOlder = useCallback(() => {
    if (activeRoomId === undefined || activePartnerId === undefined || loadingOlder) return;
    if (messages?.status !== 'ready' || !messages.hasMore) return;

    const nextPage = messages.page + 1;
    setLoadingOlder(true);

    fetchChatMessages(activeRoomId, activePartnerId, nextPage)
      .then((older) =>
        setMessagesState((prev) =>
          // 불러오는 사이에 방을 옮겼으면 버립니다.
          prev.key !== activeRoomId || prev.data?.status !== 'ready'
            ? prev
            : {
                key: prev.key,
                data: {
                  status: 'ready',
                  messages: prependOlder(older.messages, prev.data.messages),
                  page: nextPage,
                  hasMore: older.hasMore,
                },
              },
        ),
      )
      // 실패하면 다음 스크롤에 다시 시도할 수 있게 상태만 풀어 둡니다(이미 보던 메시지는 그대로).
      .catch(() => undefined)
      .finally(() => setLoadingOlder(false));
  }, [activeRoomId, activePartnerId, loadingOlder, messages]);

  // 방 목록을 불러온 뒤에 연결합니다 — 그 호출이 만료된 액세스 토큰을 갱신해 주기 때문입니다.
  // (WebSocket 핸드셰이크는 REST 와 달리 401 후 재발급·재시도를 할 수 없습니다)
  const socket = useChatSocket({
    enabled: roomsState.status === 'ready',
    roomId: activeRoomId ?? null,
    partnerId: activePartnerId ?? null,
    onMessage: handleIncoming,
  });

  return (
    <div
      role="dialog"
      aria-label={room ? `${room.partnerName}님과의 채팅` : '채팅'}
      className={cn(
        'fixed right-4 bottom-[88px] left-4 z-50 flex flex-col overflow-hidden sm:left-auto sm:w-[360px]',
        'h-[min(560px,calc(100dvh-7.5rem))] rounded-[24px] border border-line bg-white shadow-card',
      )}
    >
      <header className="flex shrink-0 items-center gap-1.5 border-b border-line px-3 py-3">
        {room ? (
          <>
            <button type="button" onClick={() => onSelectRoom(null)} className={ICON_BUTTON} aria-label="채팅 목록으로">
              <ChevronLeftIcon className="size-[18px]" />
            </button>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-[14.5px] font-semibold text-brand">{room.partnerName}</span>
            </span>
          </>
        ) : (
          <h2 className="flex-1 pl-1.5 text-[15px] font-semibold text-brand">채팅</h2>
        )}
        <button type="button" onClick={onClose} className={ICON_BUTTON} aria-label="채팅 닫기">
          <CloseIcon className="size-[18px]" />
        </button>
      </header>

      {room ? (
        <>
          {messages?.status === 'ready' ? (
            <ChatRoomView
              messages={messages.messages}
              hasMore={messages.hasMore}
              loadingOlder={loadingOlder}
              onLoadOlder={loadOlder}
            />
          ) : (
            <PanelNotice
              message={messages?.status === 'error' ? messages.message : '메시지를 불러오는 중입니다…'}
              tone={messages?.status === 'error' ? 'error' : 'muted'}
            />
          )}
          {socket.status !== 'connected' && (
            <p
              role={socket.status === 'error' ? 'alert' : undefined}
              className="shrink-0 border-t border-line bg-line-soft px-4 py-2 text-center text-[11.5px] text-brand-muted"
            >
              {socket.status === 'error' ? socket.error : '채팅 서버에 연결하는 중입니다…'}
            </p>
          )}
          <ChatComposer onSend={socket.send} disabled={socket.status !== 'connected'} />
        </>
      ) : roomsState.status === 'ready' ? (
        <ChatRoomList rooms={roomsState.rooms} onSelect={onSelectRoom} />
      ) : (
        <PanelNotice
          message={roomsState.status === 'loading' ? '채팅방을 불러오는 중입니다…' : roomsState.message}
          tone={roomsState.status === 'error' ? 'error' : 'muted'}
        />
      )}
    </div>
  );
}

function PanelNotice({ message, tone }: { message: string; tone: 'muted' | 'error' }) {
  return (
    <p
      role={tone === 'error' ? 'alert' : undefined}
      className={cn(
        'flex flex-1 items-center justify-center px-6 text-center text-[13px] leading-[1.5]',
        tone === 'error' ? 'font-medium text-brand' : 'text-brand-muted',
      )}
    >
      {message}
    </p>
  );
}
