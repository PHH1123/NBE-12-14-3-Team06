'use client';

import { AppShell } from '@/components/layout';
import HeroSection from './HeroSection';
import AboutSection from './AboutSection';
import PersonaSection from './PersonaSection';
import StepSection from './StepSection';
import ReportSection from './ReportSection';
import FaqSection from './FaqSection';
import CtaSection from './CtaSection';

/** GAJI Signature Care — 메인 섹션을 원본 순서대로 단계적 통합 */
export default function MainPage() {
  return (
    <AppShell>
      <HeroSection />
      <AboutSection />
      <PersonaSection />
      <StepSection />
      <ReportSection />
      <FaqSection />
      <CtaSection />
    </AppShell>
  );
}
