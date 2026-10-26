import { api, type Rol } from './cliente';

/** Consultan la orden de preparación (docs/sprint-4.md, decisión 22). La barra nunca la ve: operación a ciegas. */
export const ROLES_ORDEN: Rol[] = ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'];

export type EstadoOrden = 'SUGERIDA' | 'MODIFICADA' | 'CONFIRMADA' | 'DESCARTADA' | 'CERRADA';

/** De dónde salieron las botellas por asistente de la sugerencia (decisión 13). */
export type FuenteSugerencia = 'HISTORICO_TIPO' | 'PROPORCIONES' | 'HISTORICO_GENERAL' | 'SIN_DATOS';

export interface Persona {
  id: number;
  nombre: string;
}

/** Quién y cuándo. */
export interface Hecho {
  usuario: Persona | null;
  fechaHora: string;
}

export interface EventoOrden {
  id: number;
  codigo: string;
  nombre: string;
  estado: string;
  /** yyyy-mm-dd, fecha de la jornada. */
  fecha: string;
  salon: { id: number; codigo: string; nombre: string };
  /** Los previstos hoy: el total por segmento en egresados; si no, los invitados. */
  asistentes: number | null;
}

/** Cantidades en botellas; se muestran en cajas y botellas con `unidadesPorBulto`. */
export interface RenglonOrden {
  bebidaId: number;
  nombre: string;
  presentacion: string;
  unidad: string;
  unidadesPorBulto: number;
  activa: boolean;
  sugerida: number;
  aRetirar: number;
  /** Lo que tienen a retirar las órdenes vigentes de otros eventos de los próximos 15 días. */
  comprometido: number;
}

export interface Orden {
  id: number;
  estado: EstadoOrden;
  fuente: FuenteSugerencia;
  /** Eventos cerrados sobre los que se calculó (0 con proporciones o sin datos). */
  eventosBase: number;
  asistentesBase: number | null;
  margenPorcentaje: number;
  origen: string;
  /** null: la generó el sistema al confirmar el evento. */
  responsable: Persona | null;
  fechaCreacion: string;
  fechaCalculo: string;
  modificacion: Hecho | null;
  confirmacion: Hecho | null;
  reapertura: Hecho | null;
  /** Se manda al cambiarla, para no pisar cambios de otra persona. */
  version: number;
  renglones: RenglonOrden[];
}

export interface AccionesOrden {
  generar: boolean;
  recalcular: boolean;
}

export interface OrdenDelEvento {
  evento: EventoOrden;
  /** La vigente o, si no hay, la última (cerrada o descartada); null si el evento nunca tuvo. */
  orden: Orden | null;
  /** Desde que se calculó cambiaron los invitados o la asistencia prevista. */
  asistentesCambiaron: boolean;
  acciones: AccionesOrden;
}

/** Un evento Confirmado o En curso y el estado de su orden vigente (null: no tiene). */
export interface OrdenDeLaLista {
  evento: EventoOrden;
  estado: EstadoOrden | null;
  bebidas: number | null;
  asistentesCambiaron: boolean;
}

/** Botellas por asistente de una bebida en un tipo de evento (Parámetros, decisión 14). */
export interface Proporcion {
  id: number;
  tipoEventoId: number;
  bebidaId: number;
  nombre: string;
  presentacion: string;
  activa: boolean;
  botellasPorAsistente: number;
}

export const ordenes = {
  listado: () => api<OrdenDeLaLista[]>('/ordenes-preparacion'),
  consultar: (eventoId: number) => api<OrdenDelEvento>(`/eventos/${eventoId}/orden-preparacion`),
  generar: (eventoId: number) => api<OrdenDelEvento>(`/eventos/${eventoId}/orden-preparacion`, { metodo: 'POST' }),
  recalcular: (eventoId: number, version: number) =>
    api<OrdenDelEvento>(`/eventos/${eventoId}/orden-preparacion/recalculo`, { metodo: 'POST', cuerpo: { version } }),
};

export const proporciones = {
  delTipo: (tipoEventoId: number) => api<Proporcion[]>(`/proporciones-consumo?tipoEventoId=${tipoEventoId}`),
  guardar: (tipoEventoId: number, bebidaId: number, botellasPorAsistente: number) =>
    api<Proporcion>('/proporciones-consumo', { metodo: 'PUT', cuerpo: { tipoEventoId, bebidaId, botellasPorAsistente } }),
  quitar: (id: number) => api<void>(`/proporciones-consumo/${id}`, { metodo: 'DELETE' }),
};

export const ESTADO_ORDEN: Record<EstadoOrden, { label: string; tone: 'neutral' | 'info' | 'warning' | 'success' | 'danger' }> = {
  SUGERIDA: { label: 'Sugerida', tone: 'info' },
  MODIFICADA: { label: 'Modificada', tone: 'warning' },
  CONFIRMADA: { label: 'Confirmada', tone: 'success' },
  DESCARTADA: { label: 'Descartada', tone: 'danger' },
  CERRADA: { label: 'Cerrada', tone: 'neutral' },
};
