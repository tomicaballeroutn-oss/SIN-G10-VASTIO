import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Salon } from '../../api/catalogos';
import { _reiniciarCliente } from '../../api/cliente';
import type { Ubicacion } from '../../api/ubicaciones';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';

const SALONES: Salon[] = [
  { id: 1, codigo: 'avril', nombre: 'Avril', capacidad: null, activo: true },
  { id: 2, codigo: 'club', nombre: 'Club de Campo', capacidad: 250, activo: true },
  { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', capacidad: null, activo: true },
];

const DEPOSITO: Ubicacion = { id: 1, nombre: 'Depósito principal', tipo: 'DEPOSITO', salon: null, abastecimiento: null, permiteRetiroDirecto: false, activo: true };
const TRANSICION: Ubicacion = {
  id: 5, nombre: 'Transición Club', tipo: 'TRANSICION', salon: { id: 2, codigo: 'club', nombre: 'Club de Campo' }, abastecimiento: null,
  permiteRetiroDirecto: false, activo: true,
};
const barra = (id: number, nombre: string, salon: Ubicacion['salon']): Ubicacion => ({
  id, nombre, tipo: 'BARRA', salon, abastecimiento: { id: 1, nombre: 'Depósito principal', tipo: 'DEPOSITO' }, permiteRetiroDirecto: false, activo: true,
});
const UBICACIONES: Ubicacion[] = [
  DEPOSITO,
  TRANSICION,
  barra(2, 'Barra Avril', { id: 1, codigo: 'avril', nombre: 'Avril' }),
  barra(3, 'Barra Club de Campo', { id: 2, codigo: 'club', nombre: 'Club de Campo' }),
];

function backend({ metodo, ruta, cuerpo }: Pedido) {
  if (metodo === 'GET' && ruta === '/ubicaciones') return json(200, UBICACIONES);
  if (metodo === 'GET' && ruta === '/salones') return json(200, SALONES);
  if (metodo === 'POST' && ruta === '/ubicaciones') {
    const datos = cuerpo as { nombre: string; salonId: number };
    if (datos.salonId === 2) return problema(409, 'MAXIMO_BARRAS', 'Club de Campo ya tiene 1 barra activa, que es el máximo.');
    return json(201, { ...barra(9, datos.nombre, { id: 1, codigo: 'avril', nombre: 'Avril' }) });
  }
  if (metodo === 'PUT' && ruta === '/ubicaciones/3') {
    return json(200, { ...UBICACIONES[3], abastecimiento: { id: 5, nombre: 'Transición Club', tipo: 'TRANSICION' }, permiteRetiroDirecto: true });
  }
  if (metodo === 'POST' && ruta === '/ubicaciones/2/baja') return json(200, { ...UBICACIONES[2], activo: false });
  return undefined;
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-26 · ubicaciones de stock (sección de Parámetros)', () => {
  it('lista depósito, transiciones y barras con su salón y origen', async () => {
    backendFalso(sesionDe(['ADMINISTRACION']), backend);
    montar('/parametros?seccion=ubicaciones');

    expect(await screen.findByText('Depósitos de transición')).toBeInTheDocument();
    expect(screen.getByText('Barras')).toBeInTheDocument();
    expect(screen.getByText('Avril · se abastece de Depósito principal')).toBeInTheDocument();
  });

  it('una barra nueva se abastece del depósito madre y no se ofrece otro depósito madre', async () => {
    const fetchMock = backendFalso(sesionDe(['ADMINISTRACION']), backend);
    const persona = userEvent.setup();
    montar('/parametros?seccion=ubicaciones');

    await persona.click(await screen.findByRole('button', { name: 'Agregar ubicación' }));
    const tipo = screen.getByLabelText('Tipo');
    expect(within(tipo).queryByRole('option', { name: 'Depósito madre' })).not.toBeInTheDocument();
    await persona.selectOptions(tipo, 'BARRA');
    await persona.type(screen.getByLabelText('Nombre'), 'Barra Avril 2');
    await persona.selectOptions(screen.getByLabelText('Salón'), '1');
    expect(screen.queryByLabelText(/Permitir retiro directo/)).not.toBeInTheDocument();
    const formulario = screen.getByLabelText('Nombre').closest('form') as HTMLElement;
    await persona.click(within(formulario).getByRole('button', { name: 'Agregar ubicación' }));

    expect(await screen.findByText('Cambios guardados.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toEqual({
      nombre: 'Barra Avril 2', tipo: 'BARRA', salonId: 1, abastecimientoId: 1, permiteRetiroDirecto: false,
    });
  });

  it('muestra el límite de barras del salón', async () => {
    backendFalso(sesionDe(['DIRECCION']), backend);
    const persona = userEvent.setup();
    montar('/parametros?seccion=ubicaciones');

    await persona.click(await screen.findByRole('button', { name: 'Agregar ubicación' }));
    await persona.selectOptions(screen.getByLabelText('Tipo'), 'BARRA');
    await persona.type(screen.getByLabelText('Nombre'), 'Barra Club 2');
    await persona.selectOptions(screen.getByLabelText('Salón'), '2');
    const formulario = screen.getByLabelText('Nombre').closest('form') as HTMLElement;
    await persona.click(within(formulario).getByRole('button', { name: 'Agregar ubicación' }));

    expect(await screen.findByText('Club de Campo ya tiene 1 barra activa, que es el máximo.')).toBeInTheDocument();
  });

  it('una barra abastecida por una transición puede habilitar el retiro directo', async () => {
    const fetchMock = backendFalso(sesionDe(['COORDINACION']), backend);
    const persona = userEvent.setup();
    montar('/parametros?seccion=ubicaciones');

    await persona.click(await screen.findByRole('button', { name: 'Editar Barra Club de Campo' }));
    await persona.selectOptions(screen.getByLabelText('Se abastece desde'), '5');
    await persona.click(screen.getByLabelText(/Permitir retiro directo/));
    await persona.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText(/con retiro directo del depósito madre/)).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toMatchObject({ abastecimientoId: 5, permiteRetiroDirecto: true });
  });

  it('da de baja una barra y el depósito madre no tiene esa opción', async () => {
    backendFalso(sesionDe(['ADMINISTRACION']), backend);
    const persona = userEvent.setup();
    montar('/parametros?seccion=ubicaciones');

    await persona.click(await screen.findByRole('button', { name: 'Editar Depósito principal' }));
    expect(screen.queryByRole('button', { name: 'Dar de baja' })).not.toBeInTheDocument();

    await persona.click(screen.getByRole('button', { name: 'Editar Barra Avril' }));
    await persona.click(screen.getByRole('button', { name: 'Dar de baja' }));
    const fila = screen.getByRole('button', { name: 'Editar Barra Avril' }).closest('li') as HTMLElement;
    expect(await within(fila).findByText('De baja')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reactivar' })).toBeInTheDocument();
  });
});
