import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { Ficha } from '../../api/eventos';
import { backendFalso, json, montar, problema, sesionDe } from '../../test/backendFalso';
import { fichaDePrueba } from '../../test/fichas';

/** Señada con importe: quien la abre ve el importe. */
const SENADA = fichaDePrueba();
/** La misma ficha como la recibe la planner: sin importe ni moneda. */
const SIN_IMPORTE: Ficha = { ...SENADA, sena: { ...SENADA.sena!, importe: undefined, moneda: undefined } };

function backend(ficha: Ficha, roles: Rol[], comprobante: () => Response = pdf) {
  return backendFalso(sesionDe(roles), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, ficha);
    if (p.metodo === 'GET' && p.ruta === '/eventos/5/sena/comprobante') return comprobante();
    return undefined;
  });
}

function pdf() {
  return new Response(new Blob(['%PDF-1.7']), {
    status: 200,
    headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': "attachment; filename*=UTF-8''comprobante-sena-CS-2026-00001.pdf" },
  });
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('M-07 · comprobante interno de la seña', () => {
  it('quien ve el importe descarga el comprobante con la sesión y el nombre que manda el sistema', async () => {
    const fetchMock = backend(SENADA, ['VENDEDORA']);
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:comprobante'), revokeObjectURL: vi.fn() }));
    let descargado = '';
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      descargado = this.download;
    });
    const usuario = userEvent.setup();
    montar('/eventos/5');

    await usuario.click(await screen.findByRole('button', { name: 'Descargar comprobante' }));

    await vi.waitFor(() => expect(descargado).toBe('comprobante-sena-CS-2026-00001.pdf'));
    const pedido = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/eventos/5/sena/comprobante'));
    expect((pedido![1]!.headers as Record<string, string>).Authorization).toBe('Bearer token');
  });

  it('quien no ve el importe no tiene el botón', async () => {
    backend(SIN_IMPORTE, ['PLANNER']);
    montar('/eventos/5');

    await screen.findByRole('heading', { level: 1, name: 'Bruno y Martina' });
    expect(screen.getByRole('heading', { name: 'Seña' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Descargar comprobante' })).not.toBeInTheDocument();
  });

  it('si el sistema lo rechaza, lo dice al lado del botón', async () => {
    backend(SENADA, ['VENDEDORA'], () => problema(403, 'SIN_PERMISO', 'No tenés permiso para esta acción.'));
    const usuario = userEvent.setup();
    montar('/eventos/5');

    await usuario.click(await screen.findByRole('button', { name: 'Descargar comprobante' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No tenés permiso para esta acción.');
  });
});
