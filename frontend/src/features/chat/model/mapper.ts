import type { ChatMessage, ChatMessageDto, ChatRoomDto, ChatRoomSummary } from '../types';

/** 서버 채팅방 → 목록 한 칸 */
export function toChatRoomSummary(dto: ChatRoomDto): ChatRoomSummary {
  return {
    roomId: dto.chatRoomId,
    partnerId: dto.partnerId,
    partnerName: dto.partnerName,
    partnerRole: dto.partnerRole,
    lastMessage: dto.lastChatContent ?? '',
    lastSentAt: dto.lastChatSentAt,
    unreadCount: dto.unReadCount,
  };
}

/**
 * 서버 메시지 → 말풍선 하나.
 *
 * 1:1 방이라 참여자가 둘뿐이므로 "상대가 아니면 나"입니다.
 * 백엔드가 내 userId 를 따로 내려주지 않아도 이렇게 가려낼 수 있습니다.
 */
export function toChatMessage(dto: ChatMessageDto, partnerId: number): ChatMessage {
  return {
    id: dto.chatMessageId,
    senderId: dto.senderId,
    senderName: dto.senderName,
    content: dto.content,
    sentAt: dto.sentAt,
    type: dto.type,
    mine: dto.senderId !== partnerId,
  };
}

/**
 * 과거 메시지를 앞에 붙입니다.
 *
 * 서버가 offset 페이징(page=N)이라, 읽는 도중 새 메시지가 오면 창이 밀려서
 * 이미 받은 메시지가 다음 페이지에 또 딸려옵니다. id 로 걸러냅니다.
 * (메시지 삭제 기능이 없어 id 는 증가만 하므로, 밀림은 중복만 만들고 누락은 만들지 않습니다)
 */
export function prependOlder(older: ChatMessage[], current: ChatMessage[]): ChatMessage[] {
  const seen = new Set(current.map((message) => message.id));
  return [...older.filter((message) => !seen.has(message.id)), ...current];
}
