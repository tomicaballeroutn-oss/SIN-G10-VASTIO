import { api } from './cliente';
import type { RenglonIngreso } from './ingresos';

/** Ubicación sin saldos: la barra opera a ciegas. */
export interface UbicacionResumen {
  id: number;
  nombre: string;
  tipo: 'DEPOSITO' | 'TRANSICION' | 'BARRA';
}

/** Sin datos comerciales. `estado`: el código del evento (EN_CURSO, …). */
export interface EventoDeBarra {
  id: number;
  codigo: string;
  nombre: string;
  estado: string;
}

export interface SalonResumen {
  id: number;
  codigo: string;
  nombre: string;
}

export interface EventoDeLaJornada {
  evento: EventoDeBarra;
  salon: SalonResumen;
  barras: UbicacionResumen[];
}

/** Para el buscador y el lector de códigos. */
export interface BebidaParaRetiro {
  id: number;
  nombre: string;
  presentacion: string;
  unidad: string;
  unidadesPorBulto: number;
  codigos: { codigo: string; unidades: number }[];
}

/** Un retiro con quién y cuándo. Cantidades en botellas. */
export interface Retiro {
  tipo: 'SALIDA_BARRA' | 'RETIRO_ADICIONAL';
  origen: UbicacionResumen;
  renglones: RenglonIngreso[];
  usuario: { id: number; nombre: string };
  fechaHora: string;
}

/** Lo ya entregado de una bebida a la barra para el evento, en botellas. */
export type Entregado = RenglonIngreso;

/**
 * La pantalla de la barra para un evento. `estadoEntrega` null: todavía no hubo retiros (el próximo abre la entrega).
 * `depositoMadre` solo si la barra permite el retiro directo.
 */
export interface Entrega {
  evento: EventoDeBarra;
  salon: SalonResumen;
  barra: UbicacionResumen;
  origen: UbicacionResumen;
  depositoMadre: UbicacionResumen | null;
  estadoEntrega: 'ABIERTA' | 'CERRADA' | null;
  yaEntregado: Entregado[];
  retiros: Retiro[];
  bebidas: BebidaParaRetiro[];
  /** Con la entrega abierta: las bebidas de la barra, solo los nombres (se declara a ciegas). */
  aDevolver: BebidaParaRetiro[];
  /** Con la entrega abierta y una barra abastecida por una transición: las bebidas que hay ahí. */
  enTransicion: BebidaParaRetiro[];
}

export interface DatosRetiro {
  directo: boolean;
  renglones: { bebidaId: number; cantidad: number }[];
}

/** En botellas enteras. Una bebida que no va cuenta como 0 y 0: se consumió todo. */
export interface DatosDevolucion {
  renglones: { bebidaId: number; devuelto: number; remanente: number }[];
  transicion: { bebidaId: number; cantidad: number }[];
}

/** Consumo real de una bebida en la barra: entregado − devuelto − remanente, en botellas. */
export interface ConsumoBebida {
  bebidaId: number;
  nombre: string;
  presentacion: string;
  unidad: string;
  unidadesPorBulto: number;
  entregado: number;
  devuelto: number;
  remanente: number;
  consumo: number;
}

/** Recién después de registrar llega el consumo. */
export interface Devolucion {
  entrega: Entrega;
  consumo: ConsumoBebida[];
  desdeTransicion: Entregado[];
}

export const entregas = {
  jornada: () => api<EventoDeLaJornada[]>('/entregas/jornada'),
  pantalla: (eventoId: number, barraId: number) => api<Entrega>(`/eventos/${eventoId}/entregas/${barraId}`),
  retirar: (eventoId: number, barraId: number, datos: DatosRetiro) =>
    api<Entrega>(`/eventos/${eventoId}/entregas/${barraId}/retiros`, { metodo: 'POST', cuerpo: datos }),
  devolver: (eventoId: number, barraId: number, datos: DatosDevolucion) =>
    api<Devolucion>(`/eventos/${eventoId}/entregas/${barraId}/devolucion`, { metodo: 'POST', cuerpo: datos }),
};
