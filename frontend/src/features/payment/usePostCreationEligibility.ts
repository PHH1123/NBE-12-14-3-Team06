'use client';

import { useEffect, useState } from 'react';
import { fetchMyPayments } from './api';

/** PostService.write -> validNotPaid와 같은 READY 결제 존재 조건으로 버튼을 제어합니다.
 * 상태 확인에 실패한 경우는 등록 가능하다고 가정하지 않고, 실제 등록도 서버에서 재검증합니다.
 */
export type PostCreationAvailability = 'checking' | 'allowed' | 'blocked' | 'error';
export function usePostCreationEligibility(enabled: boolean): PostCreationAvailability {
  const [result, setResult] = useState<{ enabled: boolean; state: PostCreationAvailability }>({ enabled: false, state: 'allowed' });
  useEffect(() => {
    if (!enabled) return;
    let canceled = false;
    const check = () => {
      if (canceled) return;
      setResult({ enabled: true, state: 'checking' });
      fetchMyPayments().then(payments => {
        if (!canceled) setResult({ enabled: true, state: payments.some(p => p.paymentStatus === 'READY') ? 'blocked' : 'allowed' });
      }).catch(() => { if (!canceled) setResult({ enabled: true, state: 'error' }); });
    };
    check();
    const onVisibility = () => { if (document.visibilityState === 'visible') check(); };
    window.addEventListener('focus', check);
    document.addEventListener('visibilitychange', onVisibility);
    return () => { canceled = true; window.removeEventListener('focus', check); document.removeEventListener('visibilitychange', onVisibility); };
  }, [enabled]);
  if (!enabled) return 'allowed';
  return result.enabled ? result.state : 'checking';
}
export function postCreationUnavailableReason(status: PostCreationAvailability) {
  if (status === 'blocked') return '미결제 공고가 있습니다. 결제를 완료한 뒤 새 공고를 등록할 수 있습니다.';
  if (status === 'error') return '결제 상태를 확인하지 못했습니다. 새로고침 후 다시 시도해 주세요.';
  if (status === 'checking') return '결제 상태 확인 중입니다.';
  return '';
}
