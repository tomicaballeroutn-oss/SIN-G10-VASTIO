import { describe, expect, it } from 'vitest';
import type { Rol } from '../../api/cliente';
import { itemsInferiores, itemsPara, rutaDeInicio } from './menu';

const ids = (roles: Rol[]) => itemsPara(roles).map((it) => it.id);

describe('menú por perfil', () => {
  it('Dirección y Coordinación ven todo', () => {
    const todo = ['inicio', 'notificaciones', 'agenda', 'eventos', 'bloqueos', 'cocina', 'barra', 'existencias', 'movimientos', 'ordenes', 'catalogo', 'reportes', 'usuarios', 'parametros'];
    expect(ids(['DIRECCION'])).toEqual(todo);
    expect(ids(['COORDINACION'])).toEqual(todo);
  });

  it('Administración no administra usuarios ni opera la barra', () => {
    expect(ids(['ADMINISTRACION'])).toEqual(['inicio', 'notificaciones', 'agenda', 'eventos', 'bloqueos', 'existencias', 'movimientos', 'ordenes', 'catalogo', 'reportes', 'parametros']);
  });

  it('la vendedora ve la agenda y sus eventos', () => {
    const items = itemsPara(['VENDEDORA']);
    expect(items.map((it) => it.id)).toEqual(['notificaciones', 'agenda', 'eventos']);
    expect(items.find((it) => it.id === 'eventos')?.label).toBe('Mis eventos');
  });

  it('la planner ve agenda, todos los eventos y la vista de cocina', () => {
    expect(ids(['PLANNER'])).toEqual(['notificaciones', 'agenda', 'eventos', 'cocina']);
    expect(itemsPara(['PLANNER']).find((it) => it.id === 'eventos')?.label).toBe('Eventos');
  });

  it('una vendedora que también es planner ve todos los eventos', () => {
    expect(itemsPara(['VENDEDORA', 'PLANNER']).find((it) => it.id === 'eventos')?.label).toBe('Eventos');
  });

  it('Compras ve bebidas, reportes y la agenda en consulta', () => {
    expect(ids(['COMPRAS'])).toEqual(['inicio', 'notificaciones', 'agenda', 'eventos', 'cocina', 'barra', 'existencias', 'movimientos', 'ordenes', 'catalogo', 'reportes']);
    expect(itemsPara(['COMPRAS']).find((it) => it.id === 'eventos')?.label).toBe('Eventos');
  });

  it('la encargada de barra ve solo la barra y sus existencias', () => {
    expect(ids(['BARRA'])).toEqual(['notificaciones', 'barra', 'existencias']);
  });

  it('cocina ve solo la vista de cocina', () => {
    expect(ids(['COCINA'])).toEqual(['notificaciones', 'cocina']);
  });

  it('con dos perfiles se ve la unión', () => {
    expect(ids(['VENDEDORA', 'PLANNER'])).toEqual(['notificaciones', 'agenda', 'eventos', 'cocina']);
  });
});

describe('navegación inferior', () => {
  it('si entran, muestra todos los ítems', () => {
    expect(itemsInferiores(itemsPara(['PLANNER'])).map((it) => it.id)).toEqual(['notificaciones', 'agenda', 'eventos', 'cocina']);
  });

  it('si no entran, muestra cuatro por prioridad y «Más»', () => {
    expect(itemsInferiores(itemsPara(['DIRECCION'])).map((it) => it.label)).toEqual(['Agenda', 'Eventos', 'Barra', 'Existencias', 'Más']);
    expect(itemsInferiores(itemsPara(['ADMINISTRACION'])).map((it) => it.label)).toEqual(['Agenda', 'Eventos', 'Existencias', 'Movimientos', 'Más']);
  });
});

describe('pantalla de inicio', () => {
  it.each<[Rol[], string]>([
    [['DIRECCION'], '/inicio'],
    [['ADMINISTRACION'], '/inicio'],
    [['COMPRAS'], '/inicio'],
    [['VENDEDORA'], '/agenda'],
    [['PLANNER'], '/eventos'],
    [['BARRA'], '/barra'],
    [['COCINA'], '/cocina'],
    [['VENDEDORA', 'PLANNER'], '/agenda'],
  ])('%j empieza en %s', (roles, ruta) => {
    expect(rutaDeInicio(roles)).toBe(ruta);
  });
});
