'use client';

import dynamic from 'next/dynamic';

const PaymentPage = dynamic(() => import('@/features/client').then((m) => m.PaymentPage), {
  ssr: false,
  loading: () => <div className="min-h-screen bg-[#F7F8F8]" />,
});

export default function Page() {
  return (
    <div className="min-h-screen bg-[#F7F8F8]">
      <PaymentPage />
    </div>
  );
}
