import { Suspense } from 'react';
import AdminSettingsPage from '@/features/admin/components/AdminSettingsPage';

export default function Page() {
  return (
    <Suspense fallback={<div role="status" className="min-h-[60vh] bg-[#F7F8F8] py-24 text-center text-sm text-[#67717C]">불러오는 중입니다...</div>}>
      <AdminSettingsPage />
    </Suspense>
  );
}
