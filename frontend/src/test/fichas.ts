import type { Acciones, Ficha, ServicioFicha } from '../api/eventos';

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

/** Las categorías de servicio de V2, sin nada cargado. */
export const SERVICIOS_VACIOS: ServicioFicha[] = [
  ['Recepción', false, true], ['Plato principal', true, true], ['Postre', false, true], ['After', false, true],
  ['Menús especiales', false, true], ['Bodega', false, false], ['Tipo de barra', true, false], ['Técnica', false, false],
  ['Mobiliario', false, false], ['Extras', false, false],
].map(([categoria, requerida, cocina], i) => ({
  categoriaId: i + 1,
  categoria: categoria as string,
  activa: true,
  requeridaParaConfirmar: requerida as boolean,
  visibleEnCocina: cocina as boolean,
  descripcion: null,
  usuario: null,
  fechaModificacion: null,
}));

/** Evento señado de Lucía Ferreyra (id 5), con la planner Ana Sosa. Cada test cambia lo que necesita. */
export function fichaDePrueba(cambios: Partial<Ficha> = {}): Ficha {
  return {
    id: 5,
    codigo: 'EV-2026-00005',
    estado: 'SENADO',
    nombre: 'Bruno y Martina',
    tipo: { id: 1, nombre: 'Casamiento' },
    salon: { id: 1, codigo: 'avril', nombre: 'Avril' },
    fecha: '2026-11-14',
    turno: { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00' },
    cliente: { id: 7, nombre: 'Martina Gómez', documento: null, telefono: null, email: null },
    contactos: [],
    vendedora: { id: 1, nombre: 'Lucía Ferreyra' },
    planner: { id: 3, nombre: 'Ana Sosa' },
    cantidadInvitados: 180,
    invitadosDefinitivos: false,
    observacionesInternas: null,
    sena: { importe: 300000, fecha: '2026-09-29', firmanteNombre: null, firmanteDni: '30111222', firmanteContacto: null },
    fechaFirmaContrato: null,
    documentos: null,
    servicios: SERVICIOS_VACIOS,
    fechaCreacion: '2026-09-21T10:15:00-03:00',
    version: 1,
    acciones: SIN_ACCIONES,
    historial: [
      { tipo: 'ESTADO', estadoNuevo: 'PRE_RESERVA', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-21T10:15:00-03:00' },
    ],
    ...cambios,
  };
}

/** Las categorías de V2 con esos textos cargados (por nombre de categoría). */
export function servicios(textos: Record<string, string>, cambios: Partial<ServicioFicha> = {}): ServicioFicha[] {
  return SERVICIOS_VACIOS.map((s) => (textos[s.categoria] === undefined ? s : {
    ...s,
    descripcion: textos[s.categoria],
    usuario: { id: 3, nombre: 'Ana Sosa' },
    fechaModificacion: '2026-10-01T09:40:00-03:00',
    ...cambios,
  }));
}
