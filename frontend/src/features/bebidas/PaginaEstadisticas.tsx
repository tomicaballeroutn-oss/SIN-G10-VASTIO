import { useCallback, useState, type FormEvent } from 'react';
import { bebidas } from '../../api/bebidas';
import { catalogos } from '../../api/catalogos';
import { consultarEstadisticas, type FiltrosEstadisticas, type MedidasConsumo } from '../../api/estadisticas';
import { useDatos } from '../../api/useDatos';
import { Alert, Button, Card, EmptyState, Input, Select, Stat, Table, type TableColumn } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { Encabezado } from '../layout/paginas';
import './estadisticas.css';

const cargarCatalogos = async () => {
  const [tipos, salones, articulos] = await Promise.all([catalogos.tiposEvento(), catalogos.salones(), bebidas.listar()]);
  return { tipos, salones, articulos };
};
const NUMERO = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 4 });
const numero = (n: number | null) => n === null ? 'No disponible' : NUMERO.format(n);
const estado = (activo: boolean) => activo ? '' : ' · Dado de baja';
type Catalogos = Awaited<ReturnType<typeof cargarCatalogos>>;

export function PaginaEstadisticas() {
  const datos = useDatos(cargarCatalogos);
  return <section className="pantalla">
    <Encabezado titulo="Estadísticas de consumo" antetitulo="Bebidas" />
    <Cargando datos={datos}>{(lista) => <Consulta lista={lista} />}</Cargando>
  </section>;
}

function fechaValida(fecha: string) {
  if (!fecha) return true;
  if (!/^(?!0000)\d{4}-\d{2}-\d{2}$/.test(fecha)) return false;
  const dia = new Date(`${fecha}T00:00:00Z`);
  return !Number.isNaN(dia.getTime()) && dia.toISOString().slice(0, 10) === fecha;
}

function Consulta({ lista }: { lista: Catalogos }) {
  const [desde, setDesde] = useState('');
  const [hasta, setHasta] = useState('');
  const [tipo, setTipo] = useState('');
  const [salon, setSalon] = useState('');
  const [bebida, setBebida] = useState('');
  const [error, setError] = useState<string>();
  const [consulta, setConsulta] = useState<{ filtros: FiltrosEstadisticas; numero: number }>();
  function consultar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!e.currentTarget.checkValidity() || !fechaValida(desde) || !fechaValida(hasta) || (desde && hasta && desde > hasta)) {
      setError('Revisá el período: usá fechas válidas y una fecha desde anterior o igual a hasta.');
      return;
    }
    setError(undefined);
    setConsulta((anterior) => ({ numero: (anterior?.numero ?? 0) + 1, filtros: {
      desde: desde || undefined, hasta: hasta || undefined,
      tipoEventoId: tipo ? Number(tipo) : undefined, salonId: salon ? Number(salon) : undefined,
      bebidaId: bebida ? Number(bebida) : undefined,
    } }));
  }
  return <>
    <Card title="Filtros de consulta" subtitle="Solo eventos cerrados. El período corresponde a la fecha de realización.">
      <form noValidate onSubmit={consultar} className="estadisticas__filtros">
        <Input label="Fecha desde" type="date" value={desde} onChange={(e) => setDesde(e.target.value)} error={error} />
        <Input label="Fecha hasta" type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
        <Select label="Tipo de evento" value={tipo} onChange={(e) => setTipo(e.target.value)} options={[
          { value: '', label: 'Todos los tipos' }, ...lista.tipos.map((t) => ({ value: String(t.id), label: t.nombre + estado(t.activo) })),
        ]} />
        <Select label="Salón" value={salon} onChange={(e) => setSalon(e.target.value)} options={[
          { value: '', label: 'Todos los salones' }, ...lista.salones.map((s) => ({ value: String(s.id), label: s.nombre + estado(s.activo) })),
        ]} />
        <Select label="Bebida" value={bebida} onChange={(e) => setBebida(e.target.value)} options={[
          { value: '', label: 'Todas las bebidas' }, ...lista.articulos.map((b) => ({ value: String(b.id), label: `${b.nombre} ${b.presentacion}${estado(b.activo)}` })),
        ]} />
        <Button type="submit" icon="chart-column">Consultar estadísticas</Button>
      </form>
    </Card>
    {consulta ? <Resultados key={consulta.numero} filtros={consulta.filtros} lista={lista} /> : (
      <Card flush><EmptyState icon="chart-column" title="Consultá el consumo histórico">
        Combiná los filtros y seleccioná Consultar estadísticas. Sin fechas se consulta todo el historial.
      </EmptyState></Card>
    )}
  </>;
}

interface Fila { id: string; etiqueta: string; medidas: MedidasConsumo }
function Tabla({ titulo, filas }: { titulo: string; filas: Fila[] }) {
  const columnas: TableColumn<Fila>[] = [
    { key: 'grupo', header: 'Grupo', render: (f) => f.etiqueta },
    { key: 'eventos', header: 'Eventos cerrados', numeric: true, render: (f) => numero(f.medidas.eventos) },
    { key: 'registros', header: 'Eventos con registro', numeric: true, render: (f) => numero(f.medidas.eventosConConsumoRegistrado) },
    { key: 'total', header: 'Consumo total (botellas)', numeric: true, render: (f) => numero(f.medidas.consumoTotal) },
    { key: 'porEvento', header: 'Botellas por evento', numeric: true, render: (f) => numero(f.medidas.promedioPorEvento) },
    { key: 'porInvitado', header: 'Botellas por invitado registrado', numeric: true, render: (f) => numero(f.medidas.porInvitadoRegistrado) },
    { key: 'validos', header: 'Eventos con invitados válidos', numeric: true, render: (f) => numero(f.medidas.eventosConInvitadosValidos) },
    { key: 'invitados', header: 'Invitados registrados', numeric: true, render: (f) => numero(f.medidas.invitadosRegistrados) },
  ];
  return <Card title={titulo}>
    <p className="body-sm v-muted">Si no ves todas las columnas, desplazá la tabla hacia los costados.</p>
    <Table className="estadisticas__tabla" caption={titulo} dense rows={filas} columns={columnas} empty="No hay grupos para esta consulta." />
  </Card>;
}

function Resultados({ filtros, lista }: { filtros: FiltrosEstadisticas; lista: Catalogos }) {
  const cargar = useCallback(() => consultarEstadisticas(filtros), [filtros]);
  const datos = useDatos(cargar);
  const seleccion = [
    `Desde: ${filtros.desde ?? 'sin límite'}`, `Hasta: ${filtros.hasta ?? 'sin límite'}`,
    lista.tipos.find((t) => t.id === filtros.tipoEventoId)?.nombre ?? 'Todos los tipos',
    lista.salones.find((s) => s.id === filtros.salonId)?.nombre ?? 'Todos los salones',
    lista.articulos.filter((b) => b.id === filtros.bebidaId).map((b) => `${b.nombre} ${b.presentacion}`)[0] ?? 'Todas las bebidas',
  ].join(' · ');
  return <div className="pantalla" aria-label="Resultados de estadísticas" aria-busy={datos.cargando}>
    <p className="body-sm">Filtros aplicados: {seleccion}</p>
    <Cargando datos={datos}>{(r) => <>
      <Alert tone="info" title="Consumo por invitado registrado">
        Se usan invitados registrados, no asistencia real. Se excluyen del consumo y del denominador los eventos con invitados desconocidos o cero.
        Los eventos sin consumo cuentan en los promedios. Todas las cantidades se expresan en botellas.
      </Alert>
      {r.resumen.eventos === 0 ? <Alert tone="info" title="No hay eventos cerrados para estos filtros">Probá con otro período o combinación de filtros.</Alert>
        : r.resumen.eventosConConsumoRegistrado === 0
          ? <Alert tone="info" title="Eventos cerrados sin registros de consumo">Los eventos seleccionados cuentan en el promedio por evento.</Alert>
          : r.resumen.consumoTotal === 0 && <Alert tone="info" title="Consumo registrado igual a cero">Hay registros, pero el consumo total es cero.</Alert>}
      <Card title="Indicadores generales">
        <div className="estadisticas__indicadores">
          <Stat label="Eventos cerrados incluidos" value={numero(r.resumen.eventos)} />
          <Stat label="Consumo total" value={numero(r.resumen.consumoTotal)} unit="botellas" />
          <Stat label="Promedio por evento" value={numero(r.resumen.promedioPorEvento)} unit="botellas" />
          <Stat label="Consumo por invitado registrado" value={numero(r.resumen.porInvitadoRegistrado)} unit="botellas" />
        </div>
        <p className="body-sm">El cálculo por invitado usa {numero(r.resumen.eventosConInvitadosValidos)} de {numero(r.resumen.eventos)} eventos,
          {' '}{numero(r.resumen.invitadosRegistrados)} invitados registrados y {numero(r.resumen.consumoConInvitadosValidos)} botellas de esos mismos eventos.</p>
      </Card>
      <Tabla titulo="Consumo por bebida" filas={r.porBebida.map((b) => ({ id: String(b.bebidaId), etiqueta: `${b.nombre} ${b.presentacion}${estado(b.activo)}`, medidas: b.medidas }))} />
      <Tabla titulo="Consumo por tipo de evento" filas={r.porTipoEvento.map((t) => ({ id: String(t.tipoEventoId), etiqueta: t.nombre + estado(t.activo), medidas: t.medidas }))} />
      <Tabla titulo="Detalle por tipo de evento y bebida" filas={r.detalle.map((d) => {
        const tipo = r.porTipoEvento.find((t) => t.tipoEventoId === d.tipoEventoId)!;
        const bebida = r.porBebida.find((b) => b.bebidaId === d.bebidaId)!;
        return { id: `${d.tipoEventoId}-${d.bebidaId}`, etiqueta: `${tipo.nombre} · ${bebida.nombre} ${bebida.presentacion}`, medidas: d.medidas };
      })} />
    </>}</Cargando>
  </div>;
}
