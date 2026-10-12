/**
 * chat 도메인의 공개 API.
 * 이 도메인 바깥(app/, 다른 features/)에서는 반드시 여기를 통해서만 import 합니다.
 */
export { ChatWidgetProvider, useChatWidget } from './lib/ChatWidgetProvider';
export { default as ChatPanel } from './components/ChatPanel';
export { default as ChatLauncher } from './components/ChatLauncher';
export { fetchChatRooms, fetchChatMessages, openChatRoom, type ChatMessagePage } from './api';
export type {
  ChatMessage,
  ChatMessageDto,
  ChatPartnerRole,
  ChatRoomDto,
  ChatRoomSummary,
  ChatSendRequest,
  MessageType,
} from './types';
