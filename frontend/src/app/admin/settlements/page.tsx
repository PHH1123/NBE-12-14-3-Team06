import { Suspense } from 'react';
import PageFeedback from '@/components/ui/PageFeedback';
import { AdminSettlementsPage } from '@/features/admin';

export default function Page() {
  return (
    <Suspense fallback={<PageFeedback kind="loading" title="정산 관리 화면을 준비하는 중입니다." />}>
      <AdminSettlementsPage />
    </Suspense>
  );
}
