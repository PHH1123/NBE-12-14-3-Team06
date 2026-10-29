'use client';

import { Suspense } from 'react';
import dynamic from 'next/dynamic';
import { AppShell } from '@/components/layout';
import { useRequireAuth } from '@/features/auth';
import { LoaderCircle } from 'lucide-react';

function EscortPostsLoadingContent() {
    return (
        <section className="flex min-h-[calc(100svh-72px)] items-center bg-[#F7F8F8] px-5 py-14 sm:px-8 lg:py-20">
            <div className="mx-auto w-full max-w-[680px] rounded-2xl border border-[#E1E5E8] bg-white px-6 py-12 text-center shadow-[0_4px_20px_rgba(30,39,57,0.045)] sm:px-12 sm:py-14">
                <span className="inline-flex items-center rounded-md bg-[#EDF0F2] px-3 py-1.5 text-xs font-semibold tracking-[0.08em] text-[#426B93]">LOADING</span>
                <div className="mx-auto mt-7 flex h-16 w-16 items-center justify-center rounded-2xl bg-[#EDF0F2] text-[#426B93]">
                    <LoaderCircle className="h-8 w-8 animate-spin motion-reduce:animate-none" strokeWidth={1.8} aria-hidden="true" />
                </div>
                <h1 role="status" className="mt-6 text-2xl font-semibold tracking-tight text-[#14243A] sm:text-[28px]">공고를 불러오는 중입니다.</h1>
                <p className="mx-auto mt-3 max-w-[460px] text-sm leading-7 text-[#67717C] sm:text-base">동행 가능한 공고 목록을 준비하고 있어요. 잠시만 기다려 주세요.</p>
            </div>
        </section>
    );
}

const PostListPage = dynamic(
    () => import('@/features/post').then((m) => m.PostListPage),
    {
        ssr: false,
        loading: () => <EscortPostsLoadingContent />,
    },
);

const LOADING_FALLBACK = (
    <AppShell>
        <EscortPostsLoadingContent />
    </AppShell>
);

/** useRequireAuth 가 next 파라미터를 만들려고 내부에서 useSearchParams() 를 씁니다.
 * useSearchParams() 는 빌드 시점 미리 그리기(prerender)에서 쿼리 값을 알 수 없어
 * 반드시 <Suspense> 경계 안에서만 쓸 수 있어서, 이 훅을 부르는 부분을 따로 뺐습니다. */
function Guard() {
    const { loading, user } = useRequireAuth('ESCORT');

    if (loading) return LOADING_FALLBACK;
    if (!user) return null;

    return (
        <AppShell>
            <PostListPage detailBasePath="/escort/posts" />
        </AppShell>
    );
}

export default function Page() {
    return (
        <Suspense fallback={LOADING_FALLBACK}>
            <Guard />
        </Suspense>
    );
}