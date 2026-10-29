'use client';

import { useCallback, useState } from 'react';
import { AppShell } from '@/components/layout';
import SampleReportModal from './SampleReportModal';
import HeroSection from './HeroSection';
import AboutSection from './AboutSection';
import PersonaSection from './PersonaSection';
import StepSection from './StepSection';
import ReportSection from './ReportSection';
import FaqSection from './FaqSection';
import CtaSection from './CtaSection';

/** GAJI — 메인 섹션을 원본 순서대로 단계적 통합 */
export default function MainPage() {
  const [showSampleReport, setShowSampleReport] = useState(false);
  const openSampleReport = useCallback(() => setShowSampleReport(true), []);
  const closeSampleReport = useCallback(() => setShowSampleReport(false), []);
  return (
    <AppShell>
      <HeroSection />
      <AboutSection />
      <PersonaSection />
      <StepSection />
      <ReportSection onOpenSampleReport={openSampleReport} />
      <FaqSection onOpenSampleReport={openSampleReport} />
      <CtaSection />
      {showSampleReport && <SampleReportModal onClose={closeSampleReport} />}
    </AppShell>
  );
}
