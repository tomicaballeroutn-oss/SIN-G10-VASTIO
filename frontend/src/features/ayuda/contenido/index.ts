import { AGENDA } from './agenda';
import { BEBIDAS } from './bebidas';
import { GESTION } from './gestion';
import { PRIMEROS_PASOS } from './primerosPasos';
import type { ContenidoAyuda } from '../modelo';

export const CONTENIDO: Record<string, ContenidoAyuda> = {
  ...PRIMEROS_PASOS,
  ...AGENDA,
  ...BEBIDAS,
  ...GESTION,
};

export const TERMINOS = [
  { termino: 'Unidad comercializable', definicion: 'Combinación de salón, fecha y turno.' },
  { termino: 'Pre-reserva', definicion: 'Fecha apartada durante una negociación.' },
  { termino: 'Seña', definicion: 'Pago que convierte una pre-reserva en una reserva firme.' },
  { termino: 'Bloqueo', definicion: 'Período en el que una unidad queda fuera de la venta.' },
  { termino: 'Depósito madre', definicion: 'Ubicación principal de almacenamiento de bebidas.' },
  { termino: 'Depósito de transición', definicion: 'Ubicación intermedia que abastece una barra.' },
  { termino: 'Remanente', definicion: 'Bebida cerrada que permanece en la barra para el siguiente evento.' },
  { termino: 'Merma', definicion: 'Reducción de existencias por rotura u otro motivo registrado.' },
  { termino: 'Recuento', definicion: 'Conteo físico utilizado para corregir diferencias de stock.' },
  { termino: 'Consumo real', definicion: 'Cantidad entregada, incluido el remanente heredado, menos lo devuelto y el remanente que queda para la jornada siguiente.' },
] as const;
