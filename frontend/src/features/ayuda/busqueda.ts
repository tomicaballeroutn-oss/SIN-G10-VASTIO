import type { Rol } from '../../api/cliente';
import { ARTICULOS, CATEGORIAS, GLOSARIO } from './articulos';
import { CONTENIDO, DESCRIPCION_GLOSARIO, TERMINOS } from './contenido';
import { puedeLeer, type ArticuloAyuda } from './modelo';

function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

/** Busca sobre el contenido que ese usuario puede leer, conservando el orden del catálogo. */
export function buscarArticulos(consulta: string, roles: readonly Rol[]): ArticuloAyuda[] {
  const palabras = normalizar(consulta).trim().split(/\s+/).filter(Boolean);
  return [...ARTICULOS, GLOSARIO].filter((articulo) => {
    if (!puedeLeer(articulo, roles)) return false;
    const categoria = CATEGORIAS.find((item) => item.id === articulo.categoria)!;
    const contenido = CONTENIDO[articulo.id];
    const texto = articulo.id === GLOSARIO.id
      ? [DESCRIPCION_GLOSARIO, ...TERMINOS.flatMap((item) => [item.termino, item.definicion])]
      : [contenido.descripcion, ...contenido.secciones
        .filter((seccion) => !seccion.roles || seccion.roles.some((rol) => roles.includes(rol)))
        .flatMap((seccion) => [seccion.titulo, ...(seccion.pasos ?? []), ...(seccion.notas ?? [])])];
    const buscable = normalizar([articulo.titulo, categoria.titulo, ...texto].join(' '));
    return palabras.every((palabra) => buscable.includes(palabra));
  });
}
