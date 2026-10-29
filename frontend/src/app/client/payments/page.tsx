'use client';

import dynamic from 'next/dynamic';

const ClientPaymentHistoryPage = dynamic(
  () => import('@/features/client/components/ClientPaymentHistoryPage'),
  { ssr: false, loading: () => <div className="min-h-screen bg-[#F7F8F8]" /> },
);

export default function Page() {
  return <ClientPaymentHistoryPage />;
}
