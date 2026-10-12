/** 백엔드 MessageType — ENTER/LEAVE 는 "○○님이 채팅방을 개설했습니다" 같은 안내 줄로 보여 줍니다. */
export type MessageType = 'ENTER' | 'LEAVE' | 'TALK';

/** 백엔드 Role */
export type ChatPartnerRole = 'ADMIN' | 'CLIENT' | 'ESCORT';

/**
 * GET /api/v1/chat 목록의 한 줄, POST /api/v1/chat 의 응답 (백엔드 ChatRoomResponse).
 *
 * partner* 는 "보는 사람이 아닌 쪽"을 서버가 골라 담아 줍니다.
 * 그래서 내 userId 를 몰라도 `senderId !== partnerId` 로 내 메시지를 가려낼 수 있습니다.
 */
export type ChatRoomDto = {
  chatRoomId: number;
  partnerId: number;
  partnerName: string;
  partnerRole: ChatPartnerRole;
  /** 아직 대화가 없으면 null */
  lastChatContent: string | null;
  lastChatSentAt: string | null;
};

/** GET /api/v1/chat/{chatRoomId} 페이지의 한 줄 (백엔드 ChatMessageResponse) */
export type ChatMessageDto = {
  senderId: number;
  senderName: string;
  content: string;
  sentAt: string;
  type: MessageType;
};

/** STOMP 로 /app/chat/{chatRoomId} 에 보낼 본문 (백엔드 ChatMessageDto) */
export type ChatSendRequest = {
  content: string;
};

/** 목록에 보여 줄 채팅방 한 칸 */
export type ChatRoomSummary = {
  roomId: number;
  /** 상대 userId — 말풍선 좌/우를 가르는 기준입니다. */
  partnerId: number;
  partnerName: string;
  /** 상대 역할 — 이름 옆 꼬리표("동행 매니저"·"의뢰인")로 씁니다. */
  partnerRole: ChatPartnerRole;
  /** 마지막 대화. 아직 없으면 빈 문자열 */
  lastMessage: string;
  lastSentAt: string | null;
};

/** 말풍선 하나 */
export type ChatMessage = {
  /** 백엔드 ChatMessageResponse 에 id 가 없어서 화면에서만 쓰는 key 입니다. */
  key: string;
  senderId: number;
  senderName: string;
  content: string;
  sentAt: string;
  type: MessageType;
  /** 내가 보낸 메시지인지 — 말풍선을 오른쪽에 붙입니다. */
  mine: boolean;
  /** 아직 서버 응답을 못 받은 낙관적 메시지 */
  pending?: boolean;
};
