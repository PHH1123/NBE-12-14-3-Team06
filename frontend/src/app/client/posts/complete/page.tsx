'use client';

import dynamic from 'next/dynamic';

const PostCompletePage = dynamic(() => import('@/features/client').then((m) => m.PostCompletePage), {
  ssr: false,
  loading: () => <div className="min-h-screen bg-[#F5F6F8]" />,
});

export default function Page() {
  return <PostCompletePage />;
}
