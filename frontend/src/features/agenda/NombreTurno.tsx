import type { Agenda } from '../../api/agenda';
import { hora } from '../../api/catalogos';
import { Icon } from '../../ds';

/** Encabezado de un turno en el detalle del día (UI-07 y UI-18): ícono, nombre y horario. */
export function NombreTurno({ turno }: { turno: Agenda['turnos'][number] }) {
  return (
    <p className="agenda__turno-nombre">
      <Icon name={turno.codigo === 'noche' ? 'moon' : 'sun'} size={18} />
      <span className="label">{turno.nombre}</span>
      <span className="body-sm v-muted">{hora(turno.horaInicio)} a {hora(turno.horaFin)}</span>
    </p>
  );
}
