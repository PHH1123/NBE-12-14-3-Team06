'use client';

import { useRouter } from 'next/navigation';
import { AppShell } from '@/components/layout';
import { SupportContent } from './SupportModal';

export default function SupportPage() {
  const router = useRouter();

  return (
    <AppShell>
      <section className="flex min-h-[calc(100vh-72px)] justify-center bg-[#F7F8F8] px-4 py-14">
        <SupportContent onClose={() => router.push('/')} />
      </section>
    </AppShell>
  );
}
