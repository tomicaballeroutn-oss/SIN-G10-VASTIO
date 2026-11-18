import { describe, expect, it } from 'vitest';
import type { Rol } from '../../api/cliente';
import { buscarArticulos } from './busqueda';

const ids = (consulta: string, roles: Rol[] = ['DIRECCION']) => buscarArticulos(consulta, roles).map((articulo) => articulo.id);

describe('búsqueda local de ayuda', () => {
  it.each(['CONTRASEÑA', 'contrasena', 'CoNtRaSeNa', 'contrasen\u0303a'])(
    'ignora mayúsculas y tildes al buscar %s', (consulta) => {
      expect(ids(consulta)).toContain('mi-cuenta');
    },
  );

  it('busca en el nombre de la categoría', () => {
    expect(ids('GESTION Y COCINA')).toEqual(['usuarios-perfiles', 'parametros', 'cocina']);
  });

  it.each([
    ['reserva firme', 'pre-reservas-senas'],
    ['leer codigo', 'ingresos-ajustes'],
    ['remanente heredado', 'cierre'],
  ])('busca en descripción, pasos y aclaraciones con %s', (consulta, articulo) => {
    expect(ids(consulta)).toContain(articulo);
  });

  it('combina palabras y admite espacios repetidos', () => {
    expect(ids('  SENA   pre-reservas  ')).toContain('pre-reservas-senas');
    expect(ids('sena inexistentezz')).toEqual([]);
  });

  it('incluye las definiciones y la descripción del glosario', () => {
    expect(ids('conteo fisico')).toContain('glosario');
    expect(ids('significado terminos')).toContain('glosario');
  });

  it.each<Rol>(['ADMINISTRACION', 'VENDEDORA', 'PLANNER', 'COMPRAS', 'BARRA', 'COCINA'])(
    'excluye la administración de usuarios para %s', (rol) => {
      expect(ids('usuarios perfiles', [rol])).not.toContain('usuarios-perfiles');
    },
  );

  it('considera la unión de perfiles', () => {
    expect(ids('usuarios perfiles', ['BARRA', 'COORDINACION'])).toContain('usuarios-perfiles');
    expect(ids('vista cocina', ['BARRA', 'COCINA'])).toContain('cocina');
  });

  it('excluye el texto de secciones que el perfil no puede leer', () => {
    expect(ids('reabrir', ['ADMINISTRACION'])).not.toContain('ordenes-preparacion');
    expect(ids('reabrir', ['COMPRAS'])).toContain('ordenes-preparacion');
    expect(ids('invitados definitivos', ['VENDEDORA'])).not.toContain('confirmacion-reprogramacion');
    expect(ids('invitados definitivos', ['PLANNER'])).toContain('confirmacion-reprogramacion');
  });

  it('una consulta vacía conserva únicamente los artículos permitidos', () => {
    expect(ids('  ', ['COCINA'])).toEqual(['ingresar', 'mi-cuenta', 'notificaciones', 'cocina', 'glosario']);
  });
});
