import { useState } from 'react';
import { bebidas, presentacionDeBulto, type Bebida } from '../../api/bebidas';
import { ingresos, type Ingreso } from '../../api/ingresos';
import { ubicaciones } from '../../api/ubicaciones';
import { useDatos, useEnvio } from '../../api/useDatos';
import {
  Actor, Alert, Button, CantidadEnCajas, Card, Combobox, Dialog, EmptyState, IconButton, Input, LectorCodigo,
  cantidadLegible, type ComboboxOption,
} from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta, fechaHora, hoy } from '../comun/formato';
import { Encabezado } from '../layout/paginas';
import './ingreso-bebida.css';

interface Datos {
  catalogo: Bebida[];
  deposito: { id: number; nombre: string } | undefined;
  recientes: Ingreso[];
}

const cargar = (): Promise<Datos> =>
  Promise.all([bebidas.listar(), ubicaciones.listar(), ingresos.recientes()]).then(([catalogo, lista, recientes]) => ({
    catalogo,
    deposito: lista.find((u) => u.tipo === 'DEPOSITO' && u.activo),
    recientes,
  }));

interface Renglon {
  bebida: Bebida;
  /** En botellas. */
  cantidad: number;
}

/** Por nombre, presentación o código de barras. */
function coincide(b: Bebida, texto: string): boolean {
  const t = texto.trim().toLowerCase();
  return !t || `${b.nombre} ${b.presentacion}`.toLowerCase().includes(t) || b.codigos.some((c) => c.codigo.includes(t));
}

const plural = (n: number, uno: string, varios: string) => `${n} ${n === 1 ? uno : varios}`;

/**
 * UI-29 · Registrar ingreso de bebida: la mercadería comprada entra al depósito madre. Las bebidas se agregan con el
 * buscador o leyendo el código con la cámara (el lector queda abierto para varias cajas); la cantidad se carga en
 * cajas y botellas sueltas. Antes de registrar se revisa: un ingreso no se edita ni se anula. Abajo, los últimos
 * ingresos con quién y cuándo. Dirección, Coordinación, Administración y Compras.
 */
export function PaginaIngresoBebida() {
  const datos = useDatos(cargar);
  return (
    <section className="pantalla">
      <Encabezado titulo="Ingreso de mercadería" antetitulo="Bebidas" />
      <Cargando datos={datos}>
        {({ catalogo, deposito, recientes }) =>
          deposito ? (
            <FormularioIngreso
              catalogo={catalogo}
              deposito={deposito}
              recientes={recientes}
              alRegistrar={(nuevo) => datos.fijar((previos) => ({ ...(previos as Datos), recientes: [nuevo, ...(previos?.recientes ?? [])].slice(0, 10) }))}
            />
          ) : (
            <Alert tone="warning" icon="triangle-alert" title="No hay un depósito madre activo">
              Configuralo en Parámetros, Ubicaciones de stock, para poder registrar ingresos.
            </Alert>
          )
        }
      </Cargando>
    </section>
  );
}

function FormularioIngreso({ catalogo, deposito, recientes, alRegistrar }: {
  catalogo: Bebida[];
  deposito: { id: number; nombre: string };
  recientes: Ingreso[];
  alRegistrar: (i: Ingreso) => void;
}) {
  const [fecha, setFecha] = useState(hoy());
  const [remito, setRemito] = useState('');
  const [renglones, setRenglones] = useState<Renglon[]>([]);
  const [buscar, setBuscar] = useState('');
  const [lector, setLector] = useState(false);
  const [lectura, setLectura] = useState<{ texto: string; ok: boolean } | null>(null);
  const [revisando, setRevisando] = useState(false);
  const [falta, setFalta] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const { guardando, error, enviar, limpiarError } = useEnvio();

  const activas = catalogo.filter((b) => b.activo);
  const agregadas = new Set(renglones.map((r) => r.bebida.id));
  const opciones: ComboboxOption[] = buscar.trim()
    ? activas
      .filter((b) => !agregadas.has(b.id) && coincide(b, buscar))
      .slice(0, 8)
      .map((b) => ({ value: String(b.id), label: b.nombre, detail: `${b.presentacion} · ${presentacionDeBulto(b)}` }))
    : [];

  function agregar(bebida: Bebida) {
    setRenglones((previos) => (previos.some((r) => r.bebida.id === bebida.id) ? previos : [...previos, { bebida, cantidad: 0 }]));
    setAviso(null);
    setFalta(null);
  }

  /** El código identifica la bebida; la cantidad se carga en su tarjeta. */
  function leer(codigo: string) {
    const bebida = catalogo.find((b) => b.codigos.some((c) => c.codigo === codigo));
    if (!bebida) {
      setLectura({ texto: `El código ${codigo} no está en el catálogo. Elegí la bebida a mano.`, ok: false });
    } else if (!bebida.activo) {
      setLectura({ texto: `${bebida.nombre} ${bebida.presentacion} está dada de baja: no se puede ingresar.`, ok: false });
    } else if (agregadas.has(bebida.id)) {
      setLectura({ texto: `${bebida.nombre} ya está en el ingreso: cargá la cantidad en su tarjeta.`, ok: true });
    } else {
      agregar(bebida);
      setLectura({ texto: `${bebida.nombre} · ${presentacionDeBulto(bebida)} agregada.`, ok: true });
    }
  }

  function cambiar(id: number, cantidad: number) {
    setRenglones((previos) => previos.map((r) => (r.bebida.id === id ? { ...r, cantidad } : r)));
    setFalta(null);
  }

  function revisar() {
    if (!fecha) {
      setFalta('Elegí la fecha de ingreso.');
      return;
    }
    const sinCantidad = renglones.find((r) => r.cantidad <= 0);
    if (sinCantidad) {
      setFalta(`Cargá la cantidad de ${sinCantidad.bebida.nombre} o sacala del ingreso.`);
      return;
    }
    limpiarError();
    setRevisando(true);
  }

  async function registrar() {
    const registrado = await enviar(() =>
      ingresos.registrar({ fecha, numeroRemito: remito, renglones: renglones.map((r) => ({ bebidaId: r.bebida.id, cantidad: r.cantidad })) }),
    );
    if (registrado) {
      alRegistrar(registrado);
      setRevisando(false);
      setRenglones([]);
      setRemito('');
      setFecha(hoy());
      setAviso(`Ingreso registrado: ${plural(registrado.renglones.length, 'bebida', 'bebidas')} al ${registrado.destino.nombre}.`);
    }
  }

  return (
    <>
      {aviso && <Alert tone="success">{aviso}</Alert>}
      <Card>
        <div className="ingreso-bebida__cabecera">
          <Input label="Entra a" value={deposito.nombre} readOnly size="lg" hint="Los ingresos van siempre al depósito madre." />
          <Input label="Fecha de ingreso" type="date" size="lg" value={fecha} onChange={(e) => setFecha(e.target.value)} />
          <Input label="Número de remito" optional size="lg" value={remito} onChange={(e) => setRemito(e.target.value)} />
        </div>
      </Card>

      <div className="ingreso-bebida__agregar">
        <Combobox
          label="Agregar bebida"
          icon="search"
          placeholder="Nombre o código de barras"
          value={buscar}
          onInputChange={setBuscar}
          options={opciones}
          emptyText="No hay bebidas activas con ese nombre o código."
          onSelect={(o) => {
            const bebida = activas.find((b) => String(b.id) === o.value);
            if (bebida) agregar(bebida);
            setBuscar('');
          }}
        />
        <Button variant="outline" icon="scan-line" size="lg" onClick={() => { setLectura(null); setLector(true); }}>Leer código</Button>
      </div>

      {renglones.length === 0 ? (
        <Card flush>
          <EmptyState icon="truck" title="Todavía no cargaste bebidas">
            Buscalas por nombre o leé el código de la caja con la cámara.
          </EmptyState>
        </Card>
      ) : (
        <ul className="ingreso-bebida__renglones">
          {renglones.map((r) => (
            <li key={r.bebida.id}>
              <Card
                title={r.bebida.nombre}
                subtitle={`${r.bebida.presentacion} · ${presentacionDeBulto(r.bebida)}`}
                actions={<IconButton icon="x" label={`Sacar ${r.bebida.nombre} del ingreso`} variant="text" onClick={() => setRenglones((p) => p.filter((x) => x.bebida.id !== r.bebida.id))} />}
              >
                <CantidadEnCajas
                  label={`Cantidad de ${r.bebida.nombre}`}
                  value={r.cantidad}
                  onChange={(n) => cambiar(r.bebida.id, n)}
                  porBulto={r.bebida.unidadesPorBulto}
                  unidad={r.bebida.unidad.nombre}
                />
              </Card>
            </li>
          ))}
        </ul>
      )}

      {renglones.length > 0 && (
        <div className="ingreso-bebida__pie">
          {falta && <Alert tone="warning">{falta}</Alert>}
          <p className="body">
            Ingresan {plural(renglones.length, 'bebida', 'bebidas')} al {deposito.nombre}.
          </p>
          <Button size="lg" icon="check" onClick={revisar}>Revisar ingreso</Button>
        </div>
      )}

      <UltimosIngresos recientes={recientes} />

      <LectorCodigo
        open={lector}
        onClose={() => setLector(false)}
        onRead={leer}
        onManual={() => setLector(false)}
        instruction="Apuntá al código de la caja. Podés leer varias seguidas."
      >
        {lectura && <span className={lectura.ok ? 'body' : 'body v-field__error'}>{lectura.texto}</span>}
      </LectorCodigo>

      {revisando && (
      <Dialog
        open
        title="Revisá el ingreso"
        onClose={() => setRevisando(false)}
        actions={
          <>
            <Button variant="outline" onClick={() => setRevisando(false)}>Volver a editar</Button>
            <Button icon="check" loading={guardando} onClick={() => void registrar()}>Registrar ingreso</Button>
          </>
        }
      >
        <div className="ingreso-bebida__revision">
          {error && <Alert tone="danger">{error.message}</Alert>}
          <p>
            Entran al {deposito.nombre} el {fechaCorta(fecha, true)}{remito.trim() && `, remito ${remito.trim()}`}:
          </p>
          <ul className="ingreso-bebida__lista">
            {renglones.map((r) => (
              <li key={r.bebida.id}>
                <span>{r.bebida.nombre} {r.bebida.presentacion}</span>
                <span className="numeral-sm">{cantidadLegible(r.cantidad, r.bebida.unidadesPorBulto, r.bebida.unidad.nombre)}</span>
              </li>
            ))}
          </ul>
          <p className="body-sm v-muted">Un ingreso registrado no se modifica: si después hay una diferencia, se corrige con un recuento.</p>
        </div>
      </Dialog>
      )}
    </>
  );
}

function UltimosIngresos({ recientes }: { recientes: Ingreso[] }) {
  if (recientes.length === 0) return null;
  return (
    <Card title="Últimos ingresos">
      <ul className="ingreso-bebida__recientes">
        {recientes.map((i) => (
          <li key={i.id}>
            <p className="label">
              {fechaCorta(i.fechaIngreso, true)}{i.numeroRemito && ` · remito ${i.numeroRemito}`}
            </p>
            <ul className="ingreso-bebida__lista">
              {i.renglones.map((r) => (
                <li key={r.bebidaId}>
                  <span>{r.nombre} {r.presentacion}</span>
                  <span className="numeral-sm">{cantidadLegible(r.cantidad, r.unidadesPorBulto, r.unidad)}</span>
                </li>
              ))}
            </ul>
            <Actor name={i.usuario.nombre} action="registró el ingreso" at={fechaHora(i.fechaRegistro)} size="sm" />
          </li>
        ))}
      </ul>
    </Card>
  );
}
