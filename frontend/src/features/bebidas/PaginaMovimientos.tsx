import { useCallback, useState } from 'react';
import { bebidas } from '../../api/bebidas';
import {
  movimientos, NOMBRE_DE_MOVIMIENTO, type FiltrosMovimientos, type OperacionDeStock, type TipoMovimiento,
} from '../../api/movimientos';
import { ubicaciones } from '../../api/ubicaciones';
import { useDatos, useEnvio } from '../../api/useDatos';
import {
  Alert, Button, Card, EmptyState, Input, MovementCard, Select, cantidadLegible, type SalonId, type TipoMovimientoCard,
} from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta, fechaHora } from '../comun/formato';
import './movimientos.css';

const TARJETA: Record<TipoMovimiento, TipoMovimientoCard> = {
  INGRESO: 'ingreso',
  INVENTARIO_INICIAL: 'inventario-inicial',
  PREPARACION: 'preparacion',
  SALIDA_BARRA: 'entrega',
  RETIRO_ADICIONAL: 'retiro-adicional',
  DEVOLUCION: 'devolucion',
  REMANENTE: 'remanente',
  CONSUMO: 'consumo',
  AJUSTE: 'ajuste',
  MERMA: 'merma',
};

const cargarOpciones = () => Promise.all([ubicaciones.listar(), bebidas.listar(), movimientos.eventos()])
  .then(([lugares, catalogo, eventos]) => ({ lugares, catalogo, eventos }));

/**
 * UI-37 · Movimientos de stock (docs/sprint-4.md, decisiones 26 a 28): el historial para auditar una ubicación, una
 * bebida o un evento. Cada tarjeta es una operación (un ingreso, una salida a barra, una devolución) con sus bebidas en
 * cajas y botellas. Los movimientos no se editan: se corrigen con un ajuste.
 */
export function PaginaMovimientos() {
  const opciones = useDatos(cargarOpciones);
  return (
    <section className="pantalla">
      <div className="pantalla__titulos">
        <h1>Movimientos de stock</h1>
        <p className="body v-muted">Todo lo que entró, salió o se corrigió, de lo más nuevo a lo más viejo.</p>
      </div>
      <Cargando datos={opciones}>{(o) => <Consulta {...o} />}</Cargando>
    </section>
  );
}

function Consulta({ lugares, catalogo, eventos }: Awaited<ReturnType<typeof cargarOpciones>>) {
  const [borrador, setBorrador] = useState<FiltrosMovimientos>({});
  const [filtros, setFiltros] = useState<FiltrosMovimientos>({});
  const cargar = useCallback(() => movimientos.consultar(filtros, 0), [filtros]);
  const primera = useDatos(cargar);
  const [mas, setMas] = useState<{ operaciones: OperacionDeStock[]; pagina: number; hayMas: boolean } | null>(null);
  const { guardando, error, enviar } = useEnvio();

  function consultar() {
    setMas(null);
    setFiltros({ ...borrador });
  }

  async function verMas(hayMasAntes: boolean, pagina: number) {
    if (!hayMasAntes) return;
    const siguiente = await enviar(() => movimientos.consultar(filtros, pagina + 1));
    if (siguiente) {
      setMas((previo) => ({
        operaciones: [...(previo?.operaciones ?? []), ...siguiente.operaciones], pagina: pagina + 1, hayMas: siguiente.hayMas,
      }));
    }
  }

  const numero = (texto: string) => (texto ? Number(texto) : undefined);
  return (
    <>
      <Card className="movimientos__filtros">
        <div className="movimientos__campos">
          <Select
            label="Ubicación"
            placeholder="Todas"
            options={[{ value: '', label: 'Todas' }, ...lugares.map((u) => ({ value: String(u.id), label: u.nombre }))]}
            value={String(borrador.ubicacionId ?? '')}
            onChange={(e) => setBorrador((b) => ({ ...b, ubicacionId: numero(e.target.value) }))}
          />
          <Select
            label="Bebida"
            options={[{ value: '', label: 'Todas' }, ...catalogo.map((b) => ({ value: String(b.id), label: `${b.nombre} ${b.presentacion}` }))]}
            value={String(borrador.bebidaId ?? '')}
            onChange={(e) => setBorrador((b) => ({ ...b, bebidaId: numero(e.target.value) }))}
          />
          <Select
            label="Evento"
            options={[{ value: '', label: 'Todos' }, ...eventos.map((e) => ({ value: String(e.id), label: `${e.nombre} · ${fechaCorta(e.fecha)}` }))]}
            value={String(borrador.eventoId ?? '')}
            onChange={(e) => setBorrador((b) => ({ ...b, eventoId: numero(e.target.value) }))}
          />
          <Select
            label="Tipo"
            options={[{ value: '', label: 'Todos' }, ...Object.entries(NOMBRE_DE_MOVIMIENTO).map(([valor, nombre]) => ({ value: valor, label: nombre }))]}
            value={borrador.tipo ?? ''}
            onChange={(e) => setBorrador((b) => ({ ...b, tipo: (e.target.value || undefined) as TipoMovimiento | undefined }))}
          />
          <Input label="Desde" type="date" value={borrador.desde ?? ''} onChange={(e) => setBorrador((b) => ({ ...b, desde: e.target.value || undefined }))} />
          <Input label="Hasta" type="date" value={borrador.hasta ?? ''} onChange={(e) => setBorrador((b) => ({ ...b, hasta: e.target.value || undefined }))} />
        </div>
        <Button icon="search" onClick={consultar}>Consultar movimientos</Button>
      </Card>
      <Cargando datos={primera}>
        {(pagina) => {
          const lista = [...pagina.operaciones, ...(mas?.operaciones ?? [])];
          const hayMas = mas ? mas.hayMas : pagina.hayMas;
          if (lista.length === 0) {
            return <Card><EmptyState icon="history" title="Sin movimientos">No hay movimientos con estos filtros.</EmptyState></Card>;
          }
          return (
            <div className="movimientos__lista" aria-label="Movimientos">
              {lista.map((o) => <Tarjeta key={`${o.tipo}-${o.asientos[0]?.movimientoId ?? o.fechaHora}`} operacion={o} />)}
              {error && <Alert tone="danger">{error.message}</Alert>}
              {hayMas && (
                <Button variant="outline" loading={guardando} onClick={() => void verMas(hayMas, mas?.pagina ?? 0)}>Ver más movimientos</Button>
              )}
            </div>
          );
        }}
      </Cargando>
    </>
  );
}

function Tarjeta({ operacion: o }: { operacion: OperacionDeStock }) {
  const corrige = o.asientos.map((a) => a.corrige).filter((c): c is number => c !== null);
  const nota = [
    o.motivo && `Motivo: ${o.motivo}`,
    o.observacion,
    corrige.length > 0 && `Corrige el movimiento ${corrige.join(', ')}`,
  ].filter(Boolean).join(' · ');
  return (
    <MovementCard
      tipo={TARJETA[o.tipo]}
      evento={o.evento ? `${o.evento.nombre} · ${o.evento.codigo}` : undefined}
      salon={o.evento?.salon as SalonId | undefined}
      desde={o.origen?.nombre}
      hasta={o.destino?.nombre ?? (o.origen ? 'Sale del stock' : undefined)}
      items={o.asientos.map((a) => ({
        nombre: `${a.nombre} ${a.presentacion}`, cantidad: a.cantidad, cantidadTexto: cantidadLegible(a.cantidad, a.unidadesPorBulto, a.unidad),
      }))}
      nota={nota || undefined}
      actor={o.usuario.nombre}
      at={fechaHora(o.fechaHora)}
    />
  );
}
