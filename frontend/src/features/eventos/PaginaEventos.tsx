import { useState } from 'react';
import { useNavigate } from 'react-router';
import { fichas } from '../../api/eventos';
import { useDatos } from '../../api/useDatos';
import { Button, Card, EmptyState, ESTADO_POR_CODIGO, EventCard, Tabs } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta } from '../comun/formato';
import { itemsPara } from '../layout/menu';
import { Encabezado } from '../layout/paginas';
import { useSesion } from '../sesion/contexto';
import './eventos.css';

/**
 * Próximos eventos. Para la vendedora son los suyos («Mis eventos»); Dirección, Coordinación, Administración,
 * Planner y Compras ven todos. La planner puede quedarse con los que tiene asignados (su programación operativa).
 */
export function PaginaEventos() {
  const datos = useDatos(fichas.proximos);
  const navegar = useNavigate();
  const { usuario } = useSesion();
  const titulo = usuario ? itemsPara(usuario.roles).find((it) => it.id === 'eventos')?.label ?? 'Eventos' : 'Eventos';
  const esPlanner = usuario?.roles.includes('PLANNER') ?? false;
  const [filtro, setFiltro] = useState<'todos' | 'asignados'>('todos');

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
          const lista = filtro === 'asignados' ? todos.filter((e) => e.planner?.id === usuario?.id) : todos;
          return lista.length === 0 && filtro === 'asignados' ? (
            <Card flush>
              <EmptyState icon="calendar" title="No tenés eventos asignados">
                Cuando Coordinación te asigne un evento, aparece acá.
              </EmptyState>
            </Card>
          ) : lista.length === 0 ? (
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
          );
        }}
      </Cargando>
    </section>
  );
}
