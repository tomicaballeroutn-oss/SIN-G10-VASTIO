import { expect, test } from '@playwright/test';

// Navegador real, cámara y reproducción simuladas; no valida hardware ni decodificación óptica.
test.use({ channel: process.env.E2E_BROWSER_CHANNEL });
test('abre, cancela y reabre el lector liberando cada captura con StrictMode', async ({ page }) => {
  await page.addInitScript(() => {
    const estado = { abiertas: 0, cerradas: 0 };
    Object.assign(window, { camaraB402: estado });
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { value: async () => {
      estado.abiertas++;
      const stream = new MediaStream();
      const track = Object.assign(new EventTarget(), { readyState: 'live', stop: () => { estado.cerradas++; } });
      stream.getTracks = () => [track as unknown as MediaStreamTrack];
      stream.getVideoTracks = () => [track as unknown as MediaStreamTrack];
      return stream;
    } });
    HTMLMediaElement.prototype.play = async function () {
      if (this instanceof HTMLVideoElement && (!this.muted || !this.playsInline || !this.hasAttribute('webkit-playsinline'))) throw new Error('video móvil sin configurar');
    };
  });
  await page.goto('/_ds');
  const lector = page.getByRole('dialog', { name: 'Leer código de barras' });
  for (let intento = 0; intento < 2; intento++) {
    await page.getByRole('button', { name: 'Abrir lector', exact: true }).click();
    await expect(lector).toBeVisible();
    await expect.poll(() => page.evaluate(() => (window as unknown as { camaraB402: { abiertas: number } }).camaraB402.abiertas)).toBe(intento + 1);
    await expect(lector.locator('video')).toHaveCount(1);
    await page.getByRole('button', { name: 'Elegir a mano', exact: true }).click();
    await expect(lector).toHaveCount(0);
    await expect.poll(() => page.evaluate(() => (window as unknown as { camaraB402: { cerradas: number } }).camaraB402.cerradas)).toBe(intento + 1);
  }
});
