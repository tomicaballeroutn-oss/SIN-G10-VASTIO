import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react';
import type { Datos } from '../../api/useDatos';
import { Alert, Badge, Button, Card, IconButton } from '../../ds';
import { cx } from '../../ds/util';

interface ListaYEdicionProps<T extends { id: number }> {
  items: T[];
  /** Contenido de la fila. */
  fila: (item: T) => ReactNode;
  /** Nombre en texto plano, para «Editar …». */
  etiqueta: (item: T) => string;
  inactivo?: (item: T) => boolean;
  /** Encabezado del grupo al que pertenece (p. ej. el ámbito de un motivo). */
  grupo?: (item: T) => string;
  /** Texto del botón de alta («Agregar tipo de evento»). Sin él, no se agregan elementos. */
  agregar?: string;
  titulo: (item: T | undefined) => string;
  formulario: (item: T | undefined, listo: (guardado: T) => void) => ReactNode;
  alGuardar: (guardado: T) => void;
}

/**
 * Patrón de UI-06: lista a la izquierda y alta o edición en el panel de la derecha.
 * En teléfono el panel queda debajo y la pantalla baja hasta él al elegir.
 */
export function ListaYEdicion<T extends { id: number }>(p: ListaYEdicionProps<T>) {
  const [seleccion, setSeleccion] = useState<number | 'nuevo' | null>(null);
  const [guardado, setGuardado] = useState(false);
  const panel = useRef<HTMLDivElement>(null);
  const elegido = typeof seleccion === 'number' ? p.items.find((it) => it.id === seleccion) : undefined;

  useEffect(() => {
    if (seleccion !== null) panel.current?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' });
  }, [seleccion]);

  function elegir(nueva: number | 'nuevo') {
    setSeleccion(nueva);
    setGuardado(false);
  }

  function listo(item: T) {
    p.alGuardar(item);
    setSeleccion(item.id);
    setGuardado(true);
  }

  const grupos: { titulo?: string; items: T[] }[] = [];
  for (const it of p.items) {
    const titulo = p.grupo?.(it);
    const ultimo = grupos[grupos.length - 1];
    if (ultimo && ultimo.titulo === titulo) ultimo.items.push(it);
    else grupos.push({ titulo, items: [it] });
  }

  return (
    <div className="catalogo">
      <div className="catalogo__columna">
        <Card flush>
          {grupos.map((g, i) => (
            <Fragment key={g.titulo ?? i}>
              {g.titulo && <p className="overline v-muted catalogo__grupo">{g.titulo}</p>}
              <ul className="catalogo__lista">
                {g.items.map((it) => (
                  <li key={it.id} className={cx('catalogo__fila', seleccion === it.id && 'is-on')}>
                    <span className="catalogo__nombre">
                      {p.fila(it)}
                      {p.inactivo?.(it) && <Badge>De baja</Badge>}
                    </span>
                    <IconButton icon="pencil" label={`Editar ${p.etiqueta(it)}`} variant="text" pressed={seleccion === it.id} onClick={() => elegir(it.id)} />
                  </li>
                ))}
              </ul>
            </Fragment>
          ))}
        </Card>
        {p.agregar && (
          <Button variant="outline" icon="plus" onClick={() => elegir('nuevo')}>{p.agregar}</Button>
        )}
      </div>
      <div ref={panel} className="catalogo__panel">
        {seleccion === null ? (
          <Card className="catalogo__vacio">
            <p className="body-sm v-muted">Elegí un elemento de la lista para editarlo.</p>
          </Card>
        ) : (
          <Card title={p.titulo(elegido)}>
            {guardado && <Alert tone="success">Cambios guardados.</Alert>}
            <Fragment key={String(seleccion)}>{p.formulario(elegido, listo)}</Fragment>
          </Card>
        )}
      </div>
    </div>
  );
}

/** Mientras carga, error con reintento, o el contenido con los datos. */
export function Cargando<T>({ datos, children }: { datos: Datos<T>; children: (datos: T) => ReactNode }) {
  if (datos.error) {
    return (
      <Alert tone="danger" title="No pudimos traer los datos" action={<Button variant="outline" size="sm" onClick={datos.recargar}>Reintentar</Button>}>
        {datos.error.message}
      </Alert>
    );
  }
  if (datos.datos === undefined) return <p className="body-sm v-muted" role="status">Cargando…</p>;
  return children(datos.datos);
}

