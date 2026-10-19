export function openGajiSupport() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event('gaji:open-support'));
  }
}
