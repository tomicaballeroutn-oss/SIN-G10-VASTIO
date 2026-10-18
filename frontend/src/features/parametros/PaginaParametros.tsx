import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router';
import { Select, Tabs } from '../../ds';
import { Encabezado } from '../layout/paginas';
import {
  SeccionCategorias, SeccionGenerales, SeccionMotivos, SeccionSalones, SeccionSegmentos, SeccionTiposEvento, SeccionTurnos,
} from './secciones';
import { SeccionUbicaciones } from './ubicaciones';
import './parametros.css';

const SECCIONES: { id: string; label: string; contenido: () => ReactNode }[] = [
  { id: 'salones', label: 'Salones', contenido: () => <SeccionSalones /> },
  { id: 'turnos', label: 'Turnos', contenido: () => <SeccionTurnos /> },
  { id: 'tipos', label: 'Tipos de evento', contenido: () => <SeccionTiposEvento /> },
  { id: 'segmentos', label: 'Segmentos de asistencia', contenido: () => <SeccionSegmentos /> },
  { id: 'categorias', label: 'Categorías de servicio', contenido: () => <SeccionCategorias /> },
  { id: 'motivos', label: 'Motivos', contenido: () => <SeccionMotivos /> },
  { id: 'ubicaciones', label: 'Ubicaciones de stock', contenido: () => <SeccionUbicaciones /> },
  { id: 'generales', label: 'Sesión y cocina', contenido: () => <SeccionGenerales /> },
];

/**
 * UI-06 · Configurar parámetros del sistema. Una pestaña por catálogo; en teléfono, un selector de sección.
 * La sección elegida queda en la dirección (?seccion=turnos) para volver a ella al recargar.
 */
export function PaginaParametros() {
  const [busqueda, setBusqueda] = useSearchParams();
  const pedida = busqueda.get('seccion');
  const actual = SECCIONES.find((s) => s.id === pedida) ?? SECCIONES[0];
  const elegir = (id: string) => setBusqueda({ seccion: id }, { replace: true });

  return (
    <section className="pantalla">
      <Encabezado titulo="Parámetros del sistema" antetitulo="Gestión" />
      <Tabs className="parametros__pestanas" label="Secciones de parámetros" items={SECCIONES} value={actual.id} onChange={elegir} />
      <Select
        className="parametros__selector"
        label="Sección"
        options={SECCIONES.map((s) => ({ value: s.id, label: s.label }))}
        value={actual.id}
        onChange={(e) => elegir(e.target.value)}
      />
      <div key={actual.id}>{actual.contenido()}</div>
    </section>
  );
}
