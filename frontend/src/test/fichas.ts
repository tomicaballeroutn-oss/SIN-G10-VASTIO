import type { Acciones } from '../api/eventos';

/**
 * Ninguna acción disponible. Los fixtures arman las suyas encima («{ ...SIN_ACCIONES, modificar: true }»), así una
 * acción nueva no obliga a tocar todos los tests.
 */
export const SIN_ACCIONES: Acciones = {
  modificar: false,
  liberar: false,
  registrarSena: false,
  registrarFirma: false,
};
