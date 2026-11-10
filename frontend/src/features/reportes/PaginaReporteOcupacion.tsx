import { useCallback, useRef, useState, type MutableRefObject } from 'react';
import { consultarReporteOcupacion, type FiltroReporteOcupacion } from '../../api/reportes';
import { useDatos } from '../../api/useDatos';
import { Alert, Button, Card, Dialog, EmptyState, Input, OpcionesMultiples, Segmentado, Select, Table, type SalonId, type SelectOption } from '../../ds';
import { FiltroMultiple } from '../comun/FiltroMultiple';
import { MarcaSalon } from '../comun/MarcaSalon';
import { Encabezado } from '../layout/paginas';
import './reportes.css';

const hoy = new Date();
const isoHoy = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Argentina/Cordoba' }).format(hoy);
const filtrosIniciales: FiltroReporteOcupacion = {
  desde: `${isoHoy.slice(0, 4)}-01-01`, hasta: `${isoHoy.slice(0, 4)}-12-31`, salonIds: [], tipoEventoIds: [], vendedoraIds: [], regimen: '',
};
const numero = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });

export function PaginaReporteOcupacion() {
  const [filtros, setFiltros] = useState(filtrosIniciales);
  const periodoAmbasTemporadas = useRef<{ desde: string; hasta: string } | null>(null);
  const cargar = useCallback(async () => ({ ...await consultarReporteOcupacion(filtros), consulta: filtros }), [filtros]);
  const datos = useDatos(cargar);
  const cambiar = (campo: keyof FiltroReporteOcupacion, valor: string | string[]) => setFiltros(conCambio(filtros, campo, valor, periodoAmbasTemporadas));
  // En el teléfono los filtros se eligen en un panel y se aplican juntos con «Ver resultados».
  const [panelAbierto, setPanelAbierto] = useState(false);
  const [borrador, setBorrador] = useState(filtros);
  const abrirPanel = () => { setBorrador(filtros); setPanelAbierto(true); };
  const cambiarBorrador = (campo: keyof FiltroReporteOcupacion, valor: string | string[]) => setBorrador((b) => conCambio(b, campo, valor, periodoAmbasTemporadas));
  const elegirPeriodo = (clave: string) => {
    const periodo = PERIODOS_RAPIDOS.find((p) => p.clave === clave)?.rango();
    if (!periodo) return;
    periodoAmbasTemporadas.current = null;
    setBorrador((b) => ({ ...b, ...periodo, regimen: '' }));
  };
  const verResultados = () => { setFiltros(borrador); setPanelAbierto(false); };
  const filtrosActivos = [filtros.salonIds.length, filtros.tipoEventoIds.length, filtros.vendedoraIds.length, filtros.regimen].filter(Boolean).length
    // Elegir temporada recorta el período: cuenta como un solo filtro.
    + (!filtros.regimen && (filtros.desde !== filtrosIniciales.desde || filtros.hasta !== filtrosIniciales.hasta) ? 1 : 0);
  const nombreDe = (lista: { id: number; nombre: string }[] | undefined, ids: string[]) =>
    lista?.filter((x) => ids.includes(String(x.id))).map((x) => x.nombre).join(', ') || undefined;
  const resumenFiltros = [
    `${fecha(filtros.desde)} al ${fecha(filtros.hasta)}`,
    nombreDe(datos.datos?.filtros.salones, filtros.salonIds),
    nombreDe(datos.datos?.filtros.tiposEvento, filtros.tipoEventoIds),
    nombreDe(datos.datos?.filtros.vendedoras, filtros.vendedoraIds),
    filtros.regimen === 'ALTA' ? 'Temporada alta' : filtros.regimen === 'BAJA' ? 'Temporada baja' : undefined,
  ].filter(Boolean).join(' · ');

  // Los resultados y su encabezado de impresión pertenecen a la misma consulta.
  const filtrosDelResultado = datos.datos?.consulta ?? filtros;
  const resultadoVigente = !!datos.datos && JSON.stringify(datos.datos.consulta) === JSON.stringify(filtros);
  const salonElegido = nombreDe(datos.datos?.filtros.salones, filtrosDelResultado.salonIds) ?? 'Todos';
  // La marca del salón va en la impresión solo si se eligió uno.
  const codigoSalonElegido = filtrosDelResultado.salonIds.length === 1
    ? datos.datos?.porSalon.find((s) => String(s.salonId) === filtrosDelResultado.salonIds[0])?.codigo as SalonId | undefined : undefined;
  const tipoElegido = nombreDe(datos.datos?.filtros.tiposEvento, filtrosDelResultado.tipoEventoIds) ?? 'Todos';
  const vendedoraElegida = nombreDe(datos.datos?.filtros.vendedoras, filtrosDelResultado.vendedoraIds) ?? 'Todas';
  const temporadaElegida = filtrosDelResultado.regimen === 'ALTA' ? 'Alta' : filtrosDelResultado.regimen === 'BAJA' ? 'Baja' : 'Ambas';
  const etiquetaPeriodo = filtrosDelResultado.regimen
    ? `Período de temporada ${temporadaElegida.toLowerCase()} ${fecha(datos.datos?.desde ?? filtros.desde)} al ${fecha(datos.datos?.hasta ?? filtros.hasta)} · dos turnos por día`
    : `Período ${fecha(datos.datos?.desde ?? filtros.desde)} al ${fecha(datos.datos?.hasta ?? filtros.hasta)} · dos turnos por día`;

  return (
    <section className="pantalla reporte-ocupacion">
      <Encabezado titulo="Reporte de ocupación" antetitulo="Gestión comercial" />
      <div className="reporte-ocupacion__impresion">
        {codigoSalonElegido && <MarcaSalon salon={codigoSalonElegido} className="reporte-ocupacion__marca" />}
        <p><strong>Período:</strong> {fecha(filtrosDelResultado.desde)} al {fecha(filtrosDelResultado.hasta)}</p>
        <p><strong>Salón:</strong> {salonElegido} <strong>· Tipo:</strong> {tipoElegido} <strong>· Vendedora:</strong> {vendedoraElegida} <strong>· Temporada:</strong> {temporadaElegida}</p>
        <p className="reporte-ocupacion__impresion-nota">Incluye eventos señados, contratados, confirmados, en curso, realizados y cerrados. Excluye pre-reservas, cancelados y liberados.</p>
        <p className="reporte-ocupacion__impresion-fecha">Generado el {new Intl.DateTimeFormat('es-AR', { timeZone: 'America/Argentina/Cordoba', dateStyle: 'long', timeStyle: 'short' }).format(new Date())}</p>
      </div>
      <div className="reporte-ocupacion__filtrar">
        <div className="reporte-ocupacion__filtrar-botones">
          <Button variant="outline" icon="sliders-horizontal" onClick={abrirPanel}>
            {filtrosActivos > 0 ? `Filtrar · ${filtrosActivos}` : 'Filtrar'}
          </Button>
          <Button variant="solid" icon="printer" disabled={!resultadoVigente} onClick={() => window.print()}>Imprimir</Button>
        </div>
        <p className="body-sm v-muted reporte-ocupacion__filtrar-resumen">{resumenFiltros}</p>
      </div>
      <Dialog
        open={panelAbierto}
        title="Filtrar reporte"
        onClose={() => setPanelAbierto(false)}
        className="reporte-ocupacion__panel"
        actions={<>
          <Button variant="text" onClick={() => { periodoAmbasTemporadas.current = null; setBorrador(filtrosIniciales); }}>Limpiar filtros</Button>
          <Button variant="solid" onClick={verResultados}>Ver resultados</Button>
        </>}
      >
        <div className="reporte-ocupacion__panel-campos">
          <Segmentado label="Período" value={PERIODOS_RAPIDOS.find((p) => { const r = p.rango(); return r.desde === borrador.desde && r.hasta === borrador.hasta; })?.clave ?? ''}
            options={PERIODOS_RAPIDOS.map((p) => ({ value: p.clave, label: p.nombre }))} onChange={elegirPeriodo} />
          <div className="reporte-ocupacion__panel-fechas">
            <Input label="Desde" type="date" value={borrador.desde} onChange={(e) => cambiarBorrador('desde', e.target.value)} />
            <Input label="Hasta" type="date" value={borrador.hasta} onChange={(e) => cambiarBorrador('hasta', e.target.value)} />
          </div>
          <OpcionesMultiples label="Salón" hint="Sin elegir, todos los salones." values={borrador.salonIds} options={opciones(datos.datos?.filtros.salones)}
            onChange={(v) => cambiarBorrador('salonIds', v)} />
          <Segmentado label="Temporada" value={borrador.regimen} options={[
            { value: '', label: 'Ambas' }, { value: 'ALTA', label: 'Alta · oct a dic' }, { value: 'BAJA', label: 'Baja · ene a sep' },
          ]} onChange={(v) => cambiarBorrador('regimen', v)} />
          <OpcionesMultiples label="Tipo de evento" hint="Sin elegir, todos los tipos." values={borrador.tipoEventoIds} options={opciones(datos.datos?.filtros.tiposEvento)}
            onChange={(v) => cambiarBorrador('tipoEventoIds', v)} />
          <OpcionesMultiples label="Vendedora" hint="Sin elegir, todas las vendedoras." values={borrador.vendedoraIds} options={opciones(datos.datos?.filtros.vendedoras)}
            onChange={(v) => cambiarBorrador('vendedoraIds', v)} />
        </div>
      </Dialog>
      <form className="reporte-ocupacion__filtros" onSubmit={(e) => e.preventDefault()} aria-label="Filtros del reporte">
        <div className="reporte-ocupacion__filtros-campos">
          <Input label="Desde" type="date" value={filtros.desde} onChange={(e) => cambiar('desde', e.target.value)} />
          <Input label="Hasta" type="date" value={filtros.hasta} onChange={(e) => cambiar('hasta', e.target.value)} />
          <CampoMultiple titulo="Salón" todos="Todos los salones" lista={datos.datos?.filtros.salones} valores={filtros.salonIds} alCambiar={(v) => cambiar('salonIds', v)} />
          <CampoMultiple titulo="Tipo de evento" todos="Todos los tipos" lista={datos.datos?.filtros.tiposEvento} valores={filtros.tipoEventoIds} alCambiar={(v) => cambiar('tipoEventoIds', v)} />
          <CampoMultiple titulo="Vendedora" todos="Todas las vendedoras" lista={datos.datos?.filtros.vendedoras} valores={filtros.vendedoraIds} alCambiar={(v) => cambiar('vendedoraIds', v)} />
          <Select label="Régimen de temporada" value={filtros.regimen} options={[
            { value: '', label: 'Ambas temporadas' },
            { value: 'ALTA', label: 'Alta · octubre a diciembre' }, { value: 'BAJA', label: 'Baja · enero a septiembre' },
          ]} onChange={(e) => cambiar('regimen', e.target.value)} />
        </div>
        <div className="reporte-ocupacion__acciones">
          <Button type="button" variant="solid" icon="printer" disabled={!resultadoVigente} onClick={() => window.print()}>Imprimir</Button>
        </div>
      </form>

      {datos.error && <Alert tone="danger" title="No pudimos generar el reporte" action={<Button variant="outline" size="sm" onClick={datos.recargar}>Reintentar</Button>}>{datos.error.message}</Alert>}
      {datos.cargando && !datos.datos && <Card><p className="body-sm">Preparando el reporte…</p></Card>}
      {datos.datos && (
        <>
          {datos.cargando && <p className="body-sm v-muted" role="status">Actualizando resultados…</p>}
          <div className="reporte-ocupacion__resumen" aria-label="Cifras resumen">
            <Card className="reporte-ocupacion__stat"><p className="label v-muted">Unidades ocupadas</p><p className="stat">{numero.format(datos.datos.resumen.unidadesOcupadas)}</p><p className="body-sm v-muted">Salón × fecha × turno</p></Card>
            <Card className="reporte-ocupacion__stat"><p className="label v-muted">Ocupación promedio</p><p className="stat">{numero.format(datos.datos.resumen.ocupacionPromedio)}<span className="body-sm"> %</span></p><p className="body-sm v-muted">Sobre las unidades disponibles</p></Card>
            <Card className="reporte-ocupacion__stat"><p className="label v-muted">Eventos realizados</p><p className="stat">{numero.format(datos.datos.resumen.eventosRealizados ?? 0)}</p><p className="body-sm v-muted">Realizados o cerrados</p></Card>
          </div>

          <Card title="Ocupación por salón" subtitle={etiquetaPeriodo}>
            {datos.datos.porSalon.length ? <div className="reporte-ocupacion__grafico" role="img" aria-label={datos.datos.porSalon.map((s) => `${s.salon}: ${numero.format(s.ocupacion)} por ciento`).join('; ')}>
              {datos.datos.porSalon.map((salon) => <div className="reporte-ocupacion__barra-fila" key={salon.salonId}>
                <span className="body-sm">{salon.salon}</span><span className="reporte-ocupacion__riel"><span className={`reporte-ocupacion__relleno reporte-ocupacion__relleno--${salon.codigo}`} style={{ width: `${Math.min(salon.ocupacion, 100)}%` }} /></span><span className="numeral">{numero.format(salon.ocupacion)}%</span>
              </div>)}
            </div> : <EmptyState icon="chart-column" title="No hay salones para mostrar">Probá con otro período o quitá filtros.</EmptyState>}
          </Card>

          <Card title="Detalle por salón" className="reporte-ocupacion__tabla">
            <Table caption="Ocupación por salón" rows={datos.datos.porSalon.map((s) => ({ ...s, id: s.salonId }))} empty="Sin unidades para este filtro" columns={[
              { key: 'salon', header: 'Salón' },
              { key: 'unidades', header: 'Unidades ocupadas / disponibles', numeric: true, render: (s) => `${s.unidadesOcupadas} / ${s.unidadesDisponibles}` },
              { key: 'ocupacion', header: 'Ocupación', numeric: true, render: (s) => `${numero.format(s.ocupacion)}%` },
            ]} />
          </Card>

          <Card title="Eventos por vendedora y tipo" subtitle="Eventos señados, contratados, confirmados, en curso, realizados o cerrados">
            <Table caption="Eventos por vendedora y tipo" rows={datos.datos.porVendedoraYTipo.map((r) => ({ ...r, id: `${r.vendedoraId}-${r.tipoEventoId}` }))} empty="No hay eventos para este filtro" columns={[
              { key: 'vendedora', header: 'Vendedora' }, { key: 'tipo', header: 'Tipo de evento', render: (r) => r.tipoEvento },
              { key: 'eventos', header: 'Eventos', numeric: true, render: (r) => r.eventosIncluidos },
            ]} />
          </Card>
        </>
      )}
    </section>
  );
}

/**
 * Aplica un cambio de filtro. Al elegir una temporada, el período se recorta a sus meses y se recuerda el de antes,
 * para devolverlo al volver a «Ambas temporadas».
 */
function conCambio(actual: FiltroReporteOcupacion, campo: keyof FiltroReporteOcupacion, valor: string | string[],
  periodoAmbasTemporadas: MutableRefObject<{ desde: string; hasta: string } | null>): FiltroReporteOcupacion {
  if (campo !== 'regimen') return { ...actual, [campo]: valor };
  if (typeof valor !== 'string') return actual;
  if (!valor) {
    const periodo = periodoAmbasTemporadas.current;
    periodoAmbasTemporadas.current = null;
    return { ...actual, regimen: '', ...(periodo ?? {}) };
  }
  if (!actual.regimen) periodoAmbasTemporadas.current = { desde: actual.desde, hasta: actual.hasta };
  return { ...actual, regimen: valor, ...ajustarPeriodoTemporada(actual.desde, actual.hasta, valor) };
}

const ultimoDia = (anio: number, mes: number) => new Date(Date.UTC(anio, mes, 0)).getUTCDate();
const iso = (anio: number, mes: number, dia: number) => `${anio}-${String(mes).padStart(2, '0')}-${String(dia).padStart(2, '0')}`;
const ANIO = Number(isoHoy.slice(0, 4));
const MES = Number(isoHoy.slice(5, 7));

/** Atajos de período del panel del teléfono. */
const PERIODOS_RAPIDOS = [
  { clave: 'mes', nombre: 'Este mes', rango: () => ({ desde: iso(ANIO, MES, 1), hasta: iso(ANIO, MES, ultimoDia(ANIO, MES)) }) },
  { clave: 'trimestre', nombre: 'Próximos 3 meses', rango: () => {
    const fin = new Date(Date.UTC(ANIO, MES + 2, 0));
    return { desde: iso(ANIO, MES, 1), hasta: iso(fin.getUTCFullYear(), fin.getUTCMonth() + 1, fin.getUTCDate()) };
  } },
  { clave: 'anio', nombre: 'Este año', rango: () => ({ desde: iso(ANIO, 1, 1), hasta: iso(ANIO, 12, 31) }) },
  { clave: 'siguiente', nombre: 'Año que viene', rango: () => ({ desde: iso(ANIO + 1, 1, 1), hasta: iso(ANIO + 1, 12, 31) }) },
];

/** Filtro de varios valores en la computadora: el título arriba, como los demás campos, y en el botón lo elegido. */
function CampoMultiple({ titulo, todos, lista, valores, alCambiar }: {
  titulo: string; todos: string; lista: { id: number; nombre: string }[] | undefined; valores: string[]; alCambiar: (valores: string[]) => void;
}) {
  const elegidos = (lista ?? []).filter((x) => valores.includes(String(x.id))).map((x) => x.nombre);
  return (
    <div className="v-field reporte-ocupacion__multiple">
      <span className="label v-field__label">{titulo}</span>
      <FiltroMultiple label={elegidos.length ? elegidos.join(', ') : todos} opciones={opciones(lista)} valores={valores} alCambiar={alCambiar} />
    </div>
  );
}

function opciones(lista: { id: number; nombre: string }[] | undefined): SelectOption[] {
  return (lista ?? []).map((item) => ({ value: String(item.id), label: item.nombre }));
}

function fecha(iso: string): string {
  if (!iso || Number.isNaN(Date.parse(iso + 'T12:00:00Z'))) return '—';
  return new Intl.DateTimeFormat('es-AR', { timeZone: 'UTC', day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(`${iso}T12:00:00Z`));
}

function ajustarPeriodoTemporada(desde: string, hasta: string, regimen: string): { desde: string; hasta: string } {
  if (!desde || !hasta || desde > hasta) return { desde, hasta };
  const temporada = regimen === 'ALTA' ? { inicio: 10, fin: 12 } : { inicio: 1, fin: 9 };
  const anioDesde = Number(desde.slice(0, 4));
  const anioHasta = Number(hasta.slice(0, 4));
  const periodos = Array.from({ length: anioHasta - anioDesde + 1 }, (_, indice) => {
    const anio = anioDesde + indice;
    return {
      desde: `${anio}-${String(temporada.inicio).padStart(2, '0')}-01`,
      hasta: `${anio}-${String(temporada.fin).padStart(2, '0')}-${new Date(Date.UTC(anio, temporada.fin, 0)).getUTCDate()}`,
    };
  }).map((periodo) => ({ desde: periodo.desde < desde ? desde : periodo.desde, hasta: periodo.hasta > hasta ? hasta : periodo.hasta }))
    .filter((periodo) => periodo.desde <= periodo.hasta);

  if (periodos.length) return { desde: periodos[0].desde, hasta: periodos[periodos.length - 1].hasta };

  const anio = anioDesde;
  return {
    desde: `${anio}-${String(temporada.inicio).padStart(2, '0')}-01`,
    hasta: `${anio}-${String(temporada.fin).padStart(2, '0')}-${new Date(Date.UTC(anio, temporada.fin, 0)).getUTCDate()}`,
  };
}
