import { useState } from 'react';
import { useNavigate } from 'react-router';
import type { EstadoEvento } from '../../api/agenda';
import { fichas } from '../../api/eventos';
import type { EventoResumen } from '../../api/eventos';
import { useDatos } from '../../api/useDatos';
import { Badge, Button, Card, EmptyState, ESTADO_POR_CODIGO, ESTADOS, EventCard, Input, Tabs } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { FiltroMultiple, type OpcionFiltro } from '../comun/FiltroMultiple';
import { fechaCorta } from '../comun/formato';
import { itemsPara } from '../layout/menu';
import { Encabezado } from '../layout/paginas';
import { useSesion } from '../sesion/contexto';
import './eventos.css';

/** Valor interno del filtro para eventos que todavía no tienen planner asignada. */
const SIN_PLANNER = '__sin_planner__';

function opcionesUnicas(opciones: OpcionFiltro[]): OpcionFiltro[] {
  return [...new Map(opciones.map((opcion) => [opcion.value, opcion])).values()]
    .sort((a, b) => a.label.localeCompare(b.label, 'es-AR'));
}

function normalizarBusqueda(texto: string): string {
  return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es-AR').trim();
}

function textoDeEvento(evento: EventoResumen): string {
  return normalizarBusqueda([
    evento.codigo, evento.nombre, evento.tipo, evento.salon.nombre, evento.salon.codigo,
    evento.fecha, evento.turno.nombre, evento.cliente, evento.vendedora.nombre, evento.planner?.nombre,
  ].filter(Boolean).join(' '));
}

/** Próximos eventos. Para la vendedora son los suyos; los demás perfiles conservan el alcance de la API. */
export function PaginaEventos() {
  const datos = useDatos(fichas.proximos);
  const navegar = useNavigate();
  const { usuario } = useSesion();
  const titulo = usuario ? itemsPara(usuario.roles).find((it) => it.id === 'eventos')?.label ?? 'Eventos' : 'Eventos';
  const esPlanner = usuario?.roles.includes('PLANNER') ?? false;
  const [filtro, setFiltro] = useState<'todos' | 'asignados'>('todos');
  const [buscar, setBuscar] = useState('');
  const [estados, setEstados] = useState<EstadoEvento[]>([]);
  const [salones, setSalones] = useState<string[]>([]);
  const [turnos, setTurnos] = useState<string[]>([]);
  const [tipos, setTipos] = useState<string[]>([]);
  const [vendedoras, setVendedoras] = useState<string[]>([]);
  const [planners, setPlanners] = useState<string[]>([]);
  const [desde, setDesde] = useState('');
  const [hasta, setHasta] = useState('');
  const [filtrosAbiertos, setFiltrosAbiertos] = useState(false);
  const conFiltros = !!buscar.trim() || estados.length > 0 || salones.length > 0 || turnos.length > 0 || tipos.length > 0
    || vendedoras.length > 0 || planners.length > 0 || !!desde || !!hasta;

  function limpiarFiltros() {
    setBuscar('');
    setEstados([]);
    setSalones([]);
    setTurnos([]);
    setTipos([]);
    setVendedoras([]);
    setPlanners([]);
    setDesde('');
    setHasta('');
  }

  return (
    <section className="pantalla">
      <Encabezado titulo={titulo} antetitulo="Agenda" />
      {esPlanner && (
        <Tabs
          variant="segmented"
          label="Qué eventos ver"
          items={[{ id: 'todos', label: 'Todos' }, { id: 'asignados', label: 'Asignados a mí' }]}
          value={filtro}
          onChange={(f) => setFiltro(f as 'todos' | 'asignados')}
        />
      )}
      <Cargando datos={datos}>
        {(todos) => {
          const estadoOpciones = opcionesUnicas(todos.map((e) => ({
            value: e.estado,
            label: ESTADOS[ESTADO_POR_CODIGO[e.estado]].label,
          })));
          const salonOpciones = opcionesUnicas(todos.map((e) => ({ value: e.salon.codigo, label: e.salon.nombre })));
          const turnoOpciones = opcionesUnicas(todos.map((e) => ({ value: e.turno.codigo, label: e.turno.nombre })));
          const tipoOpciones = opcionesUnicas(todos.map((e) => ({ value: e.tipo, label: e.tipo })));
          const vendedoraOpciones = opcionesUnicas(todos.map((e) => ({ value: String(e.vendedora.id), label: e.vendedora.nombre })));
          const plannerOpciones = opcionesUnicas(todos.map((e) => ({
            value: e.planner ? String(e.planner.id) : SIN_PLANNER,
            label: e.planner?.nombre ?? 'Sin planner',
          })));
          const rangoInvalido = !!desde && !!hasta && desde > hasta;
          const texto = normalizarBusqueda(buscar);
          const palabras = texto ? texto.split(/\s+/) : [];
          const lista = todos.filter((e) => {
            if (filtro === 'asignados' && e.planner?.id !== usuario?.id) return false;
            if (estados.length > 0 && !estados.includes(e.estado)) return false;
            if (salones.length > 0 && !salones.includes(e.salon.codigo)) return false;
            if (turnos.length > 0 && !turnos.includes(e.turno.codigo)) return false;
            if (tipos.length > 0 && !tipos.includes(e.tipo)) return false;
            if (vendedoras.length > 0 && !vendedoras.includes(String(e.vendedora.id))) return false;
            if (planners.length > 0 && !planners.includes(e.planner ? String(e.planner.id) : SIN_PLANNER)) return false;
            if (desde && e.fecha < desde) return false;
            if (hasta && e.fecha > hasta) return false;
            const textoEvento = textoDeEvento(e);
            return !rangoInvalido && palabras.every((palabra) => textoEvento.includes(palabra));
          });
          const sinAsignados = filtro === 'asignados' && todos.every((e) => e.planner?.id !== usuario?.id);

          return (
            <>
              <Card
                title="Filtros de búsqueda"
                actions={(
                  <div className="eventos__filtros-acciones">
                    <Badge tone="brand"><span aria-live="polite">{lista.length} {lista.length === 1 ? 'resultado' : 'resultados'}</span></Badge>
                    <Button
                      variant="outline"
                      iconEnd={filtrosAbiertos ? 'chevron-up' : 'chevron-down'}
                      ariaExpanded={filtrosAbiertos}
                      ariaControls="eventos-filtros-contenido"
                      onClick={() => setFiltrosAbiertos((abiertos) => !abiertos)}
                    >
                      {filtrosAbiertos ? 'Ocultar filtros' : 'Mostrar filtros'}
                    </Button>
                  </div>
                )}
                className={`eventos__filtros${filtrosAbiertos ? '' : ' eventos__filtros--cerrados'}`}
              >
              {filtrosAbiertos && <div className="eventos__filtros-contenido" id="eventos-filtros-contenido">
                <Input
                  label="Buscar eventos"
                  icon="search"
                  value={buscar}
                  onChange={(e) => setBuscar(e.target.value)}
                  placeholder="Nombre, código, cliente, salón…"
                />
                <div className="eventos__filtros-grupos">
                  <FiltroMultiple label="Estados" opciones={estadoOpciones} valores={estados} alCambiar={(v) => setEstados(v as EstadoEvento[])} />
                  <FiltroMultiple label="Salones" opciones={salonOpciones} valores={salones} alCambiar={setSalones} />
                  <FiltroMultiple label="Turnos" opciones={turnoOpciones} valores={turnos} alCambiar={setTurnos} />
                  <FiltroMultiple label="Tipos de evento" opciones={tipoOpciones} valores={tipos} alCambiar={setTipos} />
                  <FiltroMultiple label="Vendedoras" opciones={vendedoraOpciones} valores={vendedoras} alCambiar={setVendedoras} />
                  <FiltroMultiple label="Planners" opciones={plannerOpciones} valores={planners} alCambiar={setPlanners} />
                </div>
                <div className="eventos__filtros-fechas">
                  <Input label="Desde" type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
                  <Input label="Hasta" type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
                </div>
                <div className="eventos__filtros-pie">
                  {conFiltros && <Button variant="text" onClick={limpiarFiltros}>Limpiar filtros</Button>}
                  {!conFiltros && <p className="caption v-muted">Los filtros activos se muestran en cada selector.</p>}
                </div>
                {rangoInvalido && <p className="body-sm eventos__error-fechas" role="alert">La fecha Desde no puede ser posterior a Hasta.</p>}
              </div>
              }
              </Card>
              {lista.length === 0 ? (
                <Card flush>
                  <EmptyState
                    icon="calendar"
                    title={conFiltros ? 'No hay eventos que coincidan' : sinAsignados ? 'No tenés eventos asignados' : 'No hay eventos próximos'}
                    action={conFiltros
                      ? <Button variant="outline" onClick={limpiarFiltros}>Limpiar filtros</Button>
                      : !sinAsignados ? <Button variant="outline" icon="calendar" onClick={() => navegar('/agenda')}>Ir a la agenda</Button> : undefined}
                  >
                    {conFiltros
                      ? 'Probá quitar algún filtro o cambiar el texto de búsqueda.'
                      : sinAsignados ? 'Cuando Coordinación te asigne un evento, aparece acá.' : 'Los eventos aparecen acá desde que se pre-reserva la fecha.'}
                  </EmptyState>
                </Card>
              ) : (
                <ul className="eventos__lista">
                  {lista.map((e) => (
                    <li key={e.id}>
                      <EventCard
                        salon={e.salon.codigo}
                        status={ESTADO_POR_CODIGO[e.estado]}
                        tipo={e.tipo}
                        title={e.nombre}
                        fecha={fechaCorta(e.fecha, true)}
                        turno={e.turno.nombre}
                        invitados={e.cantidadInvitados ?? undefined}
                        vendedora={e.vendedora.nombre}
                        planner={e.planner?.nombre ?? null}
                        onOpen={() => navegar(`/eventos/${e.id}`)}
                      />
                    </li>
                  ))}
                </ul>
              )}
            </>
          );
        }}
      </Cargando>
    </section>
  );
}
