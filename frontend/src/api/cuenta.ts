import { api } from './cliente';

export interface CambioDeContrasena {
  contrasenaActual: string;
  contrasenaNueva: string;
}

/** Mi cuenta (UI-03): cada persona administra solo la suya, el usuario sale del token. */
export const cuenta = {
  cambiarContrasena: (datos: CambioDeContrasena) => api<void>('/cuenta/contrasena', { metodo: 'POST', cuerpo: datos }),
};
