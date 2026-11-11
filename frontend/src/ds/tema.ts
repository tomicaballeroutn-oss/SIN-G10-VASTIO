/**
 * Tema claro (predeterminado) u oscuro (operación nocturna en barra y depósito).
 * La preferencia es de la persona en este dispositivo: es una comodidad, no un dato del sistema.
 */
export type Tema = 'claro' | 'oscuro' | 'sistema';

const CLAVE = 'vastio.tema';
const EVENTO_TEMA = 'vastio:tema-cambiado';

/** Notifica a los controles cuando cambia el tema, incluso por una preferencia del dispositivo. */
export function suscribirTema(avisar: () => void): () => void {
  window.addEventListener(EVENTO_TEMA, avisar);
  return () => window.removeEventListener(EVENTO_TEMA, avisar);
}

export function temaOscuroActivo(): boolean {
  return document.documentElement.dataset.theme === 'dark';
}

function notificarTema() {
  window.dispatchEvent(new Event(EVENTO_TEMA));
}

export function temaGuardado(): Tema {
  try {
    const valor = localStorage.getItem(CLAVE);
    return valor === 'oscuro' || valor === 'sistema' ? valor : 'claro';
  } catch {
    return 'claro';
  }
}

let quitarSeguimiento: (() => void) | null = null;

export function aplicarTema(tema: Tema = temaGuardado()) {
  // «Como el dispositivo» sigue al sistema operativo también mientras la app está abierta.
  quitarSeguimiento?.();
  quitarSeguimiento = null;
  const consulta = window.matchMedia?.('(prefers-color-scheme: dark)');
  if (tema === 'sistema' && consulta) {
    const alCambiar = () => {
      document.documentElement.dataset.theme = consulta.matches ? 'dark' : 'light';
      notificarTema();
    };
    consulta.addEventListener('change', alCambiar);
    quitarSeguimiento = () => consulta.removeEventListener('change', alCambiar);
  }
  const oscuro = tema === 'oscuro' || (tema === 'sistema' && window.matchMedia?.('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = oscuro ? 'dark' : 'light';
  notificarTema();
  try {
    localStorage.setItem(CLAVE, tema);
  } catch {
    // modo privado o almacenamiento bloqueado: el tema vale solo para esta visita
  }
}
