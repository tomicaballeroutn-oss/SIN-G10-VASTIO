const ID_YOUTUBE = /^[A-Za-z0-9_-]{11}$/;
const DOMINIOS_YOUTUBE = new Set(['youtube.com', 'www.youtube.com', 'm.youtube.com', 'youtu.be', 'www.youtu.be']);

function idYoutube(url: URL): string | undefined {
  let id: string | null = null;
  if (url.hostname === 'youtu.be' || url.hostname === 'www.youtu.be') {
    id = url.pathname.slice(1);
  } else if (url.pathname === '/watch' && url.searchParams.getAll('v').length === 1) {
    id = url.searchParams.get('v');
  } else if (url.pathname.startsWith('/shorts/')) {
    id = url.pathname.slice('/shorts/'.length);
  }
  return id && ID_YOUTUBE.test(id) ? id : undefined;
}

/** Valida la URL antes de usarla como enlace o fuente de reproducción. */
export function urlVideoValida(valor?: string): string | undefined {
  if (!valor?.trim()) return undefined;
  try {
    const url = new URL(valor);
    if (url.protocol !== 'https:' || url.username || url.password) return undefined;
    if (DOMINIOS_YOUTUBE.has(url.hostname)) {
      if (url.port) return undefined;
      const id = idYoutube(url);
      return id ? `https://www.youtube.com/watch?v=${id}` : undefined;
    }
    if (!/\.(mp4|webm|ogv)$/i.test(url.pathname)) return undefined;
    return url.href;
  } catch {
    return undefined;
  }
}

/** La inserción siempre se construye desde un ID validado, nunca desde HTML externo. */
export function urlInsercionYoutube(valor: string): string | undefined {
  const valida = urlVideoValida(valor);
  if (!valida) return undefined;
  const url = new URL(valida);
  const id = DOMINIOS_YOUTUBE.has(url.hostname) ? idYoutube(url) : undefined;
  return id ? `https://www.youtube-nocookie.com/embed/${id}` : undefined;
}
