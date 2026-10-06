import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { agenda, estadoDs, fechaLarga, hoyEnCordoba, sumarMeses, type Agenda } from '../../api/agenda';
import { catalogos, hora } from '../../api/catalogos';
import { fichas, type Ficha } from '../../api/eventos';
import { useDatos, useEnvio } from '../../api/useDatos';
import {
  AgendaGrid, Alert, Button, Card, Icon, Input, SalonTag, Select, StatusChip, ESTADOS,
  type AgendaEvent, type EventStatus, type SalonId,
} from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta } from '../comun/formato';
import { Encabezado } from '../layout/paginas';
import '../agenda/agenda.css';
import './eventos.css';

interface UnidadNueva {
  fecha: string;
  salonId: number;
  turnoId: number;
}

/**
 * UI-18 · Reprogramar evento: se elige la nueva unidad en la agenda (las ocupadas o bloqueadas no se pueden elegir)
 * y el motivo. El estado no cambia y la ficha conserva la fecha original. En pre-reserva es una modificación más,
 * sin motivo.
 */
export function PaginaReprogramar() {
  const { id } = useParams();
  const cargar = useCallback(() => fichas.ficha(Number(id)), [id]);
  const ficha = useDatos(cargar);

  return (
    <section className="pantalla">
      <Encabezado titulo="Reprogramar evento" antetitulo="Agenda" />
      <Cargando datos={ficha}>{(f) => <Reprogramar ficha={f} />}</Cargando>
    </section>
  );
}

function Reprogramar({ ficha }: { ficha: Ficha }) {
  const navegar = useNavigate();
  const hoy = hoyEnCordoba();
  const [mes, setMes] = useState(ficha.fecha.slice(0, 7));
  const cargarAgenda = useCallback(() => agenda.mes(mes), [mes]);
  const datosAgenda = useDatos(cargarAgenda);
  const motivos = useDatos(catalogos.motivos);
  const [dia, setDia] = useState<string | null>(null);
  const [elegida, setElegida] = useState<UnidadNueva | null>(null);
  const [motivoId, setMotivoId] = useState('');
  const [detalle, setDetalle] = useState('');
  const [faltaMotivo, setFaltaMotivo] = useState(false);
  const { guardando, error, enviar, limpiarError } = useEnvio();
  const preReserva = ficha.estado === 'PRE_RESERVA';
  const volver = () => navegar(`/eventos/${ficha.id}`);

  const deReprogramacion = (motivos.datos ?? []).filter((m) => m.ambito === 'REPROGRAMACION' && m.activo);

  async function confirmar() {
    if (!elegida) return;
    setFaltaMotivo(!preReserva && !motivoId);
    if (!preReserva && !motivoId) return;
    const nueva = await enviar(() => fichas.reprogramar(ficha.id, {
      salonId: elegida.salonId,
      fecha: elegida.fecha,
      turnoId: elegida.turnoId,
      motivoId: preReserva ? null : Number(motivoId),
      detalle: preReserva ? '' : detalle,
    }));
    if (nueva) {
      navegar(`/eventos/${ficha.id}`, {
        replace: true,
        state: { aviso: `Evento reprogramado: ${nueva.salon.nombre} · ${fechaCorta(nueva.fecha, true)} · ${nueva.turno.nombre}.` },
      });
    } else {
      // Si alguien tomó la unidad en el medio, la agenda se actualiza y hay que elegir otra.
      datosAgenda.recargar();
      setElegida(null);
    }
  }

  const nombreNueva = (ag: Agenda | undefined, u: UnidadNueva) => {
    const salon = ag?.salones.find((s) => s.id === u.salonId)?.nombre ?? '';
    const turno = ag?.turnos.find((t) => t.id === u.turnoId)?.nombre ?? '';
    return `${salon} · ${fechaCorta(u.fecha, true)} · ${turno}`;
  };

  const [anio, numeroMes] = mes.split('-').map(Number);

  return (
    <div className="reprogramar">
      <Card>
        <div className="reprogramar__fechas">
          <div>
            <p className="caption v-muted">Fecha actual</p>
            <p className="h4">{ficha.salon.nombre} · {fechaCorta(ficha.fecha, true)} · {ficha.turno.nombre}</p>
          </div>
          <Icon name="arrow-right" size={22} />
          <div>
            <p className="caption v-muted">Fecha nueva</p>
            <p className="h4">{elegida ? nombreNueva(datosAgenda.datos, elegida) : 'Elegila en la agenda'}</p>
          </div>
        </div>
        <p className="body-sm v-muted">{ficha.nombre} · {ficha.codigo}</p>
      </Card>

      {error && (
        <Alert tone="danger" title={error.status === 409 ? 'Esa unidad ya no está libre' : undefined}>{error.message}</Alert>
      )}

      <Cargando datos={datosAgenda}>
        {(ag) => (
          <div className="agenda">
            <Card className="agenda__mes">
              <AgendaGrid
                year={anio}
                month={numeroMes - 1}
                events={eventos(ag)}
                today={hoy}
                selected={dia ?? undefined}
                onSelectDay={(iso) => setDia(iso)}
                onPrev={() => { setMes(sumarMeses(mes, -1)); setDia(null); }}
                onNext={() => { setMes(sumarMeses(mes, 1)); setDia(null); }}
              />
            </Card>
            <UnidadesDelDia
              agenda={ag}
              fecha={dia}
              hoy={hoy}
              ficha={ficha}
              elegida={elegida}
              alElegir={(u) => { limpiarError(); setElegida(u); }}
            />
          </div>
        )}
      </Cargando>

      {!preReserva && (
        <Card title="Motivo">
          <div className="reprogramar__motivo">
            <Select
              label="Motivo de la reprogramación"
              placeholder="Elegí un motivo"
              options={deReprogramacion.map((m) => ({ value: String(m.id), label: m.nombre }))}
              value={motivoId}
              onChange={(e) => { setMotivoId(e.target.value); setFaltaMotivo(false); }}
              error={faltaMotivo ? 'Elegí el motivo de la reprogramación.' : error?.codigo === 'MOTIVO_INVALIDO' ? error.message : undefined}
              required
            />
            <Input label="Detalle" optional multiline rows={2} value={detalle} onChange={(e) => setDetalle(e.target.value)} error={error?.errorDe('detalle')} />
          </div>
        </Card>
      )}
      {preReserva && (
        <p className="body-sm v-muted">Es una pre-reserva: cambiar la fecha queda en el historial como una modificación, sin motivo.</p>
      )}

      <div className="reprogramar__acciones">
        <Button variant="outline" onClick={volver}>Volver</Button>
        <Button icon="calendar-clock" loading={guardando} disabled={!elegida} onClick={() => void confirmar()}>
          Confirmar reprogramación
        </Button>
      </div>
    </div>
  );
}

function eventos(ag: Agenda): AgendaEvent[] {
  const salones = new Map(ag.salones.map((s) => [s.id, s.codigo]));
  const turnos = new Map(ag.turnos.map((t) => [t.id, t.codigo]));
  return ag.unidades.map((u) => ({
    date: u.fecha,
    salon: salones.get(u.salonId) as SalonId,
    turno: turnos.get(u.turnoId) as 'mediodia' | 'noche',
    status: estadoDs(u.estado) as AgendaEvent['status'],
  }));
}

/** Las seis unidades del día: las libres se eligen; las ocupadas, la actual y las pasadas dicen por qué no. */
function UnidadesDelDia({ agenda: ag, fecha, hoy, ficha, elegida, alElegir }: {
  agenda: Agenda;
  fecha: string | null;
  hoy: string;
  ficha: Ficha;
  elegida: UnidadNueva | null;
  alElegir: (u: UnidadNueva) => void;
}) {
  if (!fecha) {
    return (
      <Card className="agenda__dia agenda__dia--vacio">
        <p className="body-sm v-muted">Tocá un día para ver qué unidades están libres.</p>
      </Card>
    );
  }
  return (
    <div className="agenda__dia">
      <Card title={fechaLarga(fecha)} eyebrow="Día elegido">
        {ag.turnos.map((turno) => (
          <div key={turno.id} className="agenda__turno">
            <p className="overline v-muted">{turno.nombre} · {hora(turno.horaInicio)} a {hora(turno.horaFin)}</p>
            <ul className="agenda__unidades">
              {ag.salones.map((salon) => {
                const ocupada = ag.unidades.find((u) => u.fecha === fecha && u.salonId === salon.id && u.turnoId === turno.id);
                const actual = fecha === ficha.fecha && salon.id === ficha.salon.id && turno.id === ficha.turno.id;
                const estado: EventStatus = ocupada ? estadoDs(ocupada.estado) : 'disponible';
                const motivo = actual ? 'Es la fecha actual del evento.'
                  : ocupada ? `${ESTADOS[estado].label}: no se puede elegir.`
                    : !salon.activo ? 'Salón dado de baja.'
                      : fecha < hoy ? 'La fecha ya pasó.' : null;
                const esLaElegida = elegida?.fecha === fecha && elegida.salonId === salon.id && elegida.turnoId === turno.id;
                return (
                  <li key={salon.id} className={esLaElegida ? 'agenda__unidad is-elegida' : 'agenda__unidad'}>
                    <div className="agenda__unidad-cabeza">
                      <SalonTag salon={salon.codigo} label={salon.nombre} />
                      <StatusChip status={estado} size="sm" />
                    </div>
                    {motivo ? (
                      <p className="body-sm v-muted">{motivo}</p>
                    ) : (
                      <div>
                        <Button
                          variant={esLaElegida ? 'solid' : 'outline'}
                          size="sm"
                          icon={esLaElegida ? 'check' : undefined}
                          onClick={() => alElegir({ fecha, salonId: salon.id, turnoId: turno.id })}
                        >
                          {esLaElegida ? `Elegida: ${salon.nombre} · ${turno.nombre}` : `Elegir ${salon.nombre} · ${turno.nombre}`}
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
