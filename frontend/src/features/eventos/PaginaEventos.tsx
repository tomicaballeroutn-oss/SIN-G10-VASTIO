import { useNavigate } from 'react-router';
import { fichas } from '../../api/eventos';
import { useDatos } from '../../api/useDatos';
import { Button, Card, EmptyState, ESTADO_POR_CODIGO, EventCard } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta } from '../comun/formato';
import { itemsPara } from '../layout/menu';
import { Encabezado } from '../layout/paginas';
import { useSesion } from '../sesion/contexto';
import './eventos.css';

/**
 * Próximos eventos. Para la vendedora son los suyos y para la planner los asignados («Mis eventos»);
 * Dirección, Coordinación, Administración y Compras ven todos.
 */
export function PaginaEventos() {
  const datos = useDatos(fichas.proximos);
  const navegar = useNavigate();
  const { usuario } = useSesion();
  const titulo = usuario ? itemsPara(usuario.roles).find((it) => it.id === 'eventos')?.label ?? 'Eventos' : 'Eventos';

  return (
    <section className="pantalla">
      <Encabezado titulo={titulo} antetitulo="Agenda" />
      <Cargando datos={datos}>
        {(lista) =>
          lista.length === 0 ? (
            <Card flush>
              <EmptyState
                icon="calendar"
                title="No hay eventos próximos"
                action={<Button variant="outline" icon="calendar" onClick={() => navegar('/agenda')}>Ir a la agenda</Button>}
              >
                Los eventos aparecen acá desde que se pre-reserva la fecha.
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
          )
        }
      </Cargando>
    </section>
  );
}
