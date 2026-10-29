'use client';

import dynamic from 'next/dynamic';

const FailPage = dynamic(() => import('@/features/client').then((m) => m.PaymentFailPage), {
  ssr: false,
  loading: () => <div className="min-h-screen bg-[#F5F6F8]" />,
});

export default function Page() {
  return <FailPage />;
}
