import { useCallback, useState } from 'react';
import { entregas, type BebidaParaRetiro, type Devolucion, type Entrega, type EventoDeLaJornada } from '../../api/entregas';
import { useDatos, useEnvio } from '../../api/useDatos';
import {
  Alert, Button, CantidadEnCajas, Card, Combobox, Dialog, EmptyState, ESTADO_POR_CODIGO, ESTADOS, IconButton, LectorCodigo,
  MovementCard, SalonTag, Select, StatusChip, Switch, cantidadLegible, type ComboboxOption, type SalonId,
} from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaHora } from '../comun/formato';
import { Encabezado } from '../layout/paginas';
import { ConsumoDeLaDevolucion, DevolucionBarra } from './DevolucionBarra';
import { resumenDeSalida } from './salida';
import './barra.css';

interface Renglon {
  bebida: BebidaParaRetiro;
  /** En botellas. */
  cantidad: number;
}

/** Un evento de la jornada en una de sus barras: lo que elige la encargada. */
interface Opcion {
  valor: string;
  eventoId: number;
  barraId: number;
  etiqueta: string;
}

function opciones(jornada: EventoDeLaJornada[]): Opcion[] {
  return jornada.flatMap((j) => j.barras.map((b) => ({
    valor: `${j.evento.id}-${b.id}`,
    eventoId: j.evento.id,
    barraId: b.id,
    etiqueta: `${j.evento.nombre} · ${b.nombre}`,
  })));
}

/** «Fernet Branca 750 ml · Caja de 6». */
function presentacion(b: Pick<BebidaParaRetiro, 'presentacion' | 'unidad' | 'unidadesPorBulto'>): string {
  return `${b.presentacion} · ${b.unidadesPorBulto > 1 ? `${b.unidad} de ${b.unidadesPorBulto}` : b.unidad}`;
}

/** Por nombre, presentación o código de barras. */
function coincide(b: BebidaParaRetiro, texto: string): boolean {
  const t = texto.trim().toLowerCase();
  return !t || `${b.nombre} ${b.presentacion}`.toLowerCase().includes(t) || b.codigos.some((c) => c.codigo.includes(t));
}

/**
 * UI-31 · Registrar salida a barra (y retiro adicional), con el lector de códigos (UI-30). La encargada elige el evento
 * de la jornada y la barra, y retira bebida del depósito en cajas y botellas. El primer retiro abre la entrega; los
 * siguientes son retiros adicionales. Opera a ciegas: ve lo que ya se entregó a su barra, nunca el saldo del depósito.
 * La encargada de barra, Compras, Coordinación y Dirección.
 */
export function PaginaBarra() {
  const jornada = useDatos(entregas.jornada);
  return (
    <section className="pantalla">
      {/* El título es el del menú: acá también van la devolución y el cierre de la noche. */}
      <Encabezado titulo="Barra" antetitulo="Bebidas" />
      <Cargando datos={jornada}>
        {(lista) => {
          const lugares = opciones(lista);
          if (lugares.length === 0) {
            return (
              <Card flush>
                <EmptyState icon="martini" title="Hoy no hay eventos en tus barras">
                  Cuando haya un evento en tu salón, vas a poder retirar bebida para su barra desde acá.
                </EmptyState>
              </Card>
            );
          }
          return <Seleccion lugares={lugares} />;
        }}
      </Cargando>
    </section>
  );
}

function Seleccion({ lugares }: { lugares: Opcion[] }) {
  const [elegido, setElegido] = useState(lugares[0].valor);
  const lugar = lugares.find((l) => l.valor === elegido) ?? lugares[0];
  return (
    <>
      {lugares.length > 1 && (
        <Select
          label="Evento y barra"
          size="lg"
          value={elegido}
          onChange={(e) => setElegido(e.target.value)}
          options={lugares.map((l) => ({ value: l.valor, label: l.etiqueta }))}
        />
      )}
      <PantallaEntrega key={lugar.valor} eventoId={lugar.eventoId} barraId={lugar.barraId} />
    </>
  );
}

function PantallaEntrega({ eventoId, barraId }: { eventoId: number; barraId: number }) {
  const cargar = useCallback(() => entregas.pantalla(eventoId, barraId), [eventoId, barraId]);
  const datos = useDatos(cargar);
  return (
    <Cargando datos={datos}>
      {(entrega) => <ContenidoEntrega entrega={entrega} alRegistrar={(nueva) => datos.fijar(() => nueva)} />}
    </Cargando>
  );
}

/**
 * Con el evento en curso: retiros y, cuando termina la noche, la devolución. Con el evento realizado y la entrega
 * abierta, directo a la devolución. Después de devolver, el consumo real.
 */
function ContenidoEntrega({ entrega, alRegistrar }: { entrega: Entrega; alRegistrar: (e: Entrega) => void }) {
  const [devolviendo, setDevolviendo] = useState(false);
  const [devolucion, setDevolucion] = useState<Devolucion | null>(null);
  const estado = ESTADO_POR_CODIGO[entrega.evento.estado as keyof typeof ESTADO_POR_CODIGO];
  const enCurso = entrega.evento.estado === 'EN_CURSO';
  const realizado = entrega.evento.estado === 'REALIZADO';
  const abierta = entrega.estadoEntrega === 'ABIERTA';
  const cerrada = entrega.estadoEntrega === 'CERRADA';
  const puedeDevolver = abierta && (enCurso || realizado);

  const cabecera = (
    <Card
      title={entrega.evento.nombre}
      subtitle={`${entrega.barra.nombre} · sale de ${entrega.origen.nombre}`}
      actions={estado && <StatusChip status={estado} size="sm" />}
    >
      <SalonTag salon={entrega.salon.codigo as SalonId} label={entrega.salon.nombre} />
    </Card>
  );

  if (devolucion) {
    return <>{cabecera}<ConsumoDeLaDevolucion devolucion={devolucion} /></>;
  }
  if (puedeDevolver && (devolviendo || realizado)) {
    return (
      <>
        {cabecera}
        <DevolucionBarra
          entrega={entrega}
          alCancelar={enCurso ? () => setDevolviendo(false) : null}
          alRegistrar={(d) => { setDevolucion(d); alRegistrar(d.entrega); }}
        />
      </>
    );
  }
  return (
    <>
      {cabecera}

      {!enCurso && !cerrada && (
        <Alert tone="warning" icon="clock" title={`El evento está ${estado ? ESTADOS[estado].label.toLowerCase() : entrega.evento.estado}`}>
          {entrega.evento.estado === 'CONFIRMADO'
            ? 'La salida a barra se registra con el evento en curso: empieza sola a la hora de inicio del turno.'
            : 'La salida a barra se registra con el evento en curso.'}
        </Alert>
      )}
      {cerrada && (
        <Alert tone="info" title={`La entrega a ${entrega.barra.nombre} ya se cerró`}>
          Ya se registró la devolución: no admite más retiros.
        </Alert>
      )}

      <YaEntregado entrega={entrega} />

      {enCurso && abierta && (
        <div>
          <Button variant="outline" icon="undo-2" size="lg" onClick={() => setDevolviendo(true)}>Registrar devolución</Button>
        </div>
      )}

      {enCurso && !cerrada && <NuevoRetiro entrega={entrega} alRegistrar={alRegistrar} />}
    </>
  );
}

function YaEntregado({ entrega }: { entrega: Entrega }) {
  if (entrega.retiros.length === 0) {
    return <p className="body v-muted">Todavía no se entregó nada a {entrega.barra.nombre} para este evento.</p>;
  }
  return (
    <section className="barra__entregado" aria-label="Ya entregado">
      <h2 className="h4">Ya entregado</h2>
      <Card>
        <ul className="barra__lista">
          {entrega.yaEntregado.map((e) => (
            <li key={e.bebidaId}>
              <span>{e.nombre} {e.presentacion}</span>
              <span className="numeral-sm">{cantidadLegible(e.cantidad, e.unidadesPorBulto, e.unidad)}</span>
            </li>
          ))}
        </ul>
      </Card>
      {entrega.retiros.map((r, i) => (
        <MovementCard
          key={i}
          tipo={r.tipo === 'SALIDA_BARRA' ? 'entrega' : 'retiro-adicional'}
          desde={r.origen.nombre}
          hasta={entrega.barra.nombre}
          items={r.renglones.map((x) => ({
            nombre: `${x.nombre} ${x.presentacion}`, cantidad: x.cantidad,
            cantidadTexto: cantidadLegible(x.cantidad, x.unidadesPorBulto, x.unidad),
          }))}
          actor={r.usuario.nombre}
          at={fechaHora(r.fechaHora)}
        />
      ))}
    </section>
  );
}

function NuevoRetiro({ entrega, alRegistrar }: { entrega: Entrega; alRegistrar: (e: Entrega) => void }) {
  const [renglones, setRenglones] = useState<Renglon[]>([]);
  const [buscar, setBuscar] = useState('');
  const [directo, setDirecto] = useState(false);
  const [lector, setLector] = useState(false);
  const [lectura, setLectura] = useState<{ texto: string; ok: boolean } | null>(null);
  const [revisando, setRevisando] = useState(false);
  const [falta, setFalta] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const { guardando, error, enviar, limpiarError } = useEnvio();

  const primero = entrega.estadoEntrega === null;
  const origen = directo && entrega.depositoMadre ? entrega.depositoMadre : entrega.origen;
  const agregadas = new Set(renglones.map((r) => r.bebida.id));
  const sugerencias: ComboboxOption[] = buscar.trim()
    ? entrega.bebidas
      .filter((b) => !agregadas.has(b.id) && coincide(b, buscar))
      .slice(0, 8)
      .map((b) => ({ value: String(b.id), label: b.nombre, detail: presentacion(b) }))
    : [];

  function agregar(bebida: BebidaParaRetiro) {
    setRenglones((previos) => (previos.some((r) => r.bebida.id === bebida.id) ? previos : [...previos, { bebida, cantidad: 0 }]));
    setAviso(null);
    setFalta(null);
  }

  /** El código identifica la bebida; la cantidad se carga en su tarjeta. Solo se ofrecen las activas. */
  function leer(codigo: string) {
    const bebida = entrega.bebidas.find((b) => b.codigos.some((c) => c.codigo === codigo));
    if (!bebida) {
      setLectura({ texto: `El código ${codigo} no está en el catálogo. Elegí la bebida a mano.`, ok: false });
    } else if (agregadas.has(bebida.id)) {
      setLectura({ texto: `${bebida.nombre} ya está en el retiro: cargá la cantidad en su tarjeta.`, ok: true });
    } else {
      agregar(bebida);
      setLectura({ texto: `${bebida.nombre} · ${presentacion(bebida)} agregada.`, ok: true });
    }
  }

  function revisar() {
    const sinCantidad = renglones.find((r) => r.cantidad <= 0);
    if (sinCantidad) {
      setFalta(`Cargá la cantidad de ${sinCantidad.bebida.nombre} o sacala del retiro.`);
      return;
    }
    limpiarError();
    setRevisando(true);
  }

  async function registrar() {
    const nueva = await enviar(() => entregas.retirar(entrega.evento.id, entrega.barra.id, {
      directo: directo && !!entrega.depositoMadre,
      renglones: renglones.map((r) => ({ bebidaId: r.bebida.id, cantidad: r.cantidad })),
    }));
    if (nueva) {
      setAviso(`${primero ? 'Entrega registrada' : 'Retiro adicional registrado'}: ${resumenDeSalida(renglones, entrega.barra.nombre)}`);
      setRevisando(false);
      setRenglones([]);
      setDirecto(false);
      alRegistrar(nueva);
    }
  }

  return (
    <section className="barra__retiro" aria-label="Nuevo retiro">
      <h2 className="h4">{primero ? 'Entrega a la barra' : 'Retiro adicional'}</h2>
      {aviso && <Alert tone="success">{aviso}</Alert>}
      {entrega.depositoMadre && (
        <Switch
          label={`Retirar del depósito madre (${entrega.depositoMadre.nombre})`}
          hint={`Solo como contingencia: lo habitual es retirar de ${entrega.origen.nombre}.`}
          checked={directo}
          onChange={(e) => setDirecto(e.target.checked)}
        />
      )}

      <div className="barra__agregar">
        <Combobox
          label="Agregar bebida"
          icon="search"
          placeholder="Nombre o código de barras"
          value={buscar}
          onInputChange={setBuscar}
          options={sugerencias}
          emptyText="No hay bebidas activas con ese nombre o código."
          onSelect={(o) => {
            const bebida = entrega.bebidas.find((b) => String(b.id) === o.value);
            if (bebida) agregar(bebida);
            setBuscar('');
          }}
        />
        <Button variant="outline" tone="brand" icon="scan-line" size="lg" onClick={() => { setLectura(null); setLector(true); }}>
          Escanear caja
        </Button>
      </div>

      {renglones.length === 0 ? (
        <Card flush>
          <EmptyState icon="martini" title="Todavía no cargaste bebidas">
            Buscalas por nombre o escaneá el código de la caja con la cámara.
          </EmptyState>
        </Card>
      ) : (
        <ul className="barra__renglones">
          {renglones.map((r) => (
            <li key={r.bebida.id}>
              <Card
                title={r.bebida.nombre}
                subtitle={presentacion(r.bebida)}
                actions={<IconButton icon="x" label={`Sacar ${r.bebida.nombre} del retiro`} variant="text" onClick={() => setRenglones((p) => p.filter((x) => x.bebida.id !== r.bebida.id))} />}
              >
                <CantidadEnCajas
                  label={`Cantidad de ${r.bebida.nombre}`}
                  value={r.cantidad}
                  onChange={(n) => { setRenglones((p) => p.map((x) => (x.bebida.id === r.bebida.id ? { ...x, cantidad: n } : x))); setFalta(null); }}
                  porBulto={r.bebida.unidadesPorBulto}
                  unidad={r.bebida.unidad}
                />
              </Card>
            </li>
          ))}
        </ul>
      )}

      {renglones.length > 0 && (
        <div className="barra__pie">
          {falta && <Alert tone="warning">{falta}</Alert>}
          <p className="body">{resumenDeSalida(renglones, entrega.barra.nombre)}</p>
          <Button size="lg" icon="check" onClick={revisar}>Revisar retiro</Button>
        </div>
      )}

      <LectorCodigo
        open={lector}
        onClose={() => setLector(false)}
        onRead={leer}
        onManual={() => setLector(false)}
        instruction="Apuntá al código de la caja. Podés escanear varias seguidas."
      >
        {lectura && <span className={lectura.ok ? 'body' : 'body v-field__error'}>{lectura.texto}</span>}
      </LectorCodigo>

      {revisando && (
        <Dialog
          open
          title="Revisá el retiro"
          onClose={() => setRevisando(false)}
          actions={
            <>
              <Button variant="outline" onClick={() => setRevisando(false)}>Volver a editar</Button>
              <Button icon="check" loading={guardando} onClick={() => void registrar()}>
                {primero ? 'Entregar a barra' : 'Registrar retiro adicional'}
              </Button>
            </>
          }
        >
          <div className="barra__revision">
            {error && <Alert tone="danger">{error.message}</Alert>}
            <p>{resumenDeSalida(renglones, entrega.barra.nombre)} Sale de {origen.nombre}, para {entrega.evento.nombre}:</p>
            <ul className="barra__lista">
              {renglones.map((r) => (
                <li key={r.bebida.id}>
                  <span>{r.bebida.nombre} {r.bebida.presentacion}</span>
                  <span className="numeral-sm">{cantidadLegible(r.cantidad, r.bebida.unidadesPorBulto, r.bebida.unidad)}</span>
                </li>
              ))}
            </ul>
            <p className="body-sm v-muted">Un retiro registrado no se modifica: si hay una diferencia, se corrige con un ajuste.</p>
          </div>
        </Dialog>
      )}
    </section>
  );
}
