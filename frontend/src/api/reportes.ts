import { api } from './cliente';

export interface FiltroReporteOcupacion {
  desde: string;
  hasta: string;
  salonId: string;
  tipoEventoId: string;
  vendedoraId: string;
  regimen: string;
}

export interface ReporteOcupacion {
  desde: string;
  hasta: string;
  regimen: string | null;
  resumen: { unidadesOcupadas: number; ocupacionPromedio: number; eventosRealizados: number };
  porSalon: { salonId: number; codigo: string; salon: string; unidadesOcupadas: number; unidadesDisponibles: number; ocupacion: number }[];
  porVendedoraYTipo: { vendedoraId: number; vendedora: string; tipoEventoId: number; tipoEvento: string; eventosIncluidos: number }[];
  filtros: {
    salones: { id: number; nombre: string }[];
    tiposEvento: { id: number; nombre: string }[];
    vendedoras: { id: number; nombre: string }[];
  };
}

export function consultarReporteOcupacion(filtros: FiltroReporteOcupacion): Promise<ReporteOcupacion> {
  const params = new URLSearchParams({ desde: filtros.desde, hasta: filtros.hasta });
  if (filtros.salonId) params.set('salonId', filtros.salonId);
  if (filtros.tipoEventoId) params.set('tipoEventoId', filtros.tipoEventoId);
  if (filtros.vendedoraId) params.set('vendedoraId', filtros.vendedoraId);
  if (filtros.regimen) params.set('regimen', filtros.regimen);
  return api<ReporteOcupacion>(`/reportes/ocupacion?${params.toString()}`);
}
