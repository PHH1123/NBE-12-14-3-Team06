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
  /** 내가 마지막으로 방을 연 뒤에 상대가 보낸 메시지 수 */
  unReadCount: number;
};

/** GET /api/v1/chat/{chatRoomId} 페이지의 한 줄 (백엔드 ChatMessageResponse) */
export type ChatMessageDto = {
  chatMessageId: number;
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
  /** 안 읽은 메시지 수. 0 이면 배지를 숨깁니다. */
  unreadCount: number;
};

/** 말풍선 하나 */
export type ChatMessage = {
  /** 백엔드 ChatMessage 의 id. React key 이자 과거 메시지를 붙일 때 중복 제거 기준입니다. */
  id: number;
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
