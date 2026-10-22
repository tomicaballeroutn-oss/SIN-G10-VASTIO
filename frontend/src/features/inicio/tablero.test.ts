import { describe, expect, it } from 'vitest';
import type { Rol } from '../../api/cliente';
import { HOY, OCTUBRE, PROXIMOS } from './fixtures';
import {
  alcanceDe, diasOcupados, eventosDelDia, libresDelDia, mesesDe, resumenSemana, semanaDe, sumarDias, tareasDe, tieneTareas,
} from './tablero';

const alcance = (roles: Rol[], id = 99) =>
  alcanceDe({ id, nombreCompleto: 'Prueba', nombreUsuario: 'prueba', roles, debeCambiarContrasena: false });
const SEMANA = semanaDe(HOY);

describe('fechas del Inicio', () => {
  it('la semana va de lunes a domingo', () => {
    expect(SEMANA).toEqual(['2026-10-19', '2026-10-20', '2026-10-21', '2026-10-22', '2026-10-23', '2026-10-24', '2026-10-25']);
    expect(semanaDe('2026-10-25')[0]).toBe('2026-10-19');
  });

  it('suma días y pide los meses que cruza', () => {
    expect(sumarDias('2026-10-30', 3)).toBe('2026-11-02');
    expect(mesesDe(semanaDe('2026-11-01'))).toEqual(['2026-10', '2026-11']);
  });
});

describe('resumen de la semana', () => {
  it('Dirección cuenta los eventos del complejo, sin bloqueos', () => {
    expect(resumenSemana(OCTUBRE.unidades, SEMANA, alcance(['DIRECCION']))).toEqual({
      porDia: [0, 1, 0, 1, 0, 2, 1], total: 5, confirmados: 1, contratados: 1, contratadosSinPlanner: 1, porCerrar: 3,
    });
  });

  it('la vendedora cuenta solo los que vendió', () => {
    const r = resumenSemana(OCTUBRE.unidades, SEMANA, alcance(['VENDEDORA'], 1));
    expect(r.total).toBe(3);
    expect(r.porDia).toEqual([0, 1, 0, 1, 0, 1, 0]);
  });

  it('la planner cuenta solo los que tiene asignados', () => {
    expect(resumenSemana(OCTUBRE.unidades, SEMANA, alcance(['PLANNER'], 3)).total).toBe(2);
  });
});

describe('agenda del Inicio', () => {
  it('el calendario marca reservado si hay alguna reserva firme y pre-reserva si solo hay pre-reservas', () => {
    expect(diasOcupados(OCTUBRE.unidades)).toEqual({
      '2026-10-20': 'reservado', '2026-10-22': 'reservado', '2026-10-24': 'reservado', '2026-10-25': 'prereserva', '2026-10-30': 'reservado',
    });
  });

  it('las unidades libres descuentan eventos y bloqueos', () => {
    expect(libresDelDia(OCTUBRE, OCTUBRE.unidades, '2026-10-24')).toEqual({ libres: 3, total: 6 });
    expect(libresDelDia(OCTUBRE, OCTUBRE.unidades, '2026-10-23')).toEqual({ libres: 6, total: 6 });
  });

  it('los eventos del día van por turno', () => {
    expect(eventosDelDia(PROXIMOS, '2026-10-24', alcance(['COMPRAS'])).map((e) => e.id)).toEqual([13, 12]);
    expect(eventosDelDia(PROXIMOS, '2026-10-24', alcance(['VENDEDORA'], 1)).map((e) => e.id)).toEqual([13]);
  });
});

describe('Te toca a vos', () => {
  const titulos = (roles: Rol[], id?: number) => tareasDe(PROXIMOS, alcance(roles, id)).map((t) => `${t.titulo} · ${t.eventoId}`);

  it('Dirección y Coordinación: firma de los señados y planner de los contratados sin planner', () => {
    expect(titulos(['COORDINACION'])).toEqual(['Asignar planner · 11', 'Registrar firma de contrato · 12']);
  });

  it('la vendedora: seña de sus pre-reservas', () => {
    expect(titulos(['VENDEDORA'], 1)).toEqual(['Registrar seña · 13']);
  });

  it('la planner: confirmar los contratados que tiene asignados', () => {
    expect(titulos(['PLANNER'], 3)).toEqual(['Confirmar evento · 16']);
  });

  it('Administración y Compras no tienen tareas en el Inicio', () => {
    expect(tieneTareas(['ADMINISTRACION'])).toBe(false);
    expect(tieneTareas(['COMPRAS'])).toBe(false);
    expect(tieneTareas(['VENDEDORA'])).toBe(true);
  });
});
