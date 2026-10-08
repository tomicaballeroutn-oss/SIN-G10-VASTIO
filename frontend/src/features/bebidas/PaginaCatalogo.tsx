import { useCallback, useState } from 'react';
import {
  bebidas, presentacionDeBulto, type Bebida, type CodigoBarra, type TipoBebida, type UnidadManipulacion,
} from '../../api/bebidas';
import { useDatos, useEnvio } from '../../api/useDatos';
import {
  Alert, Badge, Button, CantidadEnCajas, Card, Checkbox, Dialog, IconButton, Input, LectorCodigo, Select, Table,
  cantidadLegible, type TableColumn,
} from '../../ds';
import { Cargando } from '../comun/Cargando';
import { Encabezado } from '../layout/paginas';
import './catalogo.css';

interface Catalogo {
  lista: Bebida[];
  tipos: TipoBebida[];
  unidades: UnidadManipulacion[];
}

const cargarCatalogo = (): Promise<Catalogo> =>
  Promise.all([bebidas.listar(), bebidas.tipos(), bebidas.unidades()]).then(([lista, tipos, unidades]) => ({ lista, tipos, unidades }));

/** Solo dígitos, de 8 a 14 (EAN-8, EAN-13, DUN-14), como valida el backend. */
const FORMATO_CODIGO = /^\d{8,14}$/;

/** Por nombre, presentación o código de barras (completo o en parte). */
function coincide(b: Bebida, texto: string): boolean {
  if (!texto) return true;
  const t = texto.toLowerCase();
  return b.nombre.toLowerCase().includes(t) || b.presentacion.toLowerCase().includes(t) || b.codigos.some((c) => c.codigo.includes(t));
}

/**
 * UI-25 · Catálogo de bebidas: listado con búsqueda por nombre o código (tipeado o leído con la cámara), filtro por
 * tipo, alta, modificación, baja lógica y reactivación. Antes de una baja se muestran las ubicaciones con saldo.
 * Dirección, Coordinación, Administración y Compras. Sin precios: el sistema no maneja dinero.
 */
export function PaginaCatalogo() {
  const datos = useDatos(cargarCatalogo);
  const [buscar, setBuscar] = useState('');
  const [tipo, setTipo] = useState('');
  const [verBajas, setVerBajas] = useState(false);
  const [lector, setLector] = useState(false);
  const [editando, setEditando] = useState<Bebida | 'nueva' | null>(null);
  const [aDarDeBaja, setADarDeBaja] = useState<Bebida | null>(null);
  const reactivacion = useEnvio();
  const [aviso, setAviso] = useState<string | null>(null);

  function reemplazar(actualizada: Bebida) {
    datos.fijar((previos) => {
      const c = previos as Catalogo;
      const existe = c.lista.some((b) => b.id === actualizada.id);
      return { ...c, lista: existe ? c.lista.map((b) => (b.id === actualizada.id ? actualizada : b)) : [actualizada, ...c.lista] };
    });
  }

  function guardada(bebida: Bebida, nueva: boolean) {
    reemplazar(bebida);
    setEditando(null);
    setAviso(nueva ? `Bebida cargada: ${bebida.nombre} ${bebida.presentacion}.` : `Cambios de ${bebida.nombre} ${bebida.presentacion} guardados.`);
  }

  function dadaDeBaja(bebida: Bebida) {
    reemplazar(bebida);
    setADarDeBaja(null);
    setAviso(`${bebida.nombre} ${bebida.presentacion} quedó dada de baja: ya no se ofrece para cargar.`);
  }

  async function reactivar(b: Bebida) {
    const actualizada = await reactivacion.enviar(() => bebidas.reactivar(b.id));
    if (actualizada) {
      reemplazar(actualizada);
      setAviso(`${actualizada.nombre} ${actualizada.presentacion} volvió al catálogo.`);
    }
  }

  const columnas: TableColumn<Bebida>[] = [
    {
      key: 'nombre',
      header: 'Bebida',
      render: (b) => (
        <span className="catalogo__nombre">
          {b.nombre}
          {!b.activo && <Badge>De baja</Badge>}
        </span>
      ),
    },
    { key: 'presentacion', header: 'Presentación' },
    { key: 'tipo', header: 'Tipo', render: (b) => b.tipo.nombre },
    { key: 'bulto', header: 'Se mueve en', render: (b) => presentacionDeBulto(b) },
    {
      key: 'stockMinimo',
      header: 'Stock mínimo',
      render: (b) => (b.stockMinimo ? cantidadLegible(b.stockMinimo, b.unidadesPorBulto, b.unidad.nombre) : '—'),
    },
    {
      key: 'codigos',
      header: 'Códigos de barras',
      render: (b) => (b.codigos.length ? <span className="numeral-sm">{b.codigos.map((c) => c.codigo).join(' · ')}</span> : '—'),
    },
    {
      key: 'acciones',
      header: 'Acciones',
      render: (b) => (
        <span className="catalogo__acciones">
          <Button variant="text" size="sm" icon="pencil" onClick={() => setEditando(b)}>Modificar</Button>
          {b.activo ? (
            <Button variant="text" tone="danger" size="sm" onClick={() => setADarDeBaja(b)}>Dar de baja</Button>
          ) : (
            <Button variant="text" size="sm" icon="refresh-cw" onClick={() => void reactivar(b)}>Reactivar</Button>
          )}
        </span>
      ),
    },
  ];

  return (
    <section className="pantalla">
      <Encabezado titulo="Catálogo de bebidas" antetitulo="Bebidas" />
      {aviso && <Alert tone="success">{aviso}</Alert>}
      {reactivacion.error && <Alert tone="danger">{reactivacion.error.message}</Alert>}
      <Cargando datos={datos}>
        {({ lista, tipos, unidades }) => {
          const texto = buscar.trim();
          const filtradas = lista.filter((b) => (verBajas || b.activo) && (!tipo || String(b.tipo.id) === tipo) && coincide(b, texto));
          return (
            <>
              <div className="catalogo__filtros">
                <div className="catalogo__buscar">
                  <Input label="Buscar" icon="search" placeholder="Nombre o código de barras" value={buscar} onChange={(e) => setBuscar(e.target.value)} />
                  <IconButton icon="scan-line" label="Buscar con la cámara" variant="outline" onClick={() => setLector(true)} />
                </div>
                <Select
                  label="Tipo"
                  options={[{ value: '', label: 'Todos los tipos' }, ...tipos.map((t) => ({ value: String(t.id), label: t.nombre }))]}
                  value={tipo}
                  onChange={(e) => setTipo(e.target.value)}
                />
                <Button icon="plus" onClick={() => setEditando('nueva')}>Nueva bebida</Button>
              </div>
              <Checkbox label="Mostrar las dadas de baja" checked={verBajas} onChange={(e) => setVerBajas(e.target.checked)} />
              <Card flush>
                <Table caption="Bebidas" columns={columnas} rows={filtradas} empty="No hay bebidas con esos filtros." />
              </Card>
              {editando && (
                <DialogoBebida
                  bebida={editando === 'nueva' ? null : editando}
                  tipos={tipos}
                  unidades={unidades}
                  alCerrar={() => setEditando(null)}
                  alGuardar={(b) => guardada(b, editando === 'nueva')}
                />
              )}
            </>
          );
        }}
      </Cargando>
      <LectorCodigo
        open={lector}
        onClose={() => setLector(false)}
        onRead={(codigo) => {
          setBuscar(codigo);
          setVerBajas(true);
          setLector(false);
        }}
        manualLabel="Escribir el código"
        onManual={() => setLector(false)}
      />
      {aDarDeBaja && <DialogoBaja bebida={aDarDeBaja} alCerrar={() => setADarDeBaja(null)} alConfirmar={dadaDeBaja} />}
    </section>
  );
}

function DialogoBebida({ bebida, tipos, unidades, alCerrar, alGuardar }: {
  bebida: Bebida | null;
  tipos: TipoBebida[];
  unidades: UnidadManipulacion[];
  alCerrar: () => void;
  alGuardar: (b: Bebida) => void;
}) {
  const [nombre, setNombre] = useState(bebida?.nombre ?? '');
  const [presentacion, setPresentacion] = useState(bebida?.presentacion ?? '');
  const [tipoId, setTipoId] = useState(bebida ? String(bebida.tipo.id) : '');
  const [unidadId, setUnidadId] = useState(bebida ? String(bebida.unidad.id) : '');
  const [porBulto, setPorBulto] = useState(bebida && !bebida.unidad.esBotella ? String(bebida.unidadesPorBulto) : '');
  const [stockMinimo, setStockMinimo] = useState(bebida?.stockMinimo ?? 0);
  const [codigos, setCodigos] = useState<CodigoBarra[]>(bebida?.codigos ?? []);
  const [nuevoCodigo, setNuevoCodigo] = useState('');
  const [errorCodigo, setErrorCodigo] = useState<string | undefined>();
  const [lector, setLector] = useState(false);
  const [leido, setLeido] = useState<string | null>(null);
  const { guardando, error, enviar } = useEnvio();

  const unidad = unidades.find((u) => String(u.id) === unidadId);
  const esBotella = unidad?.esBotella ?? false;
  const bulto = esBotella ? 1 : parseInt(porBulto, 10) || 0;
  const nombreUnidad = unidad?.nombre ?? 'Caja';

  /** Agrega el código si es válido y no está; una lectura de 14 dígitos (DUN-14) suele ser de la caja. */
  function agregar(codigo: string): boolean {
    const limpio = codigo.trim();
    if (!FORMATO_CODIGO.test(limpio)) {
      setErrorCodigo('El código tiene que tener de 8 a 14 números.');
      return false;
    }
    if (codigos.some((c) => c.codigo === limpio)) {
      setErrorCodigo('Ese código ya está en la lista.');
      return false;
    }
    setCodigos((previos) => [...previos, { codigo: limpio, unidades: limpio.length === 14 && bulto > 1 ? bulto : 1 }]);
    setErrorCodigo(undefined);
    setNuevoCodigo('');
    return true;
  }

  function cambiarUnidades(codigo: string, unidades: number) {
    setCodigos((previos) => previos.map((c) => (c.codigo === codigo ? { ...c, unidades } : c)));
  }

  /** Botella o el bulto de la bebida, más el valor guardado si es otro. */
  function opcionesDeUnidades(actual: number) {
    const valores = [...new Set([1, ...(bulto > 1 ? [bulto] : []), actual])].sort((a, b) => a - b);
    return valores.map((n) => ({
      value: String(n),
      label: n === 1 ? 'Una botella' : n === bulto ? `${nombreUnidad} de ${n} botellas` : `${n} botellas`,
    }));
  }

  async function guardar() {
    const datos = {
      nombre,
      presentacion,
      tipoId: tipoId ? Number(tipoId) : null,
      unidadId: unidadId ? Number(unidadId) : null,
      unidadesPorBulto: bulto,
      stockMinimo: stockMinimo > 0 ? stockMinimo : null,
      codigos,
    };
    const guardada = await enviar(() => (bebida ? bebidas.modificar(bebida.id, datos) : bebidas.crear(datos)));
    if (guardada) alGuardar(guardada);
  }

  const erroresDeCodigos = codigos
    .map((_, i) => error?.errorDe(`codigos[${i}].codigo`) ?? error?.errorDe(`codigos[${i}].unidades`))
    .filter(Boolean);

  return (
    <Dialog
      open
      title={bebida ? 'Modificar bebida' : 'Nueva bebida'}
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="check" loading={guardando} onClick={() => void guardar()}>{bebida ? 'Guardar cambios' : 'Cargar bebida'}</Button>
        </>
      }
    >
      <div className="catalogo__form">
        {error && !error.errores.length && <Alert tone="danger">{error.message}</Alert>}
        <Input label="Nombre" placeholder="Por ejemplo, Fernet Branca" value={nombre} onChange={(e) => setNombre(e.target.value)} error={error?.errorDe('nombre')} required />
        <Input label="Presentación" placeholder="Por ejemplo, 750 ml" value={presentacion} onChange={(e) => setPresentacion(e.target.value)} error={error?.errorDe('presentacion')} required />
        <Select
          label="Tipo"
          placeholder="Elegí un tipo"
          options={tipos.map((t) => ({ value: String(t.id), label: t.nombre }))}
          value={tipoId}
          onChange={(e) => setTipoId(e.target.value)}
          error={error?.errorDe('tipoId')}
          required
        />
        <div className="catalogo__bulto">
          <Select
            label="Se mueve en"
            placeholder="Elegí la unidad"
            options={unidades.map((u) => ({ value: String(u.id), label: u.nombre }))}
            value={unidadId}
            onChange={(e) => setUnidadId(e.target.value)}
            error={error?.errorDe('unidadId')}
            required
          />
          <Input
            label="Botellas por bulto"
            inputMode="numeric"
            value={esBotella ? '1' : porBulto}
            onChange={(e) => setPorBulto(e.target.value.replace(/\D/g, ''))}
            disabled={esBotella}
            hint={esBotella ? 'Se mueve de a una botella.' : undefined}
            error={error?.errorDe('unidadesPorBulto')}
            required
          />
        </div>
        <CantidadEnCajas
          label="Stock mínimo"
          optional
          hint="Por debajo de esto, el depósito madre la marca como stock bajo. Dejalo en 0 si no hace falta."
          value={stockMinimo}
          onChange={setStockMinimo}
          porBulto={Math.max(bulto, 1)}
          unidad={nombreUnidad}
          error={error?.errorDe('stockMinimo')}
        />
        <fieldset className="catalogo__codigos">
          <legend className="label">Códigos de barras <span className="v-muted">(opcional)</span></legend>
          <p className="body-sm v-muted">El código identifica la bebida al leerlo; la cantidad se carga aparte. Puede tener el de la botella y el de la caja.</p>
          {codigos.length > 0 && (
            <ul className="catalogo__lista-codigos">
              {codigos.map((c) => (
                <li key={c.codigo} className="catalogo__codigo">
                  <span className="numeral-sm">{c.codigo}</span>
                  <Select
                    label={`Qué representa ${c.codigo}`}
                    className="catalogo__codigo-unidades"
                    options={opcionesDeUnidades(c.unidades)}
                    value={String(c.unidades)}
                    onChange={(e) => cambiarUnidades(c.codigo, Number(e.target.value))}
                  />
                  <IconButton icon="x" label={`Quitar el código ${c.codigo}`} variant="text" size="sm" onClick={() => setCodigos((previos) => previos.filter((x) => x.codigo !== c.codigo))} />
                </li>
              ))}
            </ul>
          )}
          {erroresDeCodigos.map((m) => <p key={m} className="body-sm v-field__error" role="alert">{m}</p>)}
          <div className="catalogo__nuevo-codigo">
            <Input
              label="Agregar código"
              inputMode="numeric"
              placeholder="Tipealo o leelo con la cámara"
              value={nuevoCodigo}
              onChange={(e) => setNuevoCodigo(e.target.value.replace(/\D/g, ''))}
              error={errorCodigo}
            />
            <IconButton icon="scan-line" label="Leer código con la cámara" variant="outline" onClick={() => { setLeido(null); setLector(true); }} />
            <Button variant="outline" icon="plus" onClick={() => agregar(nuevoCodigo)}>Agregar código</Button>
          </div>
        </fieldset>
      </div>
      <LectorCodigo
        open={lector}
        onClose={() => setLector(false)}
        onRead={(codigo) => {
          if (agregar(codigo)) setLeido(codigo);
        }}
        manualLabel="Escribir el código"
        onManual={() => setLector(false)}
        instruction="Apuntá al código de la botella o de la caja. Podés leer los dos."
      >
        {leido && <span className="body">Código {leido} agregado.</span>}
      </LectorCodigo>
    </Dialog>
  );
}

function DialogoBaja({ bebida, alCerrar, alConfirmar }: { bebida: Bebida; alCerrar: () => void; alConfirmar: (b: Bebida) => void }) {
  const { guardando, error, enviar } = useEnvio();
  const cargar = useCallback(() => bebidas.saldos(bebida.id), [bebida.id]);
  const saldos = useDatos(cargar);

  async function confirmar() {
    const actualizada = await enviar(() => bebidas.darDeBaja(bebida.id));
    if (actualizada) alConfirmar(actualizada);
  }

  return (
    <Dialog
      open
      title="Dar de baja bebida"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button tone="danger" loading={guardando} onClick={() => void confirmar()}>Dar de baja bebida</Button>
        </>
      }
    >
      <div className="catalogo__form">
        {error && <Alert tone="danger">{error.message}</Alert>}
        <p>
          {bebida.nombre} {bebida.presentacion} deja de ofrecerse para cargar. Lo que ya se registró se conserva y la podés reactivar cuando quieras.
        </p>
        <Cargando datos={saldos}>
          {(lista) =>
            lista.length > 0 ? (
              <Alert tone="warning" icon="triangle-alert" title={lista.length === 1 ? 'Tiene saldo en 1 ubicación' : `Tiene saldo en ${lista.length} ubicaciones`}>
                <ul className="catalogo__saldos">
                  {lista.map((s) => (
                    <li key={s.ubicacionId}>
                      {s.ubicacion}: {cantidadLegible(s.cantidad, bebida.unidadesPorBulto, bebida.unidad.nombre)}
                    </li>
                  ))}
                </ul>
                Podés darla de baja igual: se va a seguir viendo en la consulta de stock mientras tenga saldo.
              </Alert>
            ) : (
              <p className="body-sm v-muted">No tiene saldo en ninguna ubicación.</p>
            )
          }
        </Cargando>
      </div>
    </Dialog>
  );
}
