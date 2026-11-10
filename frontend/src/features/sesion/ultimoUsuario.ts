/**
 * Quién fue la última persona con sesión en esta pestaña. Sirve para no mandar a alguien a la última pantalla de otra
 * persona después de cambiar de cuenta (el `volver` del login es de quien estaba antes).
 */
const CLAVE = 'vastio.ultimoUsuario';

export function leerUltimoUsuario(): number | null {
  try {
    const valor = sessionStorage.getItem(CLAVE);
    return valor === null ? null : Number(valor);
  } catch {
    return null;
  }
}

export function guardarUltimoUsuario(id: number) {
  try {
    sessionStorage.setItem(CLAVE, String(id));
  } catch {
    // sin almacenamiento: el login respeta `volver` como antes
  }
}
