/**
 * Tema claro (predeterminado) u oscuro (operación nocturna en barra y depósito).
 * La preferencia es de la persona en este dispositivo: es una comodidad, no un dato del sistema.
 */
export type Tema = 'claro' | 'oscuro' | 'sistema';

const CLAVE = 'vastio.tema';

export function temaGuardado(): Tema {
  try {
    const valor = localStorage.getItem(CLAVE);
    return valor === 'oscuro' || valor === 'sistema' ? valor : 'claro';
  } catch {
    return 'claro';
  }
}

export function aplicarTema(tema: Tema = temaGuardado()) {
  const oscuro = tema === 'oscuro' || (tema === 'sistema' && window.matchMedia?.('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = oscuro ? 'dark' : 'light';
  try {
    localStorage.setItem(CLAVE, tema);
  } catch {
    // modo privado o almacenamiento bloqueado: el tema vale solo para esta visita
  }
}
