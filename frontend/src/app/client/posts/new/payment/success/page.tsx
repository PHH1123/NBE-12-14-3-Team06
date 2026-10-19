'use client';

import dynamic from 'next/dynamic';

const SuccessPage = dynamic(() => import('@/features/client').then((m) => m.PaymentSuccessPage), {
  ssr: false,
  loading: () => <div className="min-h-screen bg-[#F5F6F8]" />,
});

export default function Page() {
  return <SuccessPage />;
}
