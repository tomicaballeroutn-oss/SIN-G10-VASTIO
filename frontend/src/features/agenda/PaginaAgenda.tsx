import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { agenda, estadoDs, fechaLarga, hoyEnCordoba, sumarMeses, type Agenda } from '../../api/agenda';
import { hora } from '../../api/catalogos';
import type { PreReservaRegistrada } from '../../api/eventos';
import { useDatos } from '../../api/useDatos';
import {
  AgendaGrid, Alert, Button, Card, ESTADOS, ESTADOS_DEL_EVENTO, SalonTag, Select, StatusChip,
  type AgendaEvent, type EventStatus, type SalonId,
} from '../../ds';
import { Cargando } from '../comun/Cargando';
import { Encabezado } from '../layout/paginas';
import { useSesion } from '../sesion/contexto';
import { DialogoPreReserva, type UnidadElegida } from './DialogoPreReserva';
import './agenda.css';

/** Estados que se pueden filtrar: disponible, los activos del evento y bloqueado. */
const ESTADOS_FILTRABLES: EventStatus[] = [
  'disponible',
  ...ESTADOS_DEL_EVENTO.filter((e) => e !== 'liberada' && e !== 'cancelado'),
  'bloqueado',
];

const MES_VALIDO = /^\d{4}-(0[1-9]|1[0-2])$/;

/**
 * UI-07 · Consultar agenda: un mes con las seis unidades de cada día (3 salones × 2 turnos).
 * Mes, día elegido y filtros quedan en la dirección, así se comparte o se recarga sin perder el lugar.
 */
export function PaginaAgenda() {
  const [busqueda, setBusqueda] = useSearchParams();
  const hoy = hoyEnCordoba();
  const pedido = busqueda.get('mes') ?? '';
  const mes = MES_VALIDO.test(pedido) ? pedido : hoy.slice(0, 7);
  const dia = busqueda.get('dia')?.startsWith(mes) ? busqueda.get('dia') : null;
  const salon = (busqueda.get('salon') ?? '') as SalonId | '';
  const estado = (busqueda.get('estado') ?? '') as EventStatus | '';

  const cargar = useCallback(() => agenda.mes(mes), [mes]);
  const datos = useDatos(cargar);
  const { usuario } = useSesion();
  const preReserva = !!usuario?.roles.some((r) => r === 'VENDEDORA' || r === 'DIRECCION' || r === 'COORDINACION');
  const [elegida, setElegida] = useState<UnidadElegida | null>(null);
  const [registrada, setRegistrada] = useState<PreReservaRegistrada | null>(null);

  function cambiar(valores: Record<string, string | null>) {
    const siguiente = new URLSearchParams(busqueda);
    for (const [clave, valor] of Object.entries(valores)) {
      if (valor) siguiente.set(clave, valor);
      else siguiente.delete(clave);
    }
    setBusqueda(siguiente, { replace: true });
  }

  const [anio, numeroMes] = mes.split('-').map(Number);

  return (
    <section className="pantalla">
      <Encabezado titulo="Agenda" antetitulo="Agenda" />
      {registrada && (
        <Alert tone="success" title="Pre-reserva registrada">
          {registrada.codigo} · {registrada.nombre}. La fecha queda apartada hasta que se señe o se libere.
        </Alert>
      )}
      <div className="agenda__filtros">
        <Button variant="outline" icon="calendar" onClick={() => cambiar({ mes: hoy.slice(0, 7), dia: hoy })}>Ir a hoy</Button>
        <Select
          label="Salón"
          options={[
            { value: '', label: 'Todos los salones' },
            ...(datos.datos?.salones ?? []).map((s) => ({ value: s.codigo, label: s.nombre })),
          ]}
          value={salon}
          onChange={(e) => cambiar({ salon: e.target.value })}
        />
        <Select
          label="Estado"
          options={[{ value: '', label: 'Todos los estados' }, ...ESTADOS_FILTRABLES.map((e) => ({ value: e, label: ESTADOS[e].label }))]}
          value={estado}
          onChange={(e) => cambiar({ estado: e.target.value })}
        />
      </div>
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
                resaltar={salon || estado ? (u) => (!salon || u.salon === salon) && (!estado || u.status === estado) : undefined}
              />
            </Card>
            <DetalleDelDia
              agenda={ag}
              fecha={dia}
              hoy={hoy}
              alPreReservar={preReserva ? (u) => { setRegistrada(null); setElegida(u); } : undefined}
            />
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

/** Las seis unidades del día elegido, por turno y salón, con quién la tiene o por qué está bloqueada. */
function DetalleDelDia({ agenda: ag, fecha, hoy, alPreReservar }: {
  agenda: Agenda;
  fecha: string | null;
  hoy: string;
  /** Sin él, el perfil no pre-reserva y no se ofrece el botón. */
  alPreReservar?: (unidad: UnidadElegida) => void;
}) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (fecha) ref.current?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' });
  }, [fecha]);

  if (!fecha) {
    return (
      <Card className="agenda__dia agenda__dia--vacio">
        <p className="body-sm v-muted">Tocá un día para ver sus seis unidades y quién tiene cada una.</p>
      </Card>
    );
  }

  return (
    <div ref={ref} className="agenda__dia">
      <Card title={fechaLarga(fecha)} eyebrow="Día elegido">
        {ag.turnos.map((turno) => (
          <div key={turno.id} className="agenda__turno">
            <p className="overline v-muted">
              {turno.nombre} · {hora(turno.horaInicio)} a {hora(turno.horaFin)}
            </p>
            <ul className="agenda__unidades">
              {ag.salones.map((salon) => {
                const ocupada = ag.unidades.find((u) => u.fecha === fecha && u.salonId === salon.id && u.turnoId === turno.id);
                const estado: EventStatus = ocupada ? estadoDs(ocupada.estado) : 'disponible';
                return (
                  <li key={salon.id} className="agenda__unidad">
                    <div className="agenda__unidad-cabeza">
                      <SalonTag salon={salon.codigo} label={salon.nombre} />
                      <StatusChip status={estado} size="sm" />
                    </div>
                    <Descripcion ocupada={ocupada} salonActivo={salon.activo} pasada={fecha < hoy} />
                    {!ocupada && salon.activo && fecha >= hoy && alPreReservar && (
                      <div>
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
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </Card>
    </div>
  );
}

function Descripcion({ ocupada, salonActivo, pasada }: { ocupada?: Agenda['unidades'][number]; salonActivo: boolean; pasada: boolean }) {
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
    </div>
  );
}
