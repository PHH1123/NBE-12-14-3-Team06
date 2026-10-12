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
    key: messageKey(dto),
    senderId: dto.senderId,
    senderName: dto.senderName,
    content: dto.content,
    sentAt: dto.sentAt,
    type: dto.type,
    mine: dto.senderId !== partnerId,
  };
}

/**
 * 말풍선 목록의 React key.
 *
 * ⚠️ 백엔드 ChatMessageResponse 에 메시지 id 가 없어서 내용으로 키를 만듭니다.
 *    같은 사람이 같은 초에 똑같은 내용을 두 번 보내면 키가 겹칩니다(실제로는 드묾).
 *    응답에 id 가 추가되면 이 함수만 바꾸면 됩니다.
 */
export function messageKey(dto: ChatMessageDto): string {
  return `${dto.senderId}-${dto.sentAt}-${dto.content}`;
}
