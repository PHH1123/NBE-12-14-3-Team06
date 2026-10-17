'use client';

/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import { X, Award, AlertTriangle, Star, ShieldCheck, CheckCircle2 } from 'lucide-react';

interface ManagerGradeModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialTab?: 'grade' | 'policy';
}

/**
 * GAJI Character growth-stage vector illustrations
 * Sophisticated, calm, and muted palette consistent with GAJI's premium brand:
 * - 씨앗 (SEED): Soft sage green with subtle cream accents
 * - 새싹 (SPROUT): Muted natural green
 * - 꽃 (FLOWER): Soft lavender / dusty purple
 * - 가지 (GAJI): Deep muted eggplant purple with high contrast against light neutral background
 */
const GajiSeedIllustration = () => (
  <svg
    viewBox="0 0 48 48"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className="w-10 h-10 shrink-0"
    aria-label="씨앗 캐릭터"
  >
    {/* Soft neutral sage container */}
    <rect width="48" height="48" rx="12" fill="#F4F6F3" />
    <circle cx="24" cy="24" r="16" fill="#E6EDE4" />
    {/* Subtle earthen mound */}
    <ellipse cx="24" cy="35" rx="10" ry="2.5" fill="#DDD7CE" />
    {/* Soft sage seed body */}
    <path
      d="M24 16C19.8 20.2 18 25 18 28.5C18 32 20.7 34.5 24 34.5C27.3 34.5 30 32 30 28.5C30 25 28.2 20.2 24 16Z"
      fill="#5E7D68"
    />
    {/* Delicate sage micro sprout */}
    <path
      d="M24 16C22.5 13 20 11.5 18.5 12C20.5 13.8 22.8 15.2 24 16Z"
      fill="#88A891"
    />
    <path
      d="M24 16C25.2 13.5 27 12.8 28.5 13.2C27.2 14.6 25.5 15.5 24 16Z"
      fill="#75987E"
    />
    {/* Friendly eyes */}
    <circle cx="21.5" cy="27.5" r="1.3" fill="#FFFFFF" />
    <circle cx="21.5" cy="27.5" r="0.6" fill="#2B3B30" />
    <circle cx="26.5" cy="27.5" r="1.3" fill="#FFFFFF" />
    <circle cx="26.5" cy="27.5" r="0.6" fill="#2B3B30" />
    {/* Warm smile */}
    <path
      d="M22.5 30.5C23.5 31.5 24.5 31.5 25.5 30.5"
      stroke="#FFFFFF"
      strokeWidth="1.1"
      strokeLinecap="round"
    />
    {/* Soft cream-peach cheeks */}
    <circle cx="19.8" cy="29.2" r="1" fill="#D4B9A7" opacity="0.65" />
    <circle cx="28.2" cy="29.2" r="1" fill="#D4B9A7" opacity="0.65" />
  </svg>
);

const GajiSproutIllustration = () => (
  <svg
    viewBox="0 0 48 48"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className="w-10 h-10 shrink-0"
    aria-label="새싹 캐릭터"
  >
    {/* Muted natural green container */}
    <rect width="48" height="48" rx="12" fill="#F0F5F1" />
    <circle cx="24" cy="24" r="16" fill="#DFECE1" />
    {/* Stem in natural green */}
    <path
      d="M24 36V22"
      stroke="#486D51"
      strokeWidth="2.8"
      strokeLinecap="round"
    />
    {/* Left leaf */}
    <path
      d="M23 25C17 25 14 19 15 14C19 14 23 19 23 25Z"
      fill="#587F61"
    />
    {/* Right leaf */}
    <path
      d="M25 23C31 23 34 17 33 12C29 12 25 17 25 23Z"
      fill="#6B9475"
    />
    {/* Sprout face */}
    <circle cx="24" cy="27.2" r="6" fill="#486D51" />
    <circle cx="22" cy="26.2" r="1.1" fill="#FFFFFF" />
    <circle cx="22" cy="26.2" r="0.55" fill="#1E3324" />
    <circle cx="26" cy="26.2" r="1.1" fill="#FFFFFF" />
    <circle cx="26" cy="26.2" r="0.55" fill="#1E3324" />
    <path
      d="M22.5 28.7C23.5 29.7 24.5 29.7 25.5 28.7"
      stroke="#FFFFFF"
      strokeWidth="1.1"
      strokeLinecap="round"
    />
    <circle cx="20.8" cy="27.7" r="0.8" fill="#A1C6A9" opacity="0.75" />
    <circle cx="27.2" cy="27.7" r="0.8" fill="#A1C6A9" opacity="0.75" />
  </svg>
);

const GajiFlowerIllustration = () => (
  <svg
    viewBox="0 0 48 48"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className="w-10 h-10 shrink-0"
    aria-label="꽃 캐릭터"
  >
    {/* Soft dusty lavender container */}
    <rect width="48" height="48" rx="12" fill="#F5F2F8" />
    <circle cx="24" cy="24" r="16" fill="#EAE4F0" />
    {/* Petals in dusty muted purple */}
    <circle cx="24" cy="14.5" r="4.8" fill="#A193B2" />
    <circle cx="32.5" cy="20.5" r="4.8" fill="#A193B2" />
    <circle cx="30.5" cy="29.5" r="4.8" fill="#A193B2" />
    <circle cx="17.5" cy="29.5" r="4.8" fill="#A193B2" />
    <circle cx="15.5" cy="20.5" r="4.8" fill="#A193B2" />
    {/* Center character face */}
    <circle cx="24" cy="23" r="7.2" fill="#5E4E70" />
    {/* Happy gentle eyes */}
    <path
      d="M21 21.5C21.5 20.8 22.5 20.8 23 21.5"
      stroke="#FFFFFF"
      strokeWidth="1.2"
      strokeLinecap="round"
    />
    <path
      d="M25 21.5C25.5 20.8 26.5 20.8 27 21.5"
      stroke="#FFFFFF"
      strokeWidth="1.2"
      strokeLinecap="round"
    />
    {/* Smile */}
    <path
      d="M22.5 24.5C23.5 25.6 24.5 25.6 25.5 24.5"
      stroke="#FFFFFF"
      strokeWidth="1.1"
      strokeLinecap="round"
    />
    {/* Cheeks */}
    <circle cx="20.5" cy="23.5" r="0.9" fill="#C4B5D1" opacity="0.8" />
    <circle cx="27.5" cy="23.5" r="0.9" fill="#C4B5D1" opacity="0.8" />
  </svg>
);

const GajiEggplantIllustration = () => (
  <svg
    viewBox="0 0 48 48"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className="w-10 h-10 shrink-0"
    aria-label="가지 캐릭터"
  >
    {/* Soft neutral-lavender container ensuring high contrast and visibility */}
    <rect width="48" height="48" rx="12" fill="#F0EEF5" />
    <circle cx="24" cy="24" r="16" fill="#E2DEEC" />
    {/* Deep muted eggplant purple body */}
    <path
      d="M24 15C18.5 15 15 22 15 28C15 34 18.8 38 24 38C29.2 38 33 34 33 28C33 22 29.5 15 24 15Z"
      fill="#3E3452"
    />
    {/* Muted olive-sage calyx (green cap) */}
    <path
      d="M24 9.5C24 11.5 24.5 13.5 24 15C23 14 20 14 17.5 16C19.2 14.5 21 13.5 22 13C21 12 20 10.5 20 9.5C21.5 10.5 23 11.5 24 12C25 11.5 26.5 10.5 28 9.5C28 10.5 27 12 26 13C27 13.5 28.8 14.5 30.5 16C28 14 25 14 24 15Z"
      fill="#52745D"
    />
    {/* Stem tip */}
    <path
      d="M24 10C24 7.8 25 7 26 6.5"
      stroke="#52745D"
      strokeWidth="1.8"
      strokeLinecap="round"
    />
    {/* Highly legible face */}
    <circle cx="20.5" cy="23.5" r="1.3" fill="#FFFFFF" />
    <circle cx="20.5" cy="23.5" r="0.6" fill="#1C1824" />
    <circle cx="27.5" cy="23.5" r="1.3" fill="#FFFFFF" />
    <circle cx="27.5" cy="23.5" r="0.6" fill="#1C1824" />
    {/* Friendly smile */}
    <path
      d="M22 26.5C23.2 27.8 24.8 27.8 26 26.5"
      stroke="#FFFFFF"
      strokeWidth="1.2"
      strokeLinecap="round"
    />
    <circle cx="19.2" cy="25.2" r="0.9" fill="#947E9B" opacity="0.75" />
    <circle cx="28.8" cy="25.2" r="0.9" fill="#947E9B" opacity="0.75" />
    {/* Refined antique gold master emblem star */}
    <polygon
      points="24,31 25.2,33.2 27.5,33.5 25.8,35.1 26.3,37.3 24,36.1 21.7,37.3 22.2,35.1 20.5,33.5 22.8,33.2"
      fill="#C89B3C"
    />
  </svg>
);

export const ManagerGradeModal: React.FC<ManagerGradeModalProps> = ({
  isOpen,
  onClose,
  initialTab = 'grade',
}) => {
  const [activeTab, setActiveTab] = useState<'grade' | 'policy'>(initialTab);
  const prevIsOpenRef = useRef(false);
  const contentScrollRef = useRef<HTMLDivElement>(null);

  // Sync tab ONLY when modal opens (transition from closed to open)
  // This allows the user to freely toggle tabs back and forth without being overridden
  useEffect(() => {
    if (isOpen && !prevIsOpenRef.current) {
      setActiveTab(initialTab);
    }
    prevIsOpenRef.current = isOpen;
  }, [isOpen, initialTab]);

  // Reset scroll position to top whenever active tab changes
  useEffect(() => {
    if (contentScrollRef.current) {
      contentScrollRef.current.scrollTop = 0;
    }
  }, [activeTab]);

  // Prevent background scrolling while modal is open
  useEffect(() => {
    if (isOpen) {
      const originalOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
      return () => {
        document.body.style.overflow = originalOverflow;
      };
    }
  }, [isOpen]);

  // Close on Escape key
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const grades = [
    {
      level: '씨앗 (SEED)',
      count: '0 – 4회 완료',
      badgeColor: 'bg-[#F4F6F3] text-[#4A6351] border-[#D9E2DA]',
      illustration: <GajiSeedIllustration />,
      description: '기본 신원 확인 및 필수 안전 동행 교육을 이수한 신규 매니저',
      benefits: [
        '정상 등록된 병원 동행 공고 지원 가능',
        '어르신 응대 및 원내 이동 안전 수칙 가이드 제공',
        '동행 완료 건수 누적 시 다음 등급으로 자동 반영',
      ],
    },
    {
      level: '새싹 (SPROUT)',
      count: '5 – 9회 완료',
      badgeColor: 'bg-[#F0F5F1] text-[#3E6547] border-[#CDE0D2]',
      illustration: <GajiSproutIllustration />,
      description: '5회 이상 안전 동행을 완수하고 주요 병원 동선에 익숙해진 성장 매니저',
      benefits: [
        '5회 이상 성실한 동행 완수 이력 보유',
        '프로필에 ‘새싹 매니저’ 공식 등급 표시',
        '축적된 동행 경험을 바탕으로 안정적인 케어 진행',
      ],
    },
    {
      level: '꽃 (FLOWER)',
      count: '10 – 29회 완료',
      badgeColor: 'bg-[#F5F2F8] text-[#5C4F6B] border-[#DDD5E6]',
      illustration: <GajiFlowerIllustration />,
      description: '10회 이상의 다양한 진료과 동행을 완수한 숙련 매니저',
      benefits: [
        '10회 이상 다수 동행 성공 이력 보유',
        '프로필에 ‘꽃 매니저’ 공식 등급 표시',
        '대학병원 및 종합병원 외래 진료 절차에 대한 높은 이해도',
      ],
    },
    {
      level: '가지 (GAJI)',
      count: '30회 이상 완료',
      badgeColor: 'bg-[#F0EEF5] text-[#3E3452] border-[#D3CDE0]',
      illustration: <GajiEggplantIllustration />,
      description: '30회 이상의 전문 동행을 완수한 GAJI의 최고 등급 마스터 매니저',
      benefits: [
        '30회 이상 풍부한 현장 동행 완수 이력 보유',
        '프로필에 최상위 ‘가지 매니저’ 마스터 등급 표시',
        '노하우가 축적된 베테랑 동행 매니저',
      ],
    },
  ];

  return (
    <div
      role="presentation"
      className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-3 sm:p-4 overflow-hidden"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog" aria-modal="true" aria-label="매니저 등급 및 서비스 이용 안내"
        className="bg-white rounded-2xl max-w-2xl w-full max-h-[90dvh] flex flex-col shadow-2xl border border-[#E1E5E8] animate-in fade-in zoom-in-95 duration-150 overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Fixed Non-scrolling Modal Header */}
        <div className="flex items-center justify-between px-6 pt-5 pb-4 border-b border-[#E1E5E8] shrink-0 bg-white">
          <div className="flex items-center gap-2.5">
            <Award className="w-5 h-5 text-[#426B93]" />
            <h3 className="text-lg sm:text-xl font-semibold text-[#14243A]">
              매니저 등급 및 서비스 이용 안내
            </h3>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-[#67717C] hover:text-[#14243A] hover:bg-[#EDF0F2] transition-colors sc-focus"
            aria-label="닫기"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Fixed Non-scrolling Tab Navigation */}
        <div className="px-6 pt-4 pb-2 shrink-0 bg-white">
          <div className="flex items-center p-1 bg-[#EDF0F2] rounded-xl border border-[#E1E5E8] gap-1">
            <button
              type="button"
              onClick={() => setActiveTab('grade')}
              className={`flex-1 py-2 text-xs sm:text-sm font-semibold rounded-[9px] transition-all sc-focus ${
                activeTab === 'grade'
                  ? 'bg-white text-[#14243A] shadow-xs'
                  : 'text-[#67717C] hover:text-[#14243A]'
              }`}
            >
              동행 매니저 등급 체계 (4단계)
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('policy')}
              className={`flex-1 py-2 text-xs sm:text-sm font-semibold rounded-[9px] transition-all sc-focus ${
                activeTab === 'policy'
                  ? 'bg-white text-[#14243A] shadow-xs'
                  : 'text-[#67717C] hover:text-[#14243A]'
              }`}
            >
              노쇼 규정 및 서비스 정책
            </button>
          </div>
        </div>

        {/* Scrollable Content Body ONLY */}
        <div
          ref={contentScrollRef}
          className="flex-1 overflow-y-auto px-6 py-4 space-y-5"
        >
          {/* Tab 1: Grade system */}
          {activeTab === 'grade' && (
            <div className="space-y-5">
              <p className="text-sm text-[#67717C] leading-relaxed">
                GAJI 동행 매니저 등급은 어르신의 안전을 위해 오직 <strong className="text-[#14243A] font-semibold">누적 완료한 정상 동행 횟수</strong>를 기준으로 투명하게 산정됩니다.
              </p>

              {/* 4 Cards Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                {grades.map((item) => (
                  <div
                    key={item.level}
                    className="bg-[#F7F8F8] border border-[#E1E5E8] rounded-xl p-4 space-y-3 flex flex-col justify-between"
                  >
                    <div>
                      <div className="flex items-center justify-between mb-2.5">
                        <div className="flex items-center gap-2.5">
                          {item.illustration}
                          <span className="font-bold text-[#14243A] text-[15px]">
                            {item.level}
                          </span>
                        </div>
                        <span className={`text-xs px-2.5 py-0.5 rounded-md font-semibold border ${item.badgeColor}`}>
                          {item.count}
                        </span>
                      </div>
                      <p className="text-xs text-[#67717C] leading-relaxed mb-2.5">
                        {item.description}
                      </p>
                    </div>
                    <ul className="space-y-1.5 text-xs text-[#202832] border-t border-[#E1E5E8] pt-2.5">
                      {item.benefits.map((benefit, i) => (
                        <li key={i} className="flex items-start gap-1.5">
                          <span className="text-[#426B93] font-bold">✓</span>
                          <span>{benefit}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>

              {/* Clarification Callouts */}
              <div className="space-y-3 pt-1">
                <div className="bg-amber-50/70 border border-amber-200 rounded-xl p-3.5 flex items-start gap-3 text-xs text-amber-900 leading-relaxed">
                  <AlertTriangle className="w-4 h-4 text-amber-700 shrink-0 mt-0.5" />
                  <div>
                    <strong className="font-semibold block mb-0.5">
                      노쇼(No-Show) 발생 시 유효 등급 조정 규정
                    </strong>
                    노쇼 등 중대한 운영 정책 위반 발생 시, 누적 완료 횟수 자체를 차감하지는 않으나 페널티 규정에 따라 활동 제한 및 실제 적용되는 유효 등급이 하향 조정될 수 있습니다.
                  </div>
                </div>

                <div className="bg-[#EDF0F2] border border-[#E1E5E8] rounded-xl p-3.5 flex items-start gap-3 text-xs text-[#14243A] leading-relaxed">
                  <Star className="w-4 h-4 text-[#426B93] shrink-0 mt-0.5" />
                  <div>
                    <strong className="font-semibold block mb-0.5">
                      별점 평점은 등급 산정과 독립적으로 별도 표기
                    </strong>
                    이용자 별점 평점 및 후기는 신뢰도 확인을 위해 프로필에 투명하게 분리 표기되며, 객관적인 누적 동행 완료 횟수 기반의 등급 산정 공식에는 합산되지 않습니다.
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Tab 2: Policy */}
          {activeTab === 'policy' && (
            <div className="space-y-4 text-sm">
              <div className="p-4 rounded-xl border border-[#E1E5E8] bg-[#F7F8F8]">
                <div className="flex items-center gap-2 mb-1.5 text-[#14243A] font-semibold text-sm">
                  <CheckCircle2 className="w-4 h-4 text-[#2F7D5B]" />
                  <span>법정 최저시급 준수 기준</span>
                </div>
                <p className="text-xs text-[#67717C] leading-relaxed pl-6">
                  GAJI의 모든 동행 공고는 법정 최저시급(2026년 기준 10,320원) 이상의 합리적인 기준 시급으로 등록되며, 매니저의 노동 대가와 양질의 케어 품질을 보장합니다.
                </p>
              </div>

              <div className="p-4 rounded-xl border border-[#E1E5E8] bg-[#F7F8F8]">
                <div className="flex items-center gap-2 mb-1.5 text-[#14243A] font-semibold text-sm">
                  <ShieldCheck className="w-4 h-4 text-[#426B93]" />
                  <span>안전한 결제 및 정산 시스템</span>
                </div>
                <p className="text-xs text-[#67717C] leading-relaxed pl-6">
                  에스크로 기반 안심 결제 시스템을 통해 결제 대금이 안전하게 보호되며, 동행 완료 및 진료 보고서 등록 확인 후 정산 절차가 진행됩니다.
                </p>
              </div>

              <div className="p-4 rounded-xl border border-[#E1E5E8] bg-[#F7F8F8]">
                <div className="flex items-center gap-2 mb-1.5 text-[#14243A] font-semibold text-sm">
                  <AlertTriangle className="w-4 h-4 text-[#B4413C]" />
                  <span>취소 및 노쇼(No-Show) 관리 규정</span>
                </div>
                <div className="text-xs text-[#67717C] leading-relaxed pl-6 space-y-1">
                  <p>• 매니저 매칭 확정 전에는 별도의 수수료 없이 자유롭게 취소하실 수 있습니다.</p>
                  <p>• 원활한 서비스 운영과 상호 신뢰를 위해 취소 시점에 따른 세부 환불 및 수수료 기준이 적용됩니다.</p>
                  <p>• 정당한 사유 없는 일방적 당일 취소나 노쇼 발생 시 매니저 활동 제재 및 유효 등급 하향 조치가 적용될 수 있습니다.</p>
                </div>
              </div>

              <div className="p-4 rounded-xl border border-[#E1E5E8] bg-[#F7F8F8]">
                <div className="flex items-center gap-2 mb-1.5 text-[#14243A] font-semibold text-sm">
                  <Award className="w-4 h-4 text-[#426B93]" />
                  <span>동행 안전 및 이용자 보호 원칙</span>
                </div>
                <p className="text-xs text-[#67717C] leading-relaxed pl-6">
                  의뢰인과 매니저 모두가 안심할 수 있도록 본인 인증 기반 신원 확인, 필수 안전 교육, 상호 평가 시스템을 체계적으로 운영하여 안전하고 신뢰할 수 있는 동행 환경을 만듭니다.
                </p>
              </div>
            </div>
          )}
        </div>

        {/* Fixed Non-scrolling Modal Footer */}
        <div className="px-6 py-4 border-t border-[#E1E5E8] shrink-0 flex justify-end bg-[#F7F8F8]">
          <button
            onClick={onClose}
            className="h-[42px] px-6 rounded-[10px] text-sm font-semibold bg-[#14243A] text-white hover:bg-[#1f3757] transition-colors sc-focus"
          >
            확인 및 닫기
          </button>
        </div>
      </div>
    </div>
  );
};
