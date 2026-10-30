import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { agenda, estadoDs, fechaLarga, hoyEnCordoba, sumarMeses, type Agenda } from '../../api/agenda';
import type { PreReservaRegistrada } from '../../api/eventos';
import { useDatos } from '../../api/useDatos';
import {
  AgendaGrid, Alert, Button, Card, ESTADOS, ESTADOS_DEL_EVENTO, SALONES, StatusChip, Tabs,
  type AgendaEvent, type EventStatus, type SalonId, type TurnoId,
} from '../../ds';
import { Cargando } from '../comun/Cargando';
import { FiltroMultiple } from '../comun/FiltroMultiple';
import { Encabezado } from '../layout/paginas';
import { useSesion } from '../sesion/contexto';
import { disponibilidad } from './disponibilidad';
import { MacheteAgenda } from './MacheteAgenda';
import { NombreTurno } from './NombreTurno';
import { DialogoPreReserva, type UnidadElegida } from './DialogoPreReserva';
import './agenda.css';

/** Estados que se pueden filtrar: disponible, los activos del evento y bloqueado. */
const ESTADOS_FILTRABLES: EventStatus[] = [
  'disponible',
  ...ESTADOS_DEL_EVENTO.filter((e) => e !== 'liberada' && e !== 'cancelado'),
  'bloqueado',
];
const TURNOS_FILTRABLES: TurnoId[] = ['mediodia', 'noche'];

const MES_VALIDO = /^\d{4}-(0[1-9]|1[0-2])$/;

/**
 * UI-07 · Consultar agenda: un mes con las seis unidades de cada día (3 salones × 2 turnos).
 * Mes, día elegido, salón que se mira en el detalle y filtros quedan en la dirección,
 * así se comparte o se recarga sin perder el lugar.
 */
export function PaginaAgenda() {
  const [busqueda, setBusqueda] = useSearchParams();
  const hoy = hoyEnCordoba();
  const pedido = busqueda.get('mes') ?? '';
  const mes = MES_VALIDO.test(pedido) ? pedido : hoy.slice(0, 7);
  const dia = busqueda.get('dia')?.startsWith(mes) ? busqueda.get('dia') : null;
  const salones = [...new Set(busqueda.getAll('salon').filter((codigo): codigo is SalonId => codigo in SALONES))];
  const estados = [...new Set(busqueda.getAll('estado').filter((valor): valor is EventStatus =>
    ESTADOS_FILTRABLES.includes(valor as EventStatus)))];
  const turnos = [...new Set(busqueda.getAll('turno').filter((valor): valor is TurnoId =>
    TURNOS_FILTRABLES.includes(valor as TurnoId)))];
  // Sin elegir, el detalle arranca en el salón del filtro.
  const ver = busqueda.get('ver') || salones[0] || null;

  const cargar = useCallback(() => agenda.mes(mes), [mes]);
  const datos = useDatos(cargar);
  const { usuario } = useSesion();
  const preReserva = !!usuario?.roles.some((r) => r === 'VENDEDORA' || r === 'DIRECCION' || r === 'COORDINACION');
  const [elegida, setElegida] = useState<UnidadElegida | null>(null);
  const [registrada, setRegistrada] = useState<PreReservaRegistrada | null>(null);
  const [filtrosAbiertos, setFiltrosAbiertos] = useState(false);
  const navegar = useNavigate();

  function cambiar(valores: Record<string, string | null>) {
    const siguiente = new URLSearchParams(busqueda);
    for (const [clave, valor] of Object.entries(valores)) {
      if (valor) siguiente.set(clave, valor);
      else siguiente.delete(clave);
    }
    setBusqueda(siguiente, { replace: true });
  }

  function cambiarLista(clave: string, valores: string[]) {
    const siguiente = new URLSearchParams(busqueda);
    siguiente.delete(clave);
    valores.forEach((valor) => siguiente.append(clave, valor));
    setBusqueda(siguiente, { replace: true });
  }

  const [anio, numeroMes] = mes.split('-').map(Number);

  return (
    <section className="pantalla">
      <Encabezado titulo="Agenda" antetitulo="Agenda" />
      {registrada && (
        <Alert
          tone="success"
          title="Pre-reserva registrada"
          action={<Button variant="outline" size="sm" onClick={() => navegar(`/eventos/${registrada.id}`)}>Ver ficha</Button>}
        >
          {registrada.codigo} · {registrada.nombre}. La fecha queda apartada hasta que se señe o se libere.
        </Alert>
      )}
      <Card
        title="Filtros de búsqueda"
        actions={(
          <div className="agenda__filtros-acciones">
            <Button variant="outline" icon="calendar" onClick={() => cambiar({ mes: hoy.slice(0, 7), dia: hoy })}>Ir a hoy</Button>
            <Button
              variant="outline"
              iconEnd={filtrosAbiertos ? 'chevron-up' : 'chevron-down'}
              ariaExpanded={filtrosAbiertos}
              ariaControls="agenda-filtros-contenido"
              onClick={() => setFiltrosAbiertos((abiertos) => !abiertos)}
            >
              {filtrosAbiertos ? 'Ocultar filtros' : 'Mostrar filtros'}
            </Button>
          </div>
        )}
        className={`agenda__filtros-panel${filtrosAbiertos ? '' : ' agenda__filtros-panel--cerrado'}`}
      >
      {filtrosAbiertos && <div className="agenda__filtros" id="agenda-filtros-contenido">
        <FiltroMultiple
          label="Salones"
          opciones={(datos.datos?.salones ?? []).map((s) => ({ value: s.codigo, label: s.nombre }))}
          valores={salones}
          alCambiar={(valores) => cambiarLista('salon', valores)}
        />
        <FiltroMultiple
          label="Estados"
          opciones={ESTADOS_FILTRABLES.map((e) => ({ value: e, label: ESTADOS[e].label }))}
          valores={estados}
          alCambiar={(valores) => cambiarLista('estado', valores)}
        />
        <FiltroMultiple
          label="Turnos"
          opciones={(datos.datos?.turnos ?? []).map((t) => ({ value: t.codigo, label: t.nombre }))}
          valores={turnos}
          alCambiar={(valores) => cambiarLista('turno', valores)}
        />
        {(salones.length > 0 || estados.length > 0 || turnos.length > 0) && (
          <Button variant="text" onClick={() => cambiar({ salon: null, estado: null, turno: null })}>Limpiar filtros</Button>
        )}
        <p className="caption v-muted agenda__filtros-ayuda">
          Las unidades que coinciden se resaltan en el calendario.
        </p>
      </div>
      }
      </Card>
      <Cargando datos={datos}>
        {(ag) => (
          <div className="agenda">
            <Card className="agenda__mes">
              <AgendaGrid
                year={anio}
                month={numeroMes - 1}
                events={eventosDelMes(ag)}
                today={hoy}
                selected={dia ?? undefined}
                onSelectDay={(iso) => cambiar({ dia: iso })}
                onPrev={() => cambiar({ mes: sumarMeses(mes, -1), dia: null })}
                onNext={() => cambiar({ mes: sumarMeses(mes, 1), dia: null })}
                salones={ag.salones}
                resaltar={salones.length > 0 || estados.length > 0 || turnos.length > 0
                  ? (u) => (salones.length === 0 || salones.includes(u.salon))
                    && (estados.length === 0 || estados.includes(u.status))
                    && (turnos.length === 0 || turnos.includes(u.turno))
                  : undefined}
              />
            </Card>
            <div className="agenda__lateral">
              <DetalleDelDia
                agenda={ag}
                fecha={dia}
                hoy={hoy}
                ver={ver}
                alVer={(codigo) => cambiar({ ver: codigo })}
                alPreReservar={preReserva ? (u) => { setRegistrada(null); setElegida(u); } : undefined}
              />
              <MacheteAgenda />
            </div>
            {elegida && (
              <DialogoPreReserva
                agenda={ag}
                unidad={elegida}
                alCerrar={() => setElegida(null)}
                alChocar={() => { setElegida(null); datos.recargar(); }}
                alRegistrar={(r) => { setElegida(null); setRegistrada(r); datos.recargar(); }}
              />
            )}
          </div>
        )}
      </Cargando>
    </section>
  );
}

function eventosDelMes(ag: Agenda): AgendaEvent[] {
  const salones = new Map(ag.salones.map((s) => [s.id, s.codigo]));
  const turnos = new Map(ag.turnos.map((t) => [t.id, t.codigo]));
  return ag.unidades.map((u) => ({
    date: u.fecha,
    salon: salones.get(u.salonId) as SalonId,
    turno: turnos.get(u.turnoId) as 'mediodia' | 'noche',
    status: estadoDs(u.estado) as AgendaEvent['status'],
  }));
}

/**
 * El día elegido, un salón a la vez: un botón por salón con cuántos turnos le quedan libres y, debajo,
 * los dos turnos del salón elegido con quién los tiene o por qué están bloqueados.
 */
function DetalleDelDia({ agenda: ag, fecha, hoy, ver, alVer, alPreReservar }: {
  agenda: Agenda;
  fecha: string | null;
  hoy: string;
  /** Código del salón que se mira; sin él, el primero. */
  ver: string | null;
  alVer: (codigo: SalonId) => void;
  /** Sin él, el perfil no pre-reserva y no se ofrece el botón. */
  alPreReservar?: (unidad: UnidadElegida) => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const navegar = useNavigate();

  useEffect(() => {
    if (fecha) ref.current?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' });
  }, [fecha]);

  if (!fecha) {
    return (
      <Card className="agenda__dia agenda__dia--vacio">
        <p className="body-sm v-muted">Tocá un día para ver qué turnos le quedan libres a cada salón y quién tiene los demás.</p>
      </Card>
    );
  }

  const salon = ag.salones.find((s) => s.codigo === ver) ?? ag.salones[0];
  const ocupadaEn = (salonId: number, turnoId: number) =>
    ag.unidades.find((u) => u.fecha === fecha && u.salonId === salonId && u.turnoId === turnoId);

  return (
    <div ref={ref} className="agenda__dia">
      <Card title={fechaLarga(fecha)} eyebrow="Día elegido">
        <div className="agenda__panel">
          <Tabs
            variant="bloques"
            label="Salón"
            value={salon.codigo}
            onChange={(codigo) => alVer(codigo as SalonId)}
            items={ag.salones.map((s) => ({
              id: s.codigo,
              label: s.nombre,
              salon: s.codigo,
              hint: disponibilidad(s.activo, ag.turnos.filter((t) => !ocupadaEn(s.id, t.id)).length, fecha < hoy),
            }))}
          />
          <div role="tabpanel" aria-label={salon.nombre} className="agenda__turnos">
            {ag.turnos.map((turno) => {
              const ocupada = ocupadaEn(salon.id, turno.id);
              const estado: EventStatus = ocupada ? estadoDs(ocupada.estado) : 'disponible';
              return (
                <div key={turno.id} className="agenda__caja">
                  <div className="agenda__unidad-cabeza">
                    <NombreTurno turno={turno} />
                    <StatusChip status={estado} size="sm" />
                  </div>
                  <Descripcion ocupada={ocupada} salonActivo={salon.activo} pasada={fecha < hoy} alAbrir={(id) => navegar(`/eventos/${id}`)} />
                  {!ocupada && salon.activo && fecha >= hoy && alPreReservar && (
                    <div className="agenda__accion">
                      <Button
                        variant="outline"
                        size="sm"
                        icon="plus"
                        onClick={() => alPreReservar({ fecha, salonId: salon.id, turnoId: turno.id })}
                      >
                        Registrar pre-reserva
                      </Button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </Card>
    </div>
  );
}

function Descripcion({ ocupada, salonActivo, pasada, alAbrir }: {
  ocupada?: Agenda['unidades'][number];
  salonActivo: boolean;
  pasada: boolean;
  alAbrir: (eventoId: number) => void;
}) {
  if (!ocupada) {
    const texto = pasada ? 'Sin evento.' : salonActivo ? 'Libre para ofrecer.' : 'Salón dado de baja: no acepta pre-reservas.';
    return <p className="body-sm v-muted">{texto}</p>;
  }
  if (ocupada.bloqueo) {
    return <p className="body-sm">{[ocupada.bloqueo.motivo, ocupada.bloqueo.detalle].filter(Boolean).join(' · ')}</p>;
  }
  const ev = ocupada.evento;
  if (!ev) return null;
  if (!ev.detalle) {
    return <p className="body-sm">La tiene {ev.vendedora.nombre}.</p>;
  }
  return (
    <div className="agenda__evento">
      <p className="label">{ev.nombre}</p>
      <p className="body-sm v-muted">{[ev.tipo, ev.cliente].filter(Boolean).join(' · ')}</p>
      <p className="body-sm v-muted">
        Vendedora: {ev.vendedora.nombre}
        {ev.planner ? ` · Planner: ${ev.planner.nombre}` : ''}
      </p>
      {ev.id != null && (
        <div className="agenda__ver">
          <Button variant="text" size="sm" iconEnd="chevron-right" onClick={() => alAbrir(ev.id as number)}>Ver ficha</Button>
        </div>
      )}
    </div>
  );
}
