import { useCallback, useRef, useState } from 'react';
import { bebidas, type Bebida } from '../../api/bebidas';
import { preparaciones, ROLES_PREPARACION, type PantallaPreparacion, type Preparacion, type SolicitudPreparacion } from '../../api/preparaciones';
import { useDatos, useEnvio } from '../../api/useDatos';
import { ProblemaApi } from '../../api/problema';
import { ESTADO_ORDEN } from '../../api/ordenes';
import { Actor, Alert, Button, CantidadEnCajas, Card, Combobox, Dialog, EmptyState, IconButton, Input, LectorCodigo, Select, Table, cantidadLegible } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaHora } from '../comun/formato';
import { useSesion } from '../sesion/contexto';
import '../bebidas/ingreso-bebida.css';
import './preparacion.css';

function claveDePreparacion() {
  try {
    const bytes = new Uint8Array(16);
    globalThis.crypto.getRandomValues(bytes);
    return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  } catch {
    throw new ProblemaApi(0, 'CLAVE_NO_DISPONIBLE', 'No pudimos preparar la confirmación',
      'No pudimos preparar una confirmación segura. No se envió la preparación. Volvé a intentarlo desde un navegador actualizado.');
  }
}

function rechazoDefinitivo(error: unknown) {
  // Estos rechazos se producen después de buscar la clave y comprobar que no existe un registro previo.
  // Un 401/403, un conflicto de clave o un error desconocido no permiten descartar un commit anterior.
  return error instanceof ProblemaApi && (
    error.status === 409 && ['ESTADO_NO_PERMITE', 'CIRCUITO_CAMBIADO'].includes(error.codigo)
    || error.status === 422 && ['BARRA_INVALIDA', 'DESTINO_INVALIDO', 'SIN_DEPOSITO_MADRE',
      'NO_REQUIERE_PREPARACION', 'BEBIDA_INEXISTENTE', 'BEBIDA_DADA_DE_BAJA'].includes(error.codigo));
}

export function PestanaPreparacion({ eventoId }: { eventoId: number }) {
  const { usuario } = useSesion();
  const permitido = usuario?.roles.some((rol) => ROLES_PREPARACION.includes(rol)) ?? false;
  return permitido ? <ContenidoPreparacion key={eventoId} eventoId={eventoId} /> : <Alert tone="warning">No tenés permiso para consultar preparaciones.</Alert>;
}

function ContenidoPreparacion({ eventoId }: { eventoId: number }) {
  const cargar = useCallback(async () => {
    const [pantalla, catalogo] = await Promise.all([preparaciones.consultar(eventoId), bebidas.listar()]);
    return { pantalla, catalogo };
  }, [eventoId]);
  const datos = useDatos(cargar);
  return <Cargando datos={datos}>{({ pantalla, catalogo }) => <Formulario pantalla={pantalla} catalogo={catalogo}
    alRegistrar={(p) => datos.fijar((previos) => ({ pantalla: { ...pantalla, historial: [p, ...pantalla.historial.filter((x) => x.id !== p.id)] }, catalogo: previos?.catalogo ?? catalogo }))} />}</Cargando>;
}

function Formulario({ pantalla, catalogo, alRegistrar }: { pantalla: PantallaPreparacion; catalogo: Bebida[]; alRegistrar: (p: Preparacion) => void }) {
  const [barraId, setBarraId] = useState(String(pantalla.circuitos[0]?.barra.id ?? ''));
  const [renglones, setRenglones] = useState<{ bebida: Bebida; cantidad: number }[]>([]);
  const [buscar, setBuscar] = useState('');
  const [lector, setLector] = useState(false);
  const [lectura, setLectura] = useState('');
  const [revisando, setRevisando] = useState(false);
  const [aviso, setAviso] = useState('');
  const [validacion, setValidacion] = useState('');
  const [pendiente, setPendiente] = useState<{ clave: string; cuerpo: SolicitudPreparacion } | null>(null);
  const [incierta, setIncierta] = useState(false);
  const enviando = useRef(false);
  const { guardando, error, enviar, limpiarError } = useEnvio();
  const circuito = pantalla.circuitos.find((c) => String(c.barra.id) === barraId);
  const estadoValido = ['CONFIRMADO', 'EN_CURSO'].includes(pantalla.evento.estado);
  const bloqueado = guardando || incierta;
  const agregadas = new Set(renglones.map((r) => r.bebida.id));
  const opciones = buscar.trim() ? catalogo.filter((b) => b.activo && !agregadas.has(b.id)
    && `${b.nombre} ${b.presentacion} ${b.codigos.map((c) => c.codigo).join(' ')}`.toLowerCase().includes(buscar.trim().toLowerCase()))
    .slice(0, 8).map((b) => ({ value: String(b.id), label: b.nombre, detail: b.presentacion })) : [];

  function agregar(b: Bebida) {
    setRenglones((previos) => previos.some((r) => r.bebida.id === b.id) ? previos : [...previos, { bebida: b, cantidad: 0 }]);
    setValidacion('');
  }
  function leer(codigo: string) {
    const b = catalogo.find((x) => x.codigos.some((c) => c.codigo === codigo));
    if (!b) setLectura('Ese código no está en el catálogo. Elegí la bebida a mano.');
    else if (!b.activo) setLectura(`${b.nombre} está dada de baja.`);
    else {
      setLectura(agregadas.has(b.id) ? `${b.nombre} ya está en la preparación. Cargá la cantidad en su renglón.` : `${b.nombre} agregada. Cargá la cantidad.`);
      agregar(b);
    }
  }
  function revisar() {
    if (!renglones.length || renglones.some((r) => !Number.isSafeInteger(r.cantidad) || r.cantidad <= 0 || r.cantidad > 99999999)) {
      setValidacion('Cargá cantidades positivas en botellas enteras para todas las bebidas.');
      return;
    }
    limpiarError();
    setRevisando(true);
  }
  async function registrar() {
    if (enviando.current || !circuito) return;
    enviando.current = true;
    try {
      const p = await enviar(async () => {
        // Generar antes de enviar, dentro del manejo de errores: si falla, todavía no hay resultado incierto.
        const operacion = pendiente ?? { clave: claveDePreparacion(), cuerpo: { barraId: circuito.barra.id, destinoId: circuito.abastecimiento.id,
          renglones: renglones.map((r) => ({ bebidaId: r.bebida.id, cantidad: r.cantidad })) } };
        setPendiente(operacion);
        try { return await preparaciones.registrar(pantalla.evento.id, operacion.clave, operacion.cuerpo); }
        catch (e) {
          setIncierta(!rechazoDefinitivo(e));
          throw e;
        }
      });
      if (p) {
        alRegistrar(p);
        setPendiente(null);
        setIncierta(false);
        setRevisando(false);
        setRenglones([]);
        setAviso(`Preparación registrada en ${p.destino.nombre}.`);
      }
    } finally {
      enviando.current = false;
    }
  }
  function volver() {
    if (bloqueado) return;
    setRevisando(false);
    setPendiente(null);
    limpiarError();
  }

  return <div className="preparacion">
    {aviso && <Alert tone="success">{aviso}</Alert>}
    {!estadoValido && <Alert tone="info">El evento está {pantalla.evento.estado.toLowerCase().replaceAll('_', ' ')}. Solo podés preparar bebida con el evento confirmado o en curso.</Alert>}
    {!pantalla.madre && <Alert tone="warning">No hay un depósito madre activo. Configuralo en Ubicaciones de stock.</Alert>}
    {pantalla.orden && pantalla.orden.renglones.length > 0 && <Card title="Según la orden de preparación"
      subtitle={`Orden ${ESTADO_ORDEN[pantalla.orden.estado].label.toLowerCase()}: es una referencia, no se carga sola.`}>
      <Table caption="Según la orden de preparación" dense rows={pantalla.orden.renglones.map((r) => ({ ...r, id: r.bebidaId }))}
        columns={[
          { key: 'bebida', header: 'Bebida', render: (r) => `${r.nombre} ${r.presentacion}` },
          { key: 'aRetirar', header: 'Según orden', numeric: true, render: (r) => cantidadLegible(r.aRetirar, r.unidadesPorBulto, r.unidad) },
        ]} />
    </Card>}
    {pantalla.circuitos.length === 0 && <EmptyState title="No hay barras activas en el salón" icon="warehouse">Configuralas en Ubicaciones de stock.</EmptyState>}
    {pantalla.circuitos.length > 0 && <Card title="Circuito de preparación">
      <div className="ingreso-bebida__cabecera">
        <Select label="Barra" size="lg" value={barraId} disabled={bloqueado} options={pantalla.circuitos.map((c) => ({ value: String(c.barra.id), label: c.barra.nombre }))}
          onChange={(e) => { setBarraId(e.target.value); setPendiente(null); }} />
        <Input label="Sale de" size="lg" value={pantalla.madre?.nombre ?? 'Sin depósito madre'} readOnly />
        <Input label="Destino" size="lg" value={circuito?.abastecimiento.nombre ?? ''} readOnly />
      </div>
      {circuito && !circuito.requierePreparacion && <Alert tone="info">{circuito.abastecimiento.tipo === 'DEPOSITO'
        ? 'Esta barra retira directamente del depósito madre y no requiere preparación.' : 'El depósito de transición está inactivo. Revisá la configuración.'}</Alert>}
    </Card>}
    {estadoValido && pantalla.madre && circuito?.requierePreparacion && <>
      <div className="ingreso-bebida__agregar">
        <Combobox label="Agregar bebida" icon="search" placeholder="Nombre o código de barras" value={buscar} onInputChange={setBuscar}
          disabled={bloqueado} options={opciones} emptyText="No hay bebidas activas con ese nombre o código."
          onSelect={(o) => { const b = catalogo.find((x) => String(x.id) === o.value); if (b) agregar(b); setBuscar(''); }} />
        <Button size="lg" variant="outline" icon="scan-line" disabled={bloqueado} onClick={() => setLector(true)}>Leer código</Button>
      </div>
      {renglones.length === 0 && <EmptyState icon="warehouse" title="Todavía no cargaste bebidas">Elegí una bebida o leé su código. Después cargá la cantidad.</EmptyState>}
      <ul className="ingreso-bebida__renglones">{renglones.map((r) => <li key={r.bebida.id}>
        <Card title={r.bebida.nombre} subtitle={r.bebida.presentacion} actions={<IconButton label={`Sacar ${r.bebida.nombre}`} icon="x" variant="text" disabled={bloqueado}
          onClick={() => setRenglones((previos) => previos.filter((x) => x.bebida.id !== r.bebida.id))} />}>
          <CantidadEnCajas label={`Cantidad de ${r.bebida.nombre}`} value={r.cantidad} porBulto={r.bebida.unidadesPorBulto} unidad={r.bebida.unidad.nombre} disabled={bloqueado}
            onChange={(cantidad) => setRenglones((previos) => previos.map((x) => x.bebida.id === r.bebida.id ? { ...x, cantidad } : x))} />
        </Card>
      </li>)}</ul>
      {validacion && <Alert tone="warning">{validacion}</Alert>}
      <Button size="lg" disabled={bloqueado || !renglones.length} onClick={revisar}>Revisar preparación</Button>
      <LectorCodigo open={lector} onClose={() => setLector(false)} onRead={leer} onManual={() => setLector(false)} instruction="Leé el código y cargá la cantidad en su renglón.">{lectura}</LectorCodigo>
    </>}
    <Card title="Preparaciones del evento">
      {!pantalla.historial.length ? <EmptyState icon="warehouse" title="Todavía no hay preparaciones" /> : <ul className="ingreso-bebida__recientes">
        {pantalla.historial.map((p) => <li key={p.id}>
          <p className="label">{p.origen.nombre} → {p.destino.nombre} · {p.barra.nombre}</p>
          <ul className="ingreso-bebida__lista">{p.renglones.map((r) => <li key={r.bebidaId}><span>{r.nombre} {r.presentacion}</span>
            <span>{cantidadLegible(r.cantidad, r.unidadesPorBulto, r.unidad)}</span></li>)}</ul>
          <Actor name={p.usuario.nombre} action="registró la preparación" at={fechaHora(p.fechaHora)} size="sm" />
        </li>)}
      </ul>}
    </Card>
    {revisando && <Dialog open title="Revisá la preparación" onClose={volver} actions={<>
      <Button variant="outline" disabled={bloqueado} onClick={volver}>Volver a editar</Button>
      <Button loading={guardando} onClick={() => void registrar()}>{incierta ? 'Recuperar preparación' : 'Registrar preparación'}</Button>
    </>}>
      {error && <Alert tone="danger">{error.message}</Alert>}
      {incierta && <Alert tone="warning">No sabemos si la operación se confirmó. Recuperala con esta misma carga antes de registrar otra.</Alert>}
      <p>Pasan las siguientes cantidades de {pantalla.madre?.nombre} a {circuito?.abastecimiento.nombre} para {pantalla.evento.nombre}:</p>
      <ul className="ingreso-bebida__lista">{renglones.map((r) => <li key={r.bebida.id}><span>{r.bebida.nombre} {r.bebida.presentacion}</span><span>
        {cantidadLegible(r.cantidad, r.bebida.unidadesPorBulto, r.bebida.unidad.nombre)}</span></li>)}</ul>
      <p>Este traspaso no cuenta como consumo y no se edita después de registrar.</p>
    </Dialog>}
  </div>;
}
