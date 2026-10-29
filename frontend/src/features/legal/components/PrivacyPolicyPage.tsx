'use client';

import { AppShell } from '@/components/layout';
import { FileText, Info, ShieldCheck } from 'lucide-react';
import { EFFECTIVE_DATE, INTRO, POLICY_ARTICLES, POLICY_VERSIONS } from '../model/privacy';
import type { PolicyBlock } from '../types';

const CARD = 'rounded-2xl border border-[#E1E5E8] bg-white px-5 py-6 shadow-[0_3px_12px_rgba(30,39,57,0.035)] sm:px-8 sm:py-8';

function PolicyContent({ block }: { block: PolicyBlock }) {
  if (block.type === 'p') {
    return <p className="text-sm leading-7 text-[#596777] sm:text-[15px]">{block.text}</p>;
  }
  if (block.type === 'ul') {
    return (
      <ul className="space-y-2 pl-5 text-sm leading-7 text-[#596777] [list-style:disc] marker:text-[#55749B] sm:text-[15px]">
        {block.items.map((item) => <li key={item}>{item}</li>)}
      </ul>
    );
  }
  if (block.type === 'note') {
    return (
      <div className="flex gap-3 rounded-xl border border-[#DCE5EE] bg-[#F1F5F8] px-4 py-3 text-sm leading-6 text-[#465D77]">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-[#55749B]" aria-hidden="true" />
        <p>{block.text}</p>
      </div>
    );
  }
  return (
    <div className="overflow-x-auto rounded-xl border border-[#E1E5E8]">
      <table className="w-full min-w-[560px] border-collapse text-left text-[13px] leading-6 text-[#596777] sm:text-sm">
        <thead className="bg-[#F5F6F8] text-[#14243A]">
          <tr>
            {block.head.map((cell) => <th key={cell} scope="col" className="px-4 py-3 font-semibold">{cell}</th>)}
          </tr>
        </thead>
        <tbody>
          {block.rows.map((row, rowIndex) => (
            <tr key={`${row[0]}-${rowIndex}`} className="border-t border-[#E9ECEF]">
              {row.map((cell, cellIndex) => cellIndex === 0 ? (
                <th key={cellIndex} scope="row" className="px-4 py-3 align-top font-semibold text-[#344055]">{cell}</th>
              ) : <td key={cellIndex} className="px-4 py-3 align-top">{cell}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function PrivacyPolicyPage() {
  return (
    <AppShell>
      <main className="min-h-[calc(100svh-80px)] bg-[#F7F8F8] px-5 pb-24 pt-12 sm:px-8 sm:pt-16">
        <div className="mx-auto max-w-[1240px]">
          <header className="mb-9 space-y-3 sm:mb-11">
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-widest text-[#426B93]">
              <ShieldCheck className="h-4 w-4" aria-hidden="true" /> PRIVACY & TRUST
            </div>
            <h1 className="text-3xl font-semibold tracking-tight text-[#14243A] sm:text-4xl">개인정보 처리방침</h1>
            <p className="max-w-2xl text-sm leading-7 text-[#67717C] sm:text-base">GAJI 서비스에서 개인정보를 어떤 목적으로 처리하는지 확인할 수 있어요.</p>
          </header>

          <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[260px_minmax(0,1fr)] lg:gap-7">
            <aside className="lg:sticky lg:top-24">
              <nav aria-label="개인정보 처리방침 목차" className={`${CARD} max-h-[calc(100svh-120px)] overflow-y-auto !p-5`}>
                <div className="mb-4 flex items-center gap-2 border-b border-[#E9ECEF] pb-4 text-sm font-semibold text-[#14243A]">
                  <FileText className="h-[18px] w-[18px] text-[#55749B]" aria-hidden="true" /> 목차
                </div>
                <ol className="space-y-0.5">
                  {POLICY_ARTICLES.map((article, index) => (
                    <li key={article.id}>
                      <a href={`#${article.id}`} className="block rounded-lg px-2 py-2 text-[13px] leading-5 text-[#67717C] transition-colors hover:bg-[#EDF3F8] hover:text-[#426B93] focus-visible:outline-2 focus-visible:outline-[#426B93]">
                        <span className="mr-1 font-semibold">{index + 1}.</span> {article.title}
                      </a>
                    </li>
                  ))}
                  <li><a href="#history" className="block rounded-lg px-2 py-2 text-[13px] text-[#67717C] transition-colors hover:bg-[#EDF3F8] hover:text-[#426B93]">작성 이력</a></li>
                </ol>
              </nav>
            </aside>

            <div className="min-w-0 space-y-5">
              <section className={CARD} aria-label="개인정보 처리방침 안내">
                <div className="space-y-3">
                  {INTRO.map((paragraph) => <p key={paragraph} className="text-sm leading-7 text-[#596777] sm:text-[15px]">{paragraph}</p>)}
                </div>
                <p className="mt-5 border-t border-[#E9ECEF] pt-4 text-sm font-semibold text-[#344055]">시행일: {EFFECTIVE_DATE}</p>
              </section>

              {POLICY_ARTICLES.map((article, index) => (
                <section key={article.id} id={article.id} aria-labelledby={`${article.id}-title`} className={`${CARD} scroll-mt-28`}>
                  <h2 id={`${article.id}-title`} className="mb-5 border-b border-[#E9ECEF] pb-4 text-lg font-semibold text-[#14243A] sm:text-xl">
                    <span className="mr-2 text-[#55749B]">{String(index + 1).padStart(2, '0')}</span>{article.title}
                  </h2>
                  <div className="space-y-4">
                    {article.blocks.map((block, blockIndex) => <PolicyContent key={blockIndex} block={block} />)}
                  </div>
                </section>
              ))}

              <section id="history" aria-labelledby="history-title" className={`${CARD} scroll-mt-28`}>
                <h2 id="history-title" className="mb-5 border-b border-[#E9ECEF] pb-4 text-lg font-semibold text-[#14243A] sm:text-xl">작성 이력</h2>
                <ul className="space-y-3 text-sm leading-7 text-[#596777]">
                  {POLICY_VERSIONS.map((version) => (
                    <li key={`${version.label}-${version.date}`} className="flex flex-wrap gap-x-3">
                      <strong className="text-[#14243A]">{version.label} · {version.date}</strong>
                      <span>{version.summary}</span>
                    </li>
                  ))}
                </ul>
              </section>
            </div>
          </div>
        </div>
      </main>
    </AppShell>
  );
}
