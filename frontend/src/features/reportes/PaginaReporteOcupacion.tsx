import { useCallback, useRef, useState } from 'react';
import { consultarReporteOcupacion, type FiltroReporteOcupacion } from '../../api/reportes';
import { useDatos } from '../../api/useDatos';
import { Alert, Button, Card, EmptyState, Input, Select, Table, type SelectOption } from '../../ds';
import { Encabezado } from '../layout/paginas';
import './reportes.css';

const hoy = new Date();
const isoHoy = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Argentina/Cordoba' }).format(hoy);
const filtrosIniciales: FiltroReporteOcupacion = {
  desde: `${isoHoy.slice(0, 4)}-01-01`, hasta: `${isoHoy.slice(0, 4)}-12-31`, salonId: '', tipoEventoId: '', vendedoraId: '', regimen: '',
};
const numero = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });

export function PaginaReporteOcupacion() {
  const [filtros, setFiltros] = useState(filtrosIniciales);
  const periodoAmbasTemporadas = useRef<{ desde: string; hasta: string } | null>(null);
  const cargar = useCallback(async () => ({ ...await consultarReporteOcupacion(filtros), consulta: filtros }), [filtros]);
  const datos = useDatos(cargar);
  const cambiar = (campo: keyof FiltroReporteOcupacion, valor: string) => {
    if (campo !== 'regimen') {
      setFiltros((actual) => ({ ...actual, [campo]: valor }));
      return;
    }
    if (!valor) {
      const periodo = periodoAmbasTemporadas.current;
      periodoAmbasTemporadas.current = null;
      setFiltros((actual) => ({ ...actual, regimen: '', ...(periodo ?? {}) }));
      return;
    }
    if (!filtros.regimen) periodoAmbasTemporadas.current = { desde: filtros.desde, hasta: filtros.hasta };
    const periodo = ajustarPeriodoTemporada(filtros.desde, filtros.hasta, valor);
    setFiltros((actual) => ({ ...actual, regimen: valor, ...periodo }));
  };
  // Los resultados y su encabezado de impresión pertenecen a la misma consulta.
  const filtrosDelResultado = datos.datos?.consulta ?? filtros;
  const resultadoVigente = !!datos.datos && JSON.stringify(datos.datos.consulta) === JSON.stringify(filtros);
  const salonElegido = datos.datos?.filtros.salones.find((s) => String(s.id) === filtrosDelResultado.salonId)?.nombre ?? 'Todos';
  const tipoElegido = datos.datos?.filtros.tiposEvento.find((t) => String(t.id) === filtrosDelResultado.tipoEventoId)?.nombre ?? 'Todos';
  const vendedoraElegida = datos.datos?.filtros.vendedoras.find((v) => String(v.id) === filtrosDelResultado.vendedoraId)?.nombre ?? 'Todas';
  const temporadaElegida = filtrosDelResultado.regimen === 'ALTA' ? 'Alta' : filtrosDelResultado.regimen === 'BAJA' ? 'Baja' : 'Ambas';
  const etiquetaPeriodo = filtrosDelResultado.regimen
    ? `Período de temporada ${temporadaElegida.toLowerCase()} ${fecha(datos.datos?.desde ?? filtros.desde)} al ${fecha(datos.datos?.hasta ?? filtros.hasta)} · dos turnos por día`
    : `Período ${fecha(datos.datos?.desde ?? filtros.desde)} al ${fecha(datos.datos?.hasta ?? filtros.hasta)} · dos turnos por día`;

  return (
    <section className="pantalla reporte-ocupacion">
      <Encabezado titulo="Reporte de ocupación" antetitulo="Gestión comercial" />
      <div className="reporte-ocupacion__impresion">
        <p><strong>Período:</strong> {fecha(filtrosDelResultado.desde)} al {fecha(filtrosDelResultado.hasta)}</p>
        <p><strong>Salón:</strong> {salonElegido} <strong>· Tipo:</strong> {tipoElegido} <strong>· Vendedora:</strong> {vendedoraElegida} <strong>· Temporada:</strong> {temporadaElegida}</p>
        <p className="reporte-ocupacion__impresion-nota">Incluye eventos señados, contratados, confirmados, en curso, realizados y cerrados. Excluye pre-reservas, cancelados y liberados.</p>
        <p className="reporte-ocupacion__impresion-fecha">Generado el {new Intl.DateTimeFormat('es-AR', { timeZone: 'America/Argentina/Cordoba', dateStyle: 'long', timeStyle: 'short' }).format(new Date())}</p>
      </div>
      <form className="reporte-ocupacion__filtros" onSubmit={(e) => e.preventDefault()} aria-label="Filtros del reporte">
        <div className="reporte-ocupacion__filtros-campos">
          <Input label="Desde" type="date" value={filtros.desde} onChange={(e) => cambiar('desde', e.target.value)} />
          <Input label="Hasta" type="date" value={filtros.hasta} onChange={(e) => cambiar('hasta', e.target.value)} />
          <Select label="Salón" value={filtros.salonId} placeholder="Todos los salones" options={opciones(datos.datos?.filtros.salones)} onChange={(e) => cambiar('salonId', e.target.value)} />
          <Select label="Tipo de evento" value={filtros.tipoEventoId} placeholder="Todos los tipos" options={opciones(datos.datos?.filtros.tiposEvento)} onChange={(e) => cambiar('tipoEventoId', e.target.value)} />
          <Select label="Vendedora" value={filtros.vendedoraId} placeholder="Todas las vendedoras" options={opciones(datos.datos?.filtros.vendedoras)} onChange={(e) => cambiar('vendedoraId', e.target.value)} />
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
