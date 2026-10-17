/** Opens the shared guide on any page rendered with AppShell. */
export type GajiGuideTab = 'grade' | 'policy';
export function openGajiGuide(tab: GajiGuideTab): void {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent<GajiGuideTab>('gaji:open-guide', { detail: tab }));
  }
}
