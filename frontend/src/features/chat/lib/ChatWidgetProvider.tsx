'use client';

import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
// 배럴(@/features/auth)로 가져오면 auth → LoginPage → AppShell → chat 으로 순환 import 가 생겨서
// 모듈 경로로 직접 가져옵니다. (AppShell 이 같은 이유로 그렇게 하고 있습니다)
import { useAuth } from '@/features/auth/lib/AuthProvider';
import ChatLauncher from '../components/ChatLauncher';
import ChatPanel from '../components/ChatPanel';

type ChatWidgetValue = {
  open: boolean;
  /**
   * 채팅 패널을 엽니다.
   * roomId 를 주면 그 방을 바로 열고, 없으면 채팅방 목록을 보여 줍니다.
   */
  openChat: (roomId?: number) => void;
  closeChat: () => void;
};

type WidgetState = {
  open: boolean;
  /** 보고 있는 방. null 이면 목록 */
  roomId: number | null;
};

const CLOSED: WidgetState = { open: false, roomId: null };

const ChatWidgetContext = createContext<ChatWidgetValue | null>(null);

/**
 * 플로팅 채팅 위젯(우하단 버튼 + 패널)을 앱 전체에 한 번만 띄웁니다.
 *
 * 화면 어디서든 useChatWidget().openChat() 으로 열 수 있어서,
 * 각 화면의 "연락하기" 버튼은 자기 자리에서 이 함수만 부르면 됩니다.
 *
 * AppShell 이 아니라 app/layout.tsx 에서 감쌉니다 — AppShell 안에 두면
 * AppShell 을 직접 렌더링하는 페이지(동행 현황·보고서 상세 등)가 자기 최상단에서
 * useChatWidget 을 부를 수 없게 됩니다(그 자리는 아직 프로바이더 바깥).
 *
 * 채팅은 의뢰인↔동행 매니저 사이의 기능이라, 비로그인·관리자에게는
 * 버튼도 패널도 띄우지 않고 openChat 은 아무것도 하지 않습니다.
 */
export function ChatWidgetProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [state, setState] = useState<WidgetState>(CLOSED);

  const enabled = user?.role === 'CLIENT' || user?.role === 'ESCORT';

  const value = useMemo<ChatWidgetValue>(
    () => ({
      open: enabled && state.open,
      openChat: (roomId?: number) => {
        if (!enabled) return;
        setState({ open: true, roomId: roomId ?? null });
      },
      closeChat: () => setState(CLOSED),
    }),
    [enabled, state.open],
  );

  return (
    <ChatWidgetContext value={value}>
      {children}
      {enabled && (
        <>
          <ChatLauncher
            open={state.open}
            onToggle={() => setState((prev) => (prev.open ? CLOSED : { open: true, roomId: null }))}
          />
          {state.open && (
            <ChatPanel
              roomId={state.roomId}
              onSelectRoom={(roomId) => setState({ open: true, roomId })}
              onClose={() => setState(CLOSED)}
            />
          )}
        </>
      )}
    </ChatWidgetContext>
  );
}

export function useChatWidget(): ChatWidgetValue {
  const value = useContext(ChatWidgetContext);

  if (!value) {
    throw new Error('useChatWidget 는 ChatWidgetProvider 안에서만 쓸 수 있습니다. AppShell 을 확인하세요.');
  }

  return value;
}
