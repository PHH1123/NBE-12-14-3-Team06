import { api, apiPost, type SpringPage } from '@/lib/api';
import { toChatMessage, toChatRoomSummary } from './model/mapper';
import type { ChatMessage, ChatMessageDto, ChatRoomDto, ChatRoomSummary } from './types';

/** 메시지 한 페이지 — 화면이 쓰기 좋게 오래된 순으로 뒤집어 돌려줍니다. */
export type ChatMessagePage = {
  /** 위(오래된 것) → 아래(최신) 순서 */
  messages: ChatMessage[];
  /** 더 불러올 과거 메시지가 남았는지 */
  hasMore: boolean;
};

/**
 * 내 채팅방 목록 — GET /api/v1/chat (로그인 쿠키 필요)
 * 상대 정보(partnerId·partnerName·partnerRole)는 서버가 "나 아닌 쪽"으로 골라 담아 줍니다.
 */
export async function fetchChatRooms(): Promise<ChatRoomSummary[]> {
  const rooms = await api<ChatRoomDto[]>('/api/v1/chat');
  return rooms.map(toChatRoomSummary);
}

/**
 * 채팅방 열기 — POST /api/v1/chat?user-id={상대 userId}
 *
 * 이미 방이 있으면 서버가 그 방을 그대로 돌려줍니다(멱등). "연락하기"에서 바로 쓰면 됩니다.
 */
export async function openChatRoom(partnerUserId: number): Promise<ChatRoomSummary> {
  const room = await apiPost<ChatRoomDto>(`/api/v1/chat?user-id=${partnerUserId}`);
  return toChatRoomSummary(room);
}

/**
 * 채팅 메시지 조회 — GET /api/v1/chat/{chatRoomId}?page=&size=
 *
 * 서버는 id 내림차순(최신 먼저)으로 주므로, 화면에 쌓기 좋게 뒤집어서 돌려줍니다.
 * page 0 이 가장 최신 묶음이고, 숫자가 커질수록 과거로 거슬러 올라갑니다.
 */
export async function fetchChatMessages(
  roomId: number,
  partnerId: number,
  page = 0,
  size = 20,
): Promise<ChatMessagePage> {
  const data = await api<SpringPage<ChatMessageDto>>(`/api/v1/chat/${roomId}?page=${page}&size=${size}`);

  return {
    messages: data.content.map((dto) => toChatMessage(dto, partnerId)).reverse(),
    hasMore: data.number + 1 < data.totalPages,
  };
}
