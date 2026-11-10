import { useState } from 'react';
import { fichas, tieneServicio, type Ficha, type RenglonServicio, type ServicioAGuardar, type ServicioFicha } from '../../api/eventos';
import { useEnvio } from '../../api/useDatos';
import { Actor, Alert, Badge, Button, Card, Combobox, IconButton, Input, OpcionesMultiples, Segmentado } from '../../ds';
import { opcionesDe, sugerencias } from './opcionesServicios';
import { fechaHora } from '../comun/formato';

/** Un renglón mientras se escribe. La cantidad va como texto hasta guardar; `clave` lo identifica en la lista. */
interface RenglonEnEdicion {
  clave: number;
  texto: string;
  cantidad: string;
  /** Lo agregó la persona con «Agregar otro»: se muestra aunque esté vacío. */
  agregado?: boolean;
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
 * UI-13 · Servicios contratados (M-09): por categoría, renglones con cantidad opcional («Celíaco» · 2) y un detalle
 * libre. Menú sugiere al escribir; Bodega y Tipo de barra se eligen de una lista; Menús especiales se marcan de a varios
 * (opciones de ejemplo en opcionesServicios.ts). Todo se guarda como renglones de texto. Quien puede modificar el evento los edita y los guarda juntos; el resto los ve.
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
    <Card title="Servicios contratados" className="servicios__tarjeta">
      <div className="servicios">
        {guardado && !cambiados.length && <Alert tone="success" centrar>Servicios guardados.</Alert>}
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

  const opciones = opcionesDe(s.categoria);
  const nombres = opciones?.opciones.map((o) => o.nombre) ?? [];
  const esOpcion = (r: RenglonEnEdicion) => nombres.includes(r.texto.trim());

  const cantidad = (r: RenglonEnEdicion, etiqueta: string) => (
    <Input
      label={`${s.categoria}: cantidad ${etiqueta}`}
      labelHidden
      inputMode="numeric"
      placeholder="—"
      value={r.cantidad}
      onChange={(e) => cambiarRenglon(r.clave, { cantidad: e.target.value.replace(/\D/gu, '') })}
      error={errorDeRenglon(r.clave, 'cantidad')}
    />
  );

  /** Renglones de texto: libres, o con sugerencias al escribir si la categoría tiene opciones (Menú). */
  const renglonesLibres = (renglones: RenglonEnEdicion[], agregar: string) => (
    <div className="servicios__renglones">
      {renglones.length > 0 && (
        <div className="servicios__encabezado caption v-muted" aria-hidden>
          <span>Qué</span>
          <span>Cantidad</span>
        </div>
      )}
      {renglones.map((r, i) => (
        <div key={r.clave} className="servicios__renglon">
          {opciones?.modo === 'sugerencias' ? (
            <Combobox
              label={`${s.categoria}: renglón ${i + 1}`}
              labelHidden
              placeholder="Escribí para ver opciones"
              value={r.texto}
              onInputChange={(texto) => cambiarRenglon(r.clave, { texto })}
              options={sugerencias(opciones.opciones, r.texto)
                .filter((o) => o.nombre !== r.texto.trim())
                .map((o) => ({ value: o.nombre, label: o.nombre, detail: o.detalle }))}
              onSelect={(o) => cambiarRenglon(r.clave, { texto: o.value })}
              error={errorDeRenglon(r.clave, 'texto')}
            />
          ) : (
            <Input
              label={`${s.categoria}: renglón ${i + 1}`}
              labelHidden
              value={r.texto}
              onChange={(e) => cambiarRenglon(r.clave, { texto: e.target.value })}
              error={errorDeRenglon(r.clave, 'texto')}
            />
          )}
          {cantidad(r, `del renglón ${i + 1}`)}
          <IconButton icon="x" variant="text" label={`Quitar el renglón ${i + 1} de ${s.categoria}`} onClick={() => quitarRenglon(r.clave)} />
        </div>
      ))}
      {errorDe('renglones') && <p className="body-sm v-field__error" role="alert">{errorDe('renglones')}</p>}
      <div>
        <Button variant="text" size="sm" icon="plus" onClick={() => alCambiar({ ...borrador, renglones: [...borrador.renglones, { ...renglonNuevo(), agregado: true }] })}>
          {agregar}
        </Button>
      </div>
    </div>
  );

  let carga;
  if (opciones?.modo === 'una') {
    // Una sola opción (Bodega, Tipo de barra). Un texto viejo que no está entre las opciones se conserva como una más.
    const elegida = borrador.renglones.find((r) => r.texto.trim())?.texto.trim() ?? '';
    const detalle = opciones.opciones.find((o) => o.nombre === elegida)?.detalle;
    const errorUna = errorDe('renglones') ?? (borrador.renglones[0] ? errorDeRenglon(borrador.renglones[0].clave, 'texto') : undefined);
    carga = (
      <>
        <Segmentado
          label={`Elegí ${s.categoria.toLowerCase()}`}
          value={elegida}
          options={[
            { value: '', label: 'Sin definir' },
            ...opciones.opciones.map((o) => ({ value: o.nombre, label: o.nombre })),
            ...(elegida && !nombres.includes(elegida) ? [{ value: elegida, label: elegida }] : []),
          ]}
          onChange={(valor) => alCambiar({ ...borrador, renglones: [renglonNuevo(valor)] })}
        />
        {detalle && <p className="body-sm v-muted servicios__incluye">Incluye: {detalle}.</p>}
        {errorUna && <p className="body-sm v-field__error" role="alert">{errorUna}</p>}
      </>
    );
  } else if (opciones?.modo === 'varias') {
    // Varias a la vez, cada una con su cantidad (Menús especiales); abajo, otros que no estén en la lista.
    const marcadas = borrador.renglones.filter(esOpcion);
    const otras = borrador.renglones.filter((r) => !esOpcion(r));
    carga = (
      <>
        <OpcionesMultiples
          label="Marcá los que hagan falta"
          values={marcadas.map((r) => r.texto.trim())}
          options={opciones.opciones.map((o) => ({ value: o.nombre, label: o.nombre }))}
          onChange={(valores) => alCambiar({
            ...borrador,
            renglones: [
              ...valores.map((v) => marcadas.find((r) => r.texto.trim() === v) ?? renglonNuevo(v)),
              ...otras,
            ],
          })}
        />
        {marcadas.length > 0 && (
          <ul className="servicios__marcadas">
            {marcadas.map((r) => (
              <li key={r.clave} className="servicios__marcada">
                <span className="body">{r.texto}</span>
                {cantidad(r, `de ${r.texto}`)}
              </li>
            ))}
          </ul>
        )}
        {renglonesLibres(otras.filter((r) => r.texto.trim() || r.cantidad || r.agregado), 'Agregar otro')}
      </>
    );
  } else {
    carga = renglonesLibres(borrador.renglones, opciones ? 'Agregar otro menú' : 'Agregar renglón');
  }

  return (
    <fieldset className="servicios__categoria">
      <legend className="h4 servicios__leyenda">{s.categoria}</legend>
      {s.requeridaParaConfirmar && <div><Badge tone="info">Hace falta para confirmar</Badge></div>}
      {carga}
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
