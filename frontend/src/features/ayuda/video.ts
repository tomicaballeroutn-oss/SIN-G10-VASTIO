/** Reproducción nativa de archivos HTTPS, sin servicios externos. */
export function urlVideoValida(valor?: string): string | undefined {
  if (!valor?.trim()) return undefined;
  try {
    const url = new URL(valor);
    if (url.protocol !== 'https:' || url.username || url.password || !/\.(mp4|webm|ogv)$/i.test(url.pathname)) return undefined;
    return url.href;
  } catch {
    return undefined;
  }
}
