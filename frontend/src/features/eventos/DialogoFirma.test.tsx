import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { hoyEnCordoba } from '../../api/agenda';
import { _reiniciarCliente, nombreDeArchivo, type Rol } from '../../api/cliente';
import type { Ficha } from '../../api/eventos';
import { backendFalso, json, montar, problema, sesionDe, type Pedido } from '../../test/backendFalso';
import { SIN_ACCIONES } from '../../test/fichas';

const SENADO: Ficha = {
  id: 5,
  codigo: 'EV-2026-00005',
  estado: 'SENADO',
  nombre: 'Bruno y Martina',
  tipo: { id: 1, nombre: 'Casamiento' },
  salon: { id: 1, codigo: 'avril', nombre: 'Avril', capacidad: null },
  fecha: '2026-11-14',
  turno: { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00' },
  horaInicio: null,
  reprogramadoDesde: null,
  cliente: { id: 7, nombre: 'Martina Gómez', documento: null, telefono: null, email: null },
  contactos: [],
  vendedora: { id: 1, nombre: 'Lucía Ferreyra' },
  planner: null,
  cantidadInvitados: 180,
  invitadosDefinitivos: false,
  observacionesInternas: null,
  sena: { importe: 300000, fecha: '2026-09-29', firmanteNombre: null, firmanteDni: '30111222', firmanteContacto: null },
  fechaFirmaContrato: null,
  cancelacion: null,
  documentos: [],
  servicios: [],
  requisitosConfirmacion: null,
  fechaCreacion: '2026-09-21T10:15:00-03:00',
  version: 1,
  acciones: { ...SIN_ACCIONES, modificar: true, registrarFirma: true },
  historial: [
    { tipo: 'ESTADO', estadoNuevo: 'PRE_RESERVA', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-21T10:15:00-03:00' },
    { tipo: 'ESTADO', estadoAnterior: 'PRE_RESERVA', estadoNuevo: 'SENADO', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-29T16:40:00-03:00' },
  ],
};

const CONTRATADO: Ficha = {
  ...SENADO,
  estado: 'CONTRATADO',
  version: 2,
  fechaFirmaContrato: '2026-10-02',
  cancelacion: null,
  documentos: [{
    id: 40, tipo: 'CONTRATO', nombreArchivo: 'contrato firmado.pdf', mimeType: 'application/pdf', tamanoBytes: 850_000,
    usuario: { id: 2, nombre: 'Melina Sifón' }, fechaCarga: '2026-10-02T11:00:00-03:00',
  }],
  acciones: { ...SIN_ACCIONES, modificar: true },
  historial: [
    ...SENADO.historial,
    { tipo: 'ESTADO', estadoAnterior: 'SENADO', estadoNuevo: 'CONTRATADO', usuario: { id: 2, nombre: 'Melina Sifón' }, fechaHora: '2026-10-02T11:00:00-03:00' },
  ],
};

function backend(ficha: Ficha = SENADO, alRegistrar: (p: Pedido) => Response = () => json(200, CONTRATADO), roles: Rol[] = ['COORDINACION']) {
  return backendFalso(sesionDe(roles, 'Melina Sifón', 2), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, ficha);
    if (p.metodo === 'POST' && p.ruta === '/eventos/5/contrato') return alRegistrar(p);
    if (p.metodo === 'GET' && p.ruta === '/eventos/5/documentos/40') {
      return new Response(new Blob(['%PDF-1.7']), {
        status: 200,
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': "attachment; filename*=UTF-8''contrato%20firmado.pdf" },
      });
    }
    return undefined;
  });
}

/** El formulario que se mandó al registrar la firma. */
function formularioEnviado(fetchMock: ReturnType<typeof backend>): FormData {
  const llamada = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/contrato') && init?.method === 'POST');
  return llamada![1]!.body as FormData;
}

const pdf = (nombre: string, bytes = 1000) => new File([new Uint8Array(bytes)], nombre, { type: 'application/pdf' });
const jpg = (nombre: string) => new File([new Uint8Array(500)], nombre, { type: 'image/jpeg' });

async function abrir() {
  const usuario = userEvent.setup();
  montar('/eventos/5');
  await usuario.click(await screen.findByRole('button', { name: 'Registrar firma' }));
  return { usuario, dialogo: screen.getByRole('dialog', { name: 'Registrar firma de contrato' }) };
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-12 · registrar firma de contrato', () => {
  it('adjunta las hojas del contrato y la ficha pasa a Contratado', async () => {
    const fetchMock = backend();
    const { usuario, dialogo } = await abrir();

    expect(within(dialogo).getByLabelText('Fecha de firma')).toHaveValue(hoyEnCordoba());
    await usuario.upload(within(dialogo).getByLabelText('Contrato digitalizado'), [jpg('hoja 1.jpg'), jpg('hoja 2.jpg')]);
    expect(within(dialogo).getByText('hoja 1.jpg')).toBeInTheDocument();
    expect(within(dialogo).getByText('hoja 2.jpg')).toBeInTheDocument();
    await usuario.click(within(dialogo).getByRole('button', { name: 'Registrar firma' }));

    expect(await screen.findByText('Firma registrada. El evento pasó a Contratado.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByText('Contratado')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Registrar firma' })).not.toBeInTheDocument();
    const enviado = formularioEnviado(fetchMock);
    expect(enviado.get('fechaFirma')).toBe(hoyEnCordoba());
    expect((enviado.getAll('archivos') as File[]).map((a) => a.name)).toEqual(['hoja 1.jpg', 'hoja 2.jpg']);
  });

  it('se puede quitar un archivo antes de registrar', async () => {
    const fetchMock = backend();
    const { usuario, dialogo } = await abrir();

    await usuario.upload(within(dialogo).getByLabelText('Contrato digitalizado'), [pdf('equivocado.pdf'), pdf('contrato.pdf')]);
    await usuario.click(within(dialogo).getByRole('button', { name: 'Quitar equivocado.pdf' }));
    await usuario.click(within(dialogo).getByRole('button', { name: 'Registrar firma' }));

    await screen.findByText('Firma registrada. El evento pasó a Contratado.');
    expect((formularioEnviado(fetchMock).getAll('archivos') as File[]).map((a) => a.name)).toEqual(['contrato.pdf']);
  });

  it('sin archivos o con uno de más de 10 MB avisa sin llamar al sistema', async () => {
    const fetchMock = backend();
    const { usuario, dialogo } = await abrir();

    await usuario.click(within(dialogo).getByRole('button', { name: 'Registrar firma' }));
    expect(within(dialogo).getByLabelText('Contrato digitalizado')).toHaveAccessibleDescription('Adjuntá el contrato digitalizado.');

    await usuario.upload(within(dialogo).getByLabelText('Contrato digitalizado'), pdf('escaneo.pdf', 10 * 1024 * 1024 + 1));
    await usuario.click(within(dialogo).getByRole('button', { name: 'Registrar firma' }));
    expect(within(dialogo).getByText(/El archivo escaneo.pdf pesa más de 10 MB/)).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/contrato'))).toBe(false);
  });

  it('una fecha anterior a la seña se marca en el campo', async () => {
    backend(SENADO, () => problema(422, 'FECHA_FIRMA_ANTERIOR_A_SENA', 'La fecha de firma no puede ser anterior a la seña (2026-09-29).'));
    const { usuario, dialogo } = await abrir();

    await usuario.upload(within(dialogo).getByLabelText('Contrato digitalizado'), pdf('contrato.pdf'));
    await usuario.click(within(dialogo).getByRole('button', { name: 'Registrar firma' }));

    expect(await within(dialogo).findByText(/no puede ser anterior a la seña/)).toBeInTheDocument();
    expect(within(dialogo).getByLabelText('Fecha de firma')).toHaveAccessibleDescription(/no puede ser anterior a la seña/);
  });

  it('un archivo que no es PDF ni imagen se marca en el selector', async () => {
    backend(SENADO, () => problema(422, 'FORMATO_NO_ADMITIDO', 'El archivo contrato.pdf no es PDF, JPG ni PNG. Adjuntá el contrato en alguno de esos formatos.'));
    const { usuario, dialogo } = await abrir();

    await usuario.upload(within(dialogo).getByLabelText('Contrato digitalizado'), pdf('contrato.pdf'));
    await usuario.click(within(dialogo).getByRole('button', { name: 'Registrar firma' }));

    expect(await within(dialogo).findByText(/no es PDF, JPG ni PNG/)).toBeInTheDocument();
  });

  it('solo aparece para quien puede registrarla', async () => {
    backend({ ...SENADO, acciones: { ...SIN_ACCIONES, modificar: true } }, undefined, ['VENDEDORA']);
    montar('/eventos/5');

    await screen.findByRole('heading', { level: 1, name: 'Bruno y Martina' });
    expect(screen.queryByRole('button', { name: 'Registrar firma' })).not.toBeInTheDocument();
  });
});

describe('legajo del evento', () => {
  it('muestra la fecha de firma, los documentos y la firma en el historial', async () => {
    backend(CONTRATADO);
    const usuario = userEvent.setup();
    montar('/eventos/5');

    const contrato = (await screen.findByRole('heading', { name: 'Contrato' })).closest('section') as HTMLElement;
    expect(within(contrato).getByText('vie 2 oct 2026')).toBeInTheDocument();

    await usuario.click(screen.getByRole('tab', { name: /Documentos/ }));
    expect(screen.getByText('contrato firmado.pdf')).toBeInTheDocument();
    expect(screen.getByText('830 KB')).toBeInTheDocument();

    await usuario.click(screen.getByRole('tab', { name: /Historial/ }));
    expect(screen.getByText('Contrato firmado')).toBeInTheDocument();
  });

  it('descarga el contrato con la sesión', async () => {
    const fetchMock = backend(CONTRATADO);
    const crear = vi.fn(() => 'blob:contrato');
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: crear, revokeObjectURL: vi.fn() }));
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=documentos');

    await usuario.click(await screen.findByRole('button', { name: 'Descargar contrato firmado.pdf' }));

    await vi.waitFor(() => expect(click).toHaveBeenCalled());
    expect(crear).toHaveBeenCalled();
    click.mockRestore();
    const pedido = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/documentos/40'));
    expect((pedido![1]!.headers as Record<string, string>).Authorization).toBe('Bearer token');
  });

  it('quien no ve el legajo no tiene la pestaña Documentos', async () => {
    backend({ ...CONTRATADO, documentos: null }, undefined, ['PLANNER']);
    montar('/eventos/5?pestana=documentos');

    await screen.findByRole('heading', { level: 1, name: 'Bruno y Martina' });
    expect(screen.queryByRole('tab', { name: /Documentos/ })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Contrato' })).toBeInTheDocument();
  });
});

describe('nombreDeArchivo', () => {
  it.each([
    ["attachment; filename=\"contrato.pdf\"; filename*=UTF-8''contrato%20firmado.pdf", 'contrato firmado.pdf'],
    ['attachment; filename="hoja.jpg"', 'hoja.jpg'],
    [null, null],
  ])('%s → %s', (cabecera, esperado) => {
    expect(nombreDeArchivo(cabecera)).toBe(esperado);
  });
});
