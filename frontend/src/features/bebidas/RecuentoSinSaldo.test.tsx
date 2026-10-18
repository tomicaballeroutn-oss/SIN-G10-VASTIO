import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Bebida } from '../../api/bebidas';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { RenglonStock, UbicacionConsultable } from '../../api/stock';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';

const CATALOGO: Bebida[] = [
  { id: 1, nombre: 'Fernet', presentacion: '750 ml', tipo: { id: 3, nombre: 'Destilado' }, unidad: { id: 1, nombre: 'Caja', esBotella: false },
    unidadesPorBulto: 6, stockMinimo: null, codigos: [{ codigo: '7790000000001', unidades: 1 }], proveedorHabitual: null, activo: true, fechaBaja: null },
  { id: 2, nombre: 'Gin inactivo', presentacion: '750 ml', tipo: { id: 3, nombre: 'Destilado' }, unidad: { id: 1, nombre: 'Caja', esBotella: false },
    unidadesPorBulto: 6, stockMinimo: null, codigos: [], proveedorHabitual: null, activo: false, fechaBaja: '2026-10-01' },
];

function renglon(b: Bebida, cantidad: number): RenglonStock {
  return { bebida: { id: b.id, nombre: b.nombre, presentacion: b.presentacion, tipo: b.tipo.nombre,
    unidad: b.unidad.nombre, unidadesPorBulto: b.unidadesPorBulto, activo: b.activo }, cantidad };
}

function escenario(tipo: UbicacionConsultable['ubicacion']['tipo'] = 'BARRA', iniciales: RenglonStock[] = [], barra = false) {
  const ubicacion = { id: 4, nombre: 'Ubicación de prueba', tipo };
  let renglones = [...iniciales];
  const movimientos: number[] = [];
  const atender = ({ metodo, ruta, cuerpo }: Pedido): Response | undefined => {
    if (ruta === '/stock/ubicaciones') return json(200, [{ ubicacion, ...(barra ? { evento: { id: 5, codigo: 'EV-5', nombre: 'Evento de prueba' } } : {}) }]);
    if (ruta === '/stock?ubicacionId=4') return json(200, { ubicacion, renglones });
    if (ruta === '/stock') return json(200, { ubicacion: null, renglones });
    if (ruta === '/bebidas') return json(200, CATALOGO);
    if (ruta === '/motivos') return json(200, [{ id: 12, ambito: 'AJUSTE', nombre: 'Recuento físico', activo: true }]);
    if (metodo === 'POST' && ruta === '/ajustes/recuento') {
      const datos = cuerpo as { bebidaId: number; cantidadContada: number };
      const anterior = renglones.find((r) => r.bebida.id === datos.bebidaId)?.cantidad ?? 0;
      const registrado = datos.cantidadContada !== anterior;
      if (registrado) {
        movimientos.push(datos.cantidadContada - anterior);
        renglones = [...renglones.filter((r) => r.bebida.id !== datos.bebidaId), renglon(CATALOGO.find((b) => b.id === datos.bebidaId)!, datos.cantidadContada)];
      }
      return json(200, { registrado, movimientoId: registrado ? 1 : null, tipo: registrado ? 'AJUSTE' : null,
        saldoAnterior: anterior, saldo: datos.cantidadContada, diferencia: datos.cantidadContada - anterior, avisoSaldoNegativo: false });
    }
    return undefined;
  };
  return { atender, movimientos };
}

beforeEach(() => _reiniciarCliente());
afterEach(() => vi.unstubAllGlobals());

async function abrirBusqueda(persona: ReturnType<typeof userEvent.setup>) {
  const boton = await screen.findByRole('button', { name: 'Registrar recuento de bebida' });
  await waitFor(() => expect(boton).toBeEnabled());
  await persona.click(boton);
}

async function elegir(persona: ReturnType<typeof userEvent.setup>, buscar = 'fer') {
  await abrirBusqueda(persona);
  await persona.type(await screen.findByLabelText('Buscar bebida para el recuento'), buscar);
  await persona.click(await screen.findByRole('option', { name: /Fernet/ }));
  return screen.findByRole('dialog', { name: 'Registrar recuento' });
}

describe('Recuento desde el catálogo sin saldo previo', () => {
  it.each(['DEPOSITO', 'TRANSICION', 'BARRA'] as const)('permite contar seis botellas ausentes en %s', async (tipo) => {
    const e = escenario(tipo);
    const fetchMock = backendFalso(sesionDe(['ADMINISTRACION']), e.atender);
    const persona = userEvent.setup();
    montar('/existencias');
    await screen.findByText('No hay bebidas en esta ubicación');
    const dialogo = await elegir(persona);
    expect(within(dialogo).getByText('0 botellas')).toBeInTheDocument();
    expect(within(dialogo).getByText('Coincide con el saldo: no hay nada para registrar.')).toBeInTheDocument();
    await persona.click(within(dialogo).getAllByRole('button', { name: 'Sumar 1' })[0]);
    expect(within(dialogo).getByText('Se registra un ajuste de +1 caja.')).toBeInTheDocument();
    await persona.click(within(dialogo).getByRole('button', { name: 'Registrar recuento' }));
    expect(await screen.findByText('Recuento de Fernet 750 ml registrado: ajuste de +1 caja en Ubicación de prueba.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toEqual({ ubicacionId: 4, bebidaId: 1, cantidadContada: 6, motivoId: 12, detalle: '' });
    expect(e.movimientos).toEqual([6]);
    expect(await screen.findByText('Fernet')).toBeInTheDocument();
  });

  it('contar cero sobre saldo cero conserva la respuesta sin movimiento', async () => {
    const e = escenario();
    backendFalso(sesionDe(['COMPRAS']), e.atender);
    const persona = userEvent.setup();
    montar('/existencias');
    const dialogo = await elegir(persona);
    await persona.click(within(dialogo).getByRole('button', { name: 'Registrar recuento' }));
    expect(await screen.findByText('El recuento de Fernet 750 ml coincide con el saldo: no hay nada para registrar.')).toBeInTheDocument();
    expect(e.movimientos).toEqual([]);
    expect(screen.getByText('No hay bebidas en esta ubicación')).toBeInTheDocument();
  });

  it.each(['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'] as Rol[])('ofrece la búsqueda a %s sobre una barra', async (rol) => {
    const e = escenario();
    const fetchMock = backendFalso(sesionDe([rol]), e.atender);
    const persona = userEvent.setup();
    montar('/existencias');
    await elegir(persona, '7790000000001');
    expect(pedidos(fetchMock).some((p) => p.ruta === '/bebidas')).toBe(true);
  });

  it('Barra solo consulta su lista y no pide el catálogo ni recibe acciones de ajuste', async () => {
    const e = escenario('BARRA', [renglon(CATALOGO[0], 9)], true);
    const fetchMock = backendFalso(sesionDe(['BARRA']), e.atender);
    montar('/existencias');
    expect(await screen.findByText('1 caja y 3 botellas')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Registrar recuento|Declarar rotura/ })).not.toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.ruta === '/bebidas' || p.ruta === '/motivos')).toBe(false);
    expect(screen.queryByText('Gin inactivo')).not.toBeInTheDocument();
  });

  it('no ofrece el recuento en el total del complejo', async () => {
    backendFalso(sesionDe(['COMPRAS']), escenario().atender);
    const persona = userEvent.setup();
    montar('/existencias');
    await persona.selectOptions(await screen.findByLabelText('Ubicación'), 'todas');
    expect(screen.queryByRole('button', { name: 'Registrar recuento de bebida' })).not.toBeInTheDocument();
  });

  it('usa el saldo existente al seleccionar una bebida y no agrega ceros a la lista habitual', async () => {
    const e = escenario('BARRA', [renglon(CATALOGO[0], 9)]);
    const fetchMock = backendFalso(sesionDe(['COMPRAS']), e.atender);
    const persona = userEvent.setup();
    montar('/existencias');
    const dialogo = await elegir(persona);
    expect(within(dialogo).getByText('1 caja y 3 botellas')).toBeInTheDocument();
    expect(screen.getAllByText('Fernet')).toHaveLength(1);
    await persona.click(within(dialogo).getAllByRole('button', { name: 'Sumar 1' })[0]);
    await persona.click(within(dialogo).getByRole('button', { name: 'Registrar recuento' }));
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toMatchObject({ cantidadContada: 15 });
  });

  it('no ofrece inactivas en la búsqueda y conserva el recuento de su saldo remanente desde la fila', async () => {
    backendFalso(sesionDe(['COMPRAS']), escenario('BARRA', [renglon(CATALOGO[1], 2)]).atender);
    const persona = userEvent.setup();
    montar('/existencias');
    await abrirBusqueda(persona);
    await persona.type(await screen.findByLabelText('Buscar bebida para el recuento'), 'gin');
    expect(screen.queryByRole('option', { name: /Gin inactivo/ })).not.toBeInTheDocument();
    await persona.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }));
    const fila = screen.getByText('Gin inactivo').closest('li') as HTMLElement;
    await persona.click(within(fila).getByRole('button', { name: 'Registrar recuento' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Registrar recuento' });
    expect(within(dialogo).getByText('2 botellas')).toBeInTheDocument();
  });

  it('muestra el error del catálogo y permite reintentar sin alterar existencias', async () => {
    const e = escenario();
    let fallar = true;
    backendFalso(sesionDe(['COMPRAS']), (p) => p.ruta === '/bebidas' && fallar
      ? problema(503, 'NO_DISPONIBLE', 'Catálogo no disponible.') : e.atender(p));
    const persona = userEvent.setup();
    montar('/existencias');
    await abrirBusqueda(persona);
    expect(await screen.findByText('Catálogo no disponible.')).toBeInTheDocument();
    fallar = false;
    await persona.click(screen.getByRole('button', { name: 'Reintentar' }));
    await persona.type(await screen.findByLabelText('Buscar bebida para el recuento'), 'fer');
    expect(await screen.findByRole('option', { name: /Fernet/ })).toBeInTheDocument();
    expect(e.movimientos).toEqual([]);
  });

  it('conserva el recuento cargado al recibir un error de negocio', async () => {
    const e = escenario();
    backendFalso(sesionDe(['COMPRAS']), (p) => p.metodo === 'POST' ? problema(422, 'MOTIVO_INVALIDO', 'Elegí un motivo de ajuste de la lista.') : e.atender(p));
    const persona = userEvent.setup();
    montar('/existencias');
    const dialogo = await elegir(persona);
    await persona.click(within(dialogo).getAllByRole('button', { name: 'Sumar 1' })[0]);
    await persona.click(within(dialogo).getByRole('button', { name: 'Registrar recuento' }));
    expect(await screen.findByText('Elegí un motivo de ajuste de la lista.')).toBeInTheDocument();
    expect(within(dialogo).getByText('Se registra un ajuste de +1 caja.')).toBeInTheDocument();
    expect(e.movimientos).toEqual([]);
  });
});
