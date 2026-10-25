import { useState } from 'react';
import { fichas, tieneServicio, type Ficha, type RenglonServicio, type ServicioAGuardar, type ServicioFicha } from '../../api/eventos';
import { useEnvio } from '../../api/useDatos';
import { Actor, Alert, Badge, Button, Card, IconButton, Input } from '../../ds';
import { fechaHora } from '../comun/formato';

/** Un renglón mientras se escribe. La cantidad va como texto hasta guardar; `clave` lo identifica en la lista. */
interface RenglonEnEdicion {
  clave: number;
  texto: string;
  cantidad: string;
}

/** Lo que se está escribiendo en una categoría. */
interface Borrador {
  renglones: RenglonEnEdicion[];
  detalle: string;
}

/** Lo que se manda de un borrador, sin los renglones vacíos. `claves` dice de qué renglón en pantalla salió cada uno. */
interface Normalizado {
  renglones: RenglonServicio[];
  claves: number[];
  detalle: string;
}

let ultimaClave = 0;
const renglonNuevo = (texto = '', cantidad = ''): RenglonEnEdicion => ({ clave: ++ultimaClave, texto, cantidad });

function borradorDe(s: ServicioFicha): Borrador {
  const renglones = s.renglones.map((r) => renglonNuevo(r.texto, r.cantidad == null ? '' : String(r.cantidad)));
  return { renglones: renglones.length ? renglones : [renglonNuevo()], detalle: s.descripcion ?? '' };
}

function normalizar(b: Borrador): Normalizado {
  const llenos = b.renglones.filter((r) => r.texto.trim() || r.cantidad);
  return {
    renglones: llenos.map((r) => ({ texto: r.texto.trim(), cantidad: r.cantidad ? Number(r.cantidad) : null })),
    claves: llenos.map((r) => r.clave),
    detalle: b.detalle.trim(),
  };
}

function cambio(s: ServicioFicha, b: Borrador): boolean {
  const n = normalizar(b);
  return n.detalle !== (s.descripcion ?? '') || JSON.stringify(n.renglones) !== JSON.stringify(s.renglones);
}

/**
 * UI-13 · Servicios contratados (M-09): por categoría, renglones libres con cantidad opcional («Celíaco» · 2) y un
 * detalle libre. Nada predefinido. Quien puede modificar el evento los edita y los guarda juntos; el resto los ve.
 * Las categorías dadas de baja con algo cargado se ven sin editar.
 */
export function PestanaServicios({ ficha, alGuardar }: { ficha: Ficha; alGuardar: (ficha: Ficha) => void }) {
  const editable = ficha.acciones.modificar;
  const [borradores, setBorradores] = useState<Record<number, Borrador>>(() =>
    Object.fromEntries(ficha.servicios.map((s) => [s.categoriaId, borradorDe(s)])),
  );
  const { guardando, error, enviar } = useEnvio();
  const [guardado, setGuardado] = useState(false);

  const cambiados = ficha.servicios.filter((s) => s.activa && cambio(s, borradores[s.categoriaId]));

  async function guardar() {
    setGuardado(false);
    const pedido: ServicioAGuardar[] = cambiados.map((s) => {
      const n = normalizar(borradores[s.categoriaId]);
      return { categoriaId: s.categoriaId, descripcion: n.detalle, renglones: n.renglones };
    });
    const nueva = await enviar(() => fichas.registrarServicios(ficha.id, ficha.version, pedido));
    if (nueva) {
      setGuardado(true);
      setBorradores(Object.fromEntries(nueva.servicios.map((s) => [s.categoriaId, borradorDe(s)])));
      alGuardar(nueva);
    }
  }

  /** Errores de validación de una categoría: el backend los nombra `servicios[i].…`, con i en el orden de lo enviado. */
  function erroresDe(s: ServicioFicha): (campo: string) => string | undefined {
    const i = cambiados.findIndex((c) => c.categoriaId === s.categoriaId);
    return (campo) => (i >= 0 ? error?.errorDe(`servicios[${i}].${campo}`) : undefined);
  }

  return (
    <Card title="Servicios contratados" subtitle={editable ? 'Cargá lo acordado con el cliente. Todo es opcional.' : undefined}>
      <div className="servicios">
        {guardado && !cambiados.length && <Alert tone="success">Servicios guardados.</Alert>}
        {error && !error.errores.length && <Alert tone="danger">{error.message}</Alert>}
        {ficha.servicios.map((s) => (editable && s.activa ? (
          <CategoriaEditable
            key={s.categoriaId}
            servicio={s}
            borrador={borradores[s.categoriaId]}
            alCambiar={(b) => setBorradores((previos) => ({ ...previos, [s.categoriaId]: b }))}
            errorDe={erroresDe(s)}
          />
        ) : (
          <div key={s.categoriaId} className="servicios__categoria">
            <div className="servicios__titulo">
              <h4 className="h4">{s.categoria}</h4>
              {s.requeridaParaConfirmar && <Badge tone="info">Hace falta para confirmar</Badge>}
              {!s.activa && <Badge>Categoría dada de baja</Badge>}
            </div>
            <ServicioCargado servicio={s} />
            <Autor servicio={s} />
          </div>
        )))}
        {editable && (
          <div className="servicios__acciones">
            <Button icon="check" loading={guardando} disabled={!cambiados.length} onClick={() => void guardar()}>
              Guardar servicios
            </Button>
          </div>
        )}
      </div>
    </Card>
  );
}

function CategoriaEditable({ servicio: s, borrador, alCambiar, errorDe }: {
  servicio: ServicioFicha;
  borrador: Borrador;
  alCambiar: (b: Borrador) => void;
  errorDe: (campo: string) => string | undefined;
}) {
  const { claves } = normalizar(borrador);
  const errorDeRenglon = (clave: number, campo: 'texto' | 'cantidad') => {
    const j = claves.indexOf(clave);
    return j >= 0 ? errorDe(`renglones[${j}].${campo}`) : undefined;
  };
  const cambiarRenglon = (clave: number, cambios: Partial<RenglonEnEdicion>) =>
    alCambiar({ ...borrador, renglones: borrador.renglones.map((r) => (r.clave === clave ? { ...r, ...cambios } : r)) });
  const quitarRenglon = (clave: number) => {
    const quedan = borrador.renglones.filter((r) => r.clave !== clave);
    alCambiar({ ...borrador, renglones: quedan.length ? quedan : [renglonNuevo()] });
  };

  return (
    <fieldset className="servicios__categoria">
      <legend className="h4 servicios__leyenda">{s.categoria}</legend>
      {s.requeridaParaConfirmar && <div><Badge tone="info">Hace falta para confirmar</Badge></div>}
      <div className="servicios__renglones">
        <div className="servicios__encabezado caption v-muted" aria-hidden>
          <span>Qué</span>
          <span>Cantidad</span>
        </div>
        {borrador.renglones.map((r, i) => (
          <div key={r.clave} className="servicios__renglon">
            <Input
              label={`${s.categoria}: renglón ${i + 1}`}
              labelHidden
              value={r.texto}
              onChange={(e) => cambiarRenglon(r.clave, { texto: e.target.value })}
              error={errorDeRenglon(r.clave, 'texto')}
            />
            <Input
              label={`${s.categoria}: cantidad del renglón ${i + 1}`}
              labelHidden
              inputMode="numeric"
              placeholder="—"
              value={r.cantidad}
              onChange={(e) => cambiarRenglon(r.clave, { cantidad: e.target.value.replace(/\D/gu, '') })}
              error={errorDeRenglon(r.clave, 'cantidad')}
            />
            <IconButton icon="x" variant="text" label={`Quitar el renglón ${i + 1} de ${s.categoria}`} onClick={() => quitarRenglon(r.clave)} />
          </div>
        ))}
        {errorDe('renglones') && <p className="body-sm v-field__error" role="alert">{errorDe('renglones')}</p>}
        <div>
          <Button variant="text" size="sm" icon="plus" onClick={() => alCambiar({ ...borrador, renglones: [...borrador.renglones, renglonNuevo()] })}>
            Agregar renglón
          </Button>
        </div>
      </div>
      <Input
        label={`Detalle de ${s.categoria}`}
        optional
        multiline
        rows={2}
        placeholder="Lo que haga falta aclarar"
        value={borrador.detalle}
        onChange={(e) => alCambiar({ ...borrador, detalle: e.target.value })}
        error={errorDe('descripcion')}
      />
      <Autor servicio={s} />
    </fieldset>
  );
}

/** Lo cargado en una categoría, en solo lectura. */
function ServicioCargado({ servicio: s }: { servicio: ServicioFicha }) {
  if (!tieneServicio(s)) return <p className="body v-muted">Sin cargar.</p>;
  return (
    <>
      {s.renglones.length > 0 && (
        <ul className="servicios__lista body">
          {s.renglones.map((r) => (
            <li key={`${r.texto}-${r.cantidad}`}>
              {r.texto}
              {r.cantidad != null && <span className="numeral-sm"> · {r.cantidad}</span>}
            </li>
          ))}
        </ul>
      )}
      {s.descripcion && <p className="body servicios__texto">{s.descripcion}</p>}
    </>
  );
}

function Autor({ servicio: s }: { servicio: ServicioFicha }) {
  if (!s.usuario || !s.fechaModificacion) return null;
  return <Actor name={s.usuario.nombre} action="lo cargó" at={fechaHora(s.fechaModificacion)} size="sm" />;
}
