'use client';

import { Client, type IMessage } from '@stomp/stompjs';
import { useCallback, useEffect, useRef, useState } from 'react';
import { toChatMessage } from '../model/mapper';
import type { ChatMessage, ChatMessageDto } from '../types';

/**
 * 채팅 WebSocket 주소. next.config.ts 의 rewrites 는 /api 만 넘기고
 * WebSocket 업그레이드는 중계하지 못해서 백엔드에 직접 붙습니다.
 */
const WS_URL = process.env.NEXT_PUBLIC_WS_URL;

export type ChatSocketStatus = 'connecting' | 'connected' | 'error';

type Options = {
  /**
   * 연결을 시작할지. 채팅방 목록을 먼저 불러온 뒤에 켜세요 —
   * 그 호출이 액세스 토큰을 갱신해 주기 때문입니다(아래 설명 참고).
   */
  enabled: boolean;
  /** 구독할 방. null 이면 구독하지 않습니다(연결은 유지). */
  roomId: number | null;
  /** 내 메시지를 가려내는 기준. roomId 와 짝입니다. */
  partnerId: number | null;
  /** 새 메시지가 도착했을 때 */
  onMessage: (message: ChatMessage) => void;
};

type Result = {
  status: ChatSocketStatus;
  /** status 가 'error' 일 때의 안내 문구 */
  error: string | null;
  /** 메시지 발행. 연결 전이거나 방이 없으면 false 를 돌려줍니다. */
  send: (content: string) => boolean;
};

/**
 * 채팅 STOMP 연결.
 *
 * 인증: 핸드셰이크(평범한 HTTP 요청)에 로그인 쿠키가 자동으로 실리고,
 * 백엔드 JwtAuthenticationFilter 가 그걸 읽어 Principal 을 만들어 줍니다.
 * 액세스 토큰이 HttpOnly 라 JS 가 헤더에 실을 수 없어서 이 방식을 씁니다.
 *
 * ⚠️ 토큰이 만료된 채로 핸드셰이크하면 복구할 방법이 없습니다(REST 처럼 재발급 후 재시도가 안 됨).
 *    그래서 enabled 는 "/api 호출이 한 번 성공한 뒤"에 켜야 합니다 —
 *    src/lib/api.ts 가 그 과정에서 만료 토큰을 갱신해 둡니다.
 */
export function useChatSocket({ enabled, roomId, partnerId, onMessage }: Options): Result {
  const [status, setStatus] = useState<ChatSocketStatus>('connecting');
  const [error, setError] = useState<string | null>(null);
  const clientRef = useRef<Client | null>(null);

  // 구독 콜백이 매 렌더마다 바뀌어도 재구독하지 않도록 최신 함수만 들고 있습니다.
  const onMessageRef = useRef(onMessage);
  useEffect(() => {
    onMessageRef.current = onMessage;
  });

  // 연결은 패널이 열려 있는 동안 한 번만. 방을 옮겨도 끊지 않습니다.
  useEffect(() => {
    if (!enabled || !WS_URL) return;

    const client = new Client({
      brokerURL: WS_URL,
      // 끊겨도 5초마다 다시 붙습니다. 재연결되면 onConnect 가 다시 불려 구독도 복구됩니다.
      reconnectDelay: 5000,
      beforeConnect: () => {
        setStatus('connecting');
        setError(null);
      },
      onConnect: () => {
        setStatus('connected');
        setError(null);
      },
      onStompError: (frame) => {
        setStatus('error');
        setError(frame.headers.message || '채팅 서버가 연결을 거부했습니다.');
      },
      onWebSocketError: () => {
        setStatus('error');
        setError('채팅 서버에 연결하지 못했습니다.');
      },
    });

    clientRef.current = client;
    client.activate();

    return () => {
      clientRef.current = null;
      void client.deactivate();
    };
  }, [enabled]);

  // 방이 바뀌면 그 방의 토픽만 다시 구독합니다.
  useEffect(() => {
    const client = clientRef.current;
    if (status !== 'connected' || !client || roomId === null || partnerId === null) return;

    const subscription = client.subscribe(`/topic/chat/${roomId}`, (frame: IMessage) => {
      const dto = JSON.parse(frame.body) as ChatMessageDto;
      onMessageRef.current(toChatMessage(dto, partnerId));
    });

    return () => subscription.unsubscribe();
  }, [status, roomId, partnerId]);

  const send = useCallback(
    (content: string) => {
      const client = clientRef.current;
      if (!client?.connected || roomId === null) return false;

      // 백엔드 ChatMessageDto 는 content 하나만 받습니다.
      // 보낸 메시지도 서버가 토픽으로 되돌려주므로 여기서 미리 화면에 넣지 않습니다.
      client.publish({ destination: `/app/chat/${roomId}`, body: JSON.stringify({ content }) });
      return true;
    },
    [roomId],
  );

  // 빌드 타임 상수라 런타임에 바뀌지 않습니다 — 상태로 들고 있지 않고 여기서 덮어씁니다.
  if (!WS_URL) {
    return { status: 'error', error: '채팅 서버 주소(NEXT_PUBLIC_WS_URL)가 설정되지 않았습니다.', send: () => false };
  }

  return { status, error, send };
}
