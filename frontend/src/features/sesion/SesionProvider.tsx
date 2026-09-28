import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import {
  cerrarSesion, iniciarSesion, renovarSesion, suscribirSesion, vencerSesion, type Sesion, type UsuarioSesion,
} from '../../api/cliente';
import { SesionContext, type EstadoSesion } from './contexto';

const MINUTO = 60_000;
/** Cada cuánto se revisa la inactividad. */
const INTERVALO_REVISION = 15_000;
const EVENTOS_DE_ACTIVIDAD = ['pointerdown', 'keydown', 'wheel', 'touchstart'] as const;

/**
 * Estado de la sesión para toda la app.
 *
 * - Al arrancar intenta renovar con la cookie: si hay sesión, la persona sigue donde estaba.
 * - Mide la inactividad con MINUTOS_EXPIRACION_SESION y MINUTOS_AVISO_EXPIRACION (vienen del backend):
 *   avisa antes de vencer y, al vencer, cierra la sesión sin desmontar la pantalla (UI-02).
 * - Mientras hay actividad, renueva la cookie antes de la mitad de su vida para que no venza en medio de una carga larga.
 */
export function SesionProvider({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<EstadoSesion>('cargando');
  const [usuario, setUsuario] = useState<UsuarioSesion | null>(null);
  const [vencida, setVencida] = useState(false);
  const [avisoVencimiento, setAvisoVencimiento] = useState(false);
  const minutos = useRef({ expiracion: 60, aviso: 5 });
  // Se fijan al tomar la sesión.
  const ultimaActividad = useRef(0);
  const ultimaRenovacion = useRef(0);
  const arrancando = useRef(true);

  const tomarSesion = useCallback((sesion: Sesion) => {
    minutos.current = { expiracion: sesion.minutosExpiracionSesion, aviso: sesion.minutosAvisoExpiracion };
    ultimaRenovacion.current = Date.now();
    ultimaActividad.current = Date.now();
    setUsuario(sesion.usuario);
    setEstado('activa');
    setVencida(false);
    setAvisoVencimiento(false);
  }, []);

  useEffect(() => {
    const desuscribir = suscribirSesion((evento) => {
      if (evento.tipo === 'iniciada' || evento.tipo === 'renovada') tomarSesion(evento.sesion);
      if (evento.tipo === 'cerrada') {
        setUsuario(null);
        setEstado('anonima');
        setVencida(false);
      }
      // Al arrancar sin cookie no hay nada que avisar: simplemente no hay sesión.
      if (evento.tipo === 'vencida' && !arrancando.current) {
        setVencida(true);
        setAvisoVencimiento(false);
      }
    });
    renovarSesion()
      .catch(() => setEstado('anonima'))
      .finally(() => {
        arrancando.current = false;
      });
    return desuscribir;
  }, [tomarSesion]);

  // Inactividad: solo cuenta mientras hay una sesión activa y no vencida.
  useEffect(() => {
    if (estado !== 'activa' || vencida) return undefined;

    function registrarActividad() {
      ultimaActividad.current = Date.now();
      const mitadDeLaSesion = (minutos.current.expiracion * MINUTO) / 2;
      if (Date.now() - ultimaRenovacion.current > mitadDeLaSesion) {
        ultimaRenovacion.current = Date.now();
        renovarSesion().catch(() => undefined);
      }
    }

    const revisar = window.setInterval(() => {
      const inactivo = Date.now() - ultimaActividad.current;
      const { expiracion, aviso } = minutos.current;
      if (inactivo >= expiracion * MINUTO) {
        void vencerSesion();
      } else {
        setAvisoVencimiento(inactivo >= (expiracion - aviso) * MINUTO);
      }
    }, INTERVALO_REVISION);

    EVENTOS_DE_ACTIVIDAD.forEach((e) => window.addEventListener(e, registrarActividad, { passive: true }));
    return () => {
      window.clearInterval(revisar);
      EVENTOS_DE_ACTIVIDAD.forEach((e) => window.removeEventListener(e, registrarActividad));
    };
  }, [estado, vencida]);

  const valor = useMemo(
    () => ({
      estado,
      usuario,
      vencida,
      avisoVencimiento,
      ingresar: async (nombreUsuario: string, contrasena: string) => {
        await iniciarSesion(nombreUsuario, contrasena);
      },
      salir: () => cerrarSesion(),
      seguirTrabajando: async () => {
        ultimaActividad.current = Date.now();
        setAvisoVencimiento(false);
        await renovarSesion();
      },
    }),
    [estado, usuario, vencida, avisoVencimiento],
  );

  return <SesionContext.Provider value={valor}>{children}</SesionContext.Provider>;
}
