import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { Ficha } from '../../api/eventos';
import { backendFalso, json, montar, sesionDe } from '../../test/backendFalso';
import { fichaDePrueba, servicios } from '../../test/fichas';

const FICHA = fichaDePrueba({
  salon: { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', capacidad: null },
  observacionesInternas: 'Llamar después de las 18.',
  contactos: [{ id: 3, nombre: 'Mariela Ríos', vinculo: 'madre', telefono: '351 444-0000', email: null }],
  servicios: servicios({ 'Menú': 'Menú de pasos' }),
});

function backend(roles: Rol[], ficha: Ficha = FICHA) {
  return backendFalso(sesionDe(roles), ({ ruta }) => (ruta === '/eventos/5' ? json(200, ficha) : undefined));
}

/** La hoja vive en el body (fuera de la app) mientras dura la impresión. */
const hoja = () => document.querySelector('.ficha-impresa') as HTMLElement;

let imprimir: ReturnType<typeof vi.fn>;

beforeEach(() => {
  _reiniciarCliente();
  imprimir = vi.fn();
  window.print = imprimir as unknown as typeof window.print;
});

afterEach(() => {
  vi.unstubAllGlobals();
});

async function abrirEImprimir(marcar: string[] = []) {
  await userEvent.click(await screen.findByRole('button', { name: 'Imprimir ficha' }));
  const dialogo = screen.getByRole('dialog', { name: 'Imprimir ficha' });
  for (const etiqueta of marcar) await userEvent.click(within(dialogo).getByRole('checkbox', { name: new RegExp(`^${etiqueta}`) }));
  await userEvent.click(within(dialogo).getByRole('button', { name: 'Imprimir ficha' }));
}

describe('Imprimir ficha', () => {
  it('se ofrece a cualquier perfil que ve la ficha, aunque no tenga acciones', async () => {
    backend(['PLANNER']);
    montar('/eventos/5');
    expect(await screen.findByRole('button', { name: 'Imprimir ficha' })).toBeInTheDocument();
  });

  it('por defecto imprime evento, cliente, contactos y servicios, sin seña ni observaciones', async () => {
    backend(['VENDEDORA']);
    montar('/eventos/5');
    await abrirEImprimir();

    expect(imprimir).toHaveBeenCalledTimes(1);
    const ficha = within(hoja());
    expect(ficha.getByRole('heading', { level: 1, name: 'Bruno y Martina' })).toBeInTheDocument();
    expect(ficha.getByText('EV-2026-00005 · Señado')).toBeInTheDocument();
    expect(ficha.getByText('Martina Gómez')).toBeInTheDocument();
    expect(ficha.getByText('Mariela Ríos · 351 444-0000')).toBeInTheDocument();
    expect(ficha.getByText('Menú de pasos')).toBeInTheDocument();
    expect(ficha.queryByRole('heading', { name: 'Seña' })).not.toBeInTheDocument();
    expect(ficha.queryByText('Llamar después de las 18.')).not.toBeInTheDocument();
    expect(ficha.getByText(/^Impreso por /)).toBeInTheDocument();
  });

  it('usa el logo positivo del salón', async () => {
    backend(['VENDEDORA']);
    montar('/eventos/5');
    await abrirEImprimir();
    expect(within(hoja()).getByRole('img', { name: 'Santa Bárbara' })).toHaveAttribute('src', '/brand/salones/santa-barbara-positivo.png');
  });

  it('suma la seña y las observaciones si se eligen', async () => {
    backend(['VENDEDORA']);
    montar('/eventos/5');
    await abrirEImprimir(['Seña y pago', 'Observaciones internas']);

    const ficha = within(hoja());
    expect(ficha.getByText('$ 300.000,00')).toBeInTheDocument();
    expect(ficha.getByText('Llamar después de las 18.')).toBeInTheDocument();
  });

  it('sin importe en la ficha (perfil que no lo ve), la seña impresa tampoco lo tiene', async () => {
    backend(['PLANNER'], { ...FICHA, sena: { fecha: '2026-09-29', firmanteNombre: null, firmanteDni: '30111222', firmanteContacto: null } });
    montar('/eventos/5');
    await abrirEImprimir(['Seña y pago']);

    const ficha = within(hoja());
    expect(ficha.getByText('Fecha del pago')).toBeInTheDocument();
    expect(ficha.queryByText('Importe')).not.toBeInTheDocument();
  });

  it('sin seña registrada no ofrece incluirla', async () => {
    backend(['VENDEDORA'], { ...FICHA, estado: 'PRE_RESERVA', sena: null });
    montar('/eventos/5');
    await userEvent.click(await screen.findByRole('button', { name: 'Imprimir ficha' }));
    expect(screen.queryByRole('checkbox', { name: /^Seña y pago/ })).not.toBeInTheDocument();
  });

  it('al terminar la impresión saca la hoja', async () => {
    backend(['VENDEDORA']);
    montar('/eventos/5');
    await abrirEImprimir();
    expect(hoja()).not.toBeNull();
    act(() => { window.dispatchEvent(new Event('afterprint')); });
    expect(hoja()).toBeNull();
  });
});
