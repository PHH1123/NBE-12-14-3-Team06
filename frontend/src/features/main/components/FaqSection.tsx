'use client';

import { useState, type CSSProperties } from 'react';
import Link from 'next/link';
import { openGajiGuide } from '@/lib/gajiGuide';
import { useCurrentUser } from '@/features/auth';

/** FAQ: GAJI 최종 디자인(HomeView.tsx)의 질문과 답변을 그대로 이식. */
const FAQ_ITEMS = [
  { question: '동행 서비스는 어떻게 신청하나요?', answer: '홈페이지 상단의 [동행 요청하기] 버튼을 통해 방문하실 병원, 진료 일시, 진료과, 어르신의 보행 상태(자립 보행, 부축 필요, 휠체어 이용 등) 및 상세 주의사항을 입력하시면 간편하게 공고가 등록됩니다. 공고가 등록되면 인근에서 활동하는 검증된 전문 매니저들이 지원서를 접수하게 됩니다.', action: { label: '공고 등록 바로가기', href: '/login' } },
  { question: '동행 신청은 언제까지 가능한가요?', answer: '병원 진료 일정 며칠 전 미리 등록하시면 더 많은 매니저의 지원을 받아 프로필과 후기를 여유롭게 검토하고 선택하실 수 있습니다. 당일 또는 진료 전날 긴급한 신청도 공고 등록은 언제든 가능하지만, 매니저의 지원 및 수락 일정에 따라 매칭 가능 여부가 달라질 수 있으므로 최소 1~2일 전 등록을 권장해 드립니다.', action: { label: '공고 등록 바로가기', href: '/login' } },
  { question: '동행 비용 산정과 결제는 어떻게 진행되나요?', answer: '동행 비용은 법정 최저시급(2026년 기준 10,320원) 이상의 기준 시급과 예상 동행 시간(기본 2시간 이상)을 바탕으로 사전에 투명하게 산정됩니다. 공고 등록 시 결제가 진행되며, 서비스가 안전하게 완료될 때까지 결제 대금이 안전하게 보호되는 안심 에스크로 결제 시스템을 적용하고 있습니다.', action: { label: '이용 방법 자세히 보기', href: '/#steps' } },
  { question: '동행 매니저는 어떻게 선정하나요?', answer: 'GAJI는 자동 배정 방식이 아닙니다. 의뢰인이 공고를 등록하면 매니저들이 자율적으로 지원하며, 의뢰인은 지원한 매니저들의 프로필, 활동 등급(씨앗·새싹·꽃·가지), 보유 자격증, 실제 이용자들의 리뷰와 평점을 꼼꼼히 확인한 후 마음에 드는 매니저를 직접 선택하여 승인함으로써 매칭이 최종 확정됩니다.', action: { label: '이용 방법 자세히 보기', href: '/#steps' } },
  { question: '동행 매니저는 어떤 자격과 교육을 거치나요?', answer: '모든 동행 매니저는 본인 인증을 통한 철저한 신원 확인을 거치며, 요양보호사, 사회복지사, 간호조무사 등 보유 자격증 정보는 회원가입 시 선택적으로 등록할 수 있습니다. 특정 전문 자격증 소지가 필수는 아니지만, 모든 매니저는 공고 지원 전 환자 부축법, 낙상 방지, 휠체어 조작, 비상 대응 수칙이 포함된 필수 안전 동행 교육을 반드시 이수해야 활동할 수 있습니다.', action: { label: '매니저 등급 자세히 보기', href: '/#faq-question-9' } },
  { question: '병원 내 이동과 진료실 동행도 가능한가요?', answer: '네, 물론 가능합니다. 자택 문 앞 만남부터 병원 이동, 복잡한 종합병원 원내 길 찾기, 무인 접수·수납기(키오스크) 조작 보조, 진료실 내부 동행 및 의사 설명 경청, 검사실 및 채혈실 이동, 처방전 약국 조제 수령, 안전한 귀가 확인까지 전 과정을 세심하게 곁에서 보조해 드립니다.', action: null },
  { question: '동행을 취소하면 어떻게 되나요?', answer: '매니저 매칭 확정 전에는 언제든 수수료 없이 자유롭게 취소 및 전액 환불받으실 수 있습니다. 매칭이 확정된 이후 취소하실 경우, 상호 신뢰와 긴급 대체 보호를 위해 취소 시점에 따른 세부 취소 및 환불 기준이 적용됩니다. 정당한 사유 없는 일방 취소나 노쇼(No-Show) 발생 시에는 매니저 활동 제재 및 유효 등급 하향 조치가 적용될 수 있습니다.', action: { label: '서비스 이용 정책 보기', href: '/#faq-question-7' } },
  { question: '진료 후 AI 진료 보고서는 어떻게 확인하나요?', answer: '동행이 끝나면 매니저가 진료실에서 기록한 의사 소견, 다음 진료 예약 일정, 주의사항, 약국 복약 스케줄을 앱 내 진료 보고서로 등록합니다. 등록된 내용은 GAJI의 AI 안심 요약 기술을 통해 3줄 핵심 요약으로 정리되어, 의뢰인은 마이페이지 및 진료 보고서 메뉴에서 언제 어디서나 편리하게 확인하고 가족과 공유할 수 있습니다.', action: { label: '예시 보고서 열람하기', href: '/#report' } },
  { question: '동행 매니저 등급은 어떻게 정해지나요?', answer: '동행 매니저 등급은 안전하게 완료한 실제 동행 횟수를 기준으로 씨앗(0~4회), 새싹(5~9회), 꽃(10~29회), 가지(30회 이상) 총 4단계로 투명하게 운영됩니다. 노쇼나 중대한 정책 위반 발생 시 페널티로 유효 등급이 하향 조정될 수 있으며, 실제 이용자 후기와 별점 평점은 객관적 완료 횟수 기반 등급과 분리되어 프로필에 독립적으로 표기됩니다.', action: { label: '매니저 등급 자세히 보기', href: '/#faq-question-9' } },
] as const;

function ArrowIcon() {
  return <svg aria-hidden="true" className="h-3.5 w-3.5 transition-transform duration-200 group-hover/faq-link:translate-x-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14m-6-6 6 6-6 6"/></svg>;
}

export default function FaqSection() {
  const [openIndex, setOpenIndex] = useState<number | null>(0);
  const { user } = useCurrentUser();

  const actionHref = (index: number, href: string) => {
    if (index === 0 || index === 1) {
      if (!user) return '/login';
      return user.role === 'ESCORT' ? '/escort/posts' : '/client/posts/new';
    }
    return href;
  };

  return (
    <section id="faq" className="border-t border-[#E1E5E8] bg-white py-20 lg:py-28">
      <div className="mx-auto max-w-[880px] px-5 sm:px-8">
        <div className="mx-auto mb-12 max-w-xl text-center">
          <span className="mb-2 inline-block text-xs font-semibold uppercase tracking-wider text-[#426B93]">Frequently Asked Questions</span>
          <h2 className="text-[28px] font-semibold leading-[1.3] tracking-tight text-[#14243A] sm:text-[34px]">자주 묻는 질문</h2>
          <p className="mt-2.5 text-[15px] text-[#67717C] sm:text-[16px]">GAJI 서비스 이용에 대해 가장 많이 궁금해하시는 질문들을 모았습니다.</p>
        </div>
        <div className="divide-y divide-[#E1E5E8] border-y border-[#E1E5E8]">
          {FAQ_ITEMS.map((item, index) => {
            const isOpen = openIndex === index;
            return (
              <div key={item.question} id={`faq-question-${index + 1}`} className="scroll-mt-24">
                <button
                  id={`faq-header-${index}`}
                  type="button"
                  onClick={() => setOpenIndex(isOpen ? null : index)}
                  aria-expanded={isOpen}
                  aria-controls={`faq-panel-${index}`}
                  className="group flex w-full cursor-pointer items-center justify-between gap-4 py-5 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#426B93] sm:py-6"
                >
                  <span className="text-[16px] font-semibold leading-snug text-[#14243A] transition-colors duration-200 group-hover:text-[#426B93] sm:text-[17px]">{item.question}</span>
                  <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full transition-all duration-200 ${isOpen ? 'rotate-180 bg-[#426B93]/10 text-[#426B93]' : 'bg-[#EDF0F2] text-[#67717C]'}`}>
                    <svg aria-hidden="true" className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m6 9 6 6 6-6"/></svg>
                  </span>
                </button>
                <div
                  id={`faq-panel-${index}`}
                  role="region"
                  aria-labelledby={`faq-header-${index}`}
                  aria-hidden={!isOpen}
                  inert={!isOpen}
                  className="grid transition-[grid-template-rows,opacity] duration-300 ease-out motion-reduce:transition-none"
                  style={{ gridTemplateRows: isOpen ? '1fr' : '0fr', opacity: isOpen ? 1 : 0 } as CSSProperties}
                >
                  <div className="min-h-0 overflow-hidden">
                    <div className="pb-6 pt-1 text-[15px] leading-[1.75] text-[#67717C]">
                      <p className="mb-3 whitespace-pre-line">{item.answer}</p>
                      {item.action && (
                        <div className="pt-2">
                          {(index === 4 || index === 6 || index === 8) ? (
                            <button type="button" onClick={() => openGajiGuide(index === 6 ? 'policy' : 'grade')} className="group/faq-link inline-flex cursor-pointer items-center gap-1.5 text-xs font-semibold text-[#426B93] transition-colors duration-200 hover:text-[#345778]">
                              <span>{item.action.label}</span><ArrowIcon />
                            </button>
                          ) : (
                            <Link href={actionHref(index, item.action.href)} className="group/faq-link inline-flex items-center gap-1.5 text-xs font-semibold text-[#426B93] transition-colors duration-200 hover:text-[#345778]">
                              <span>{item.action.label}</span><ArrowIcon />
                            </Link>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
        <div className="mt-10 flex flex-col items-center justify-between gap-4 rounded-2xl border border-[#E1E5E8] bg-[#F7F8F8] p-5 text-center sm:flex-row sm:text-left">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#426B93]/10 text-[#426B93]">
              <svg aria-hidden="true" className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.5 9a2.5 2.5 0 1 1 4.2 1.8c-1 .9-1.7 1.3-1.7 2.7M12 17h.01"/></svg>
            </div>
            <div>
              <p className="text-sm font-semibold text-[#14243A]">원하시는 답변을 찾지 못하셨나요?</p>
              <p className="text-xs text-[#67717C]">고객지원팀에 1:1 온라인 문의를 남겨주시면 빠르게 답변드립니다.</p>
            </div>
          </div>
          <Link href="/support" className="shrink-0 rounded-lg border border-[#E1E5E8] bg-white px-4 py-2 text-xs font-semibold text-[#14243A] shadow-xs transition-colors duration-200 hover:bg-[#EDF0F2]">1:1 문의하기</Link>
        </div>
      </div>
    </section>
  );
}
