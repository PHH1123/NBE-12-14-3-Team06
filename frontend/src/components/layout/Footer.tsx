'use client';

import Link from 'next/link';
import { openGajiGuide } from '@/lib/gajiGuide';
import { openGajiSupport } from '@/lib/gajiSupport';

export default function Footer() {
  const linkClass = 'text-left transition-colors duration-200 hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white';
  return (
    <footer id="footer" className="mt-0 border-t border-white/10 bg-[#14243A] pt-16 pb-12 text-white/80">
      <div className="mx-auto max-w-[1240px] px-5 sm:px-8 lg:px-16">
        <div className="grid grid-cols-1 gap-10 border-b border-white/10 pb-12 md:grid-cols-12">
          <div className="space-y-4 md:col-span-5">
            <div className="flex items-center gap-2">
              <span className="text-2xl font-semibold tracking-tight text-white">GAJI</span>
              <span className="text-base font-bold text-white/90">가지</span>
              <span className="inline-block h-1.5 w-1.5 rounded-full bg-white/60" />
            </div>
            <p className="max-w-sm text-sm leading-relaxed text-white/60">어르신의 안전한 병원 가는 길부터 귀가까지, 믿을 수 있는 전문 동행 매니저가 곁을 지키는 병원 동행 서비스입니다.</p>
            <div className="flex items-center gap-4 pt-2 text-xs text-white/50">
              <span className="inline-flex items-center gap-1.5"><span className="text-[#426B93]">♢</span>매니저 신원 확인</span>
              <span>·</span>
              <span className="inline-flex items-center gap-1.5"><span className="text-[#2F7D5B]">♡</span>안심 동행 케어 준수</span>
            </div>
          </div>
          <div className="space-y-3 md:col-span-2">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-white">서비스 안내</h4>
            <ul className="space-y-2 text-sm">
              <li><Link href="/#persona" className={linkClass}>서비스 소개</Link></li>
              <li><Link href="/#steps" className={linkClass}>이용 방법 안내</Link></li>
            </ul>
          </div>
          <div className="space-y-3 md:col-span-2">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-white">이용 안내</h4>
            <ul className="space-y-2 text-sm">
              <li><button type="button" onClick={() => openGajiGuide('grade')} className={linkClass}>매니저 등급 안내</button></li>
              <li><Link href="/#faq" className={linkClass}>자주 묻는 질문</Link></li>
              <li><button type="button" onClick={() => openGajiGuide('policy')} className={linkClass}>서비스 이용 정책</button></li>
            </ul>
          </div>
          <div className="space-y-3 md:col-span-3">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-white">고객센터</h4>
            <p className="text-xs leading-relaxed text-white/60">서비스 이용 중 궁금한 점은 고객센터에서 자주 묻는 질문과 문의 안내를 확인해 주세요.</p>
            <div className="pt-1">
              <button type="button" onClick={openGajiSupport} className="inline-flex items-center gap-2 rounded-lg border border-white/25 px-3.5 py-2 text-xs text-white transition-all duration-200 hover:border-white/50 hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white">
                <svg aria-hidden="true" className="h-3.5 w-3.5 text-[#426B93]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M3 14v-3a9 9 0 0 1 18 0v3M3 14v4a2 2 0 0 0 2 2h3v-8H5a2 2 0 0 0-2 2Zm18 0v4a2 2 0 0 1-2 2h-3v-8h3a2 2 0 0 1 2 2Z"/></svg>
                고객센터 및 문의 안내
              </button>
            </div>
          </div>
        </div>
        <div className="flex flex-col items-start justify-between gap-4 pt-8 text-xs text-white/40 md:flex-row md:items-center">
          <p>GAJI는 의뢰인과 동행 매니저를 연결하는 중개 플랫폼이며, 의료법상 의료행위를 직접 제공하지 않습니다. 명시된 병원명은 이용 고객의 방문 목적지이며 공식 제휴 관계를 의미하지 않습니다.</p>
          <div className="flex shrink-0 items-center gap-2"><span>© 2026 GAJI Care. All rights reserved.</span></div>
        </div>
      </div>
    </footer>
  );
}
