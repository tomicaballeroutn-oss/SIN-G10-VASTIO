import { useState, type ReactNode } from 'react';
import { Card, EmptyState, Nav, Tabs, aplicarTema, temaGuardado, type Tema } from '../../ds';
import { Navigate, useNavigate } from 'react-router';
import { useSesion } from '../sesion/contexto';
import { BloqueCuenta } from './BloqueCuenta';
import { itemsPara, rutaDeInicio, type ItemDeMenu } from './menu';

/** Antetítulo (grupo del menú) y título de la pantalla. */
export function Encabezado({ titulo, antetitulo }: { titulo: string; antetitulo?: string }) {
  return (
    <header className="pantalla__titulos">
      {antetitulo && <p className="overline v-muted">{antetitulo}</p>}
      <h1>{titulo}</h1>
    </header>
  );
}

/**
 * Pantalla de un ítem del menú. Si el perfil no lo tiene, avisa en lugar de mostrarla
 * (la autorización real está en el backend; esto evita pantallas que solo darían 403).
 */
export function ConPermiso({ item, children }: { item: ItemDeMenu; children: ReactNode }) {
  const { usuario } = useSesion();
  const propio = usuario ? itemsPara(usuario.roles).some((it) => it.id === item.id) : false;
  if (propio) return children;
  return (
    <section className="pantalla">
      <Encabezado titulo={item.label} antetitulo={item.group} />
      <Card flush>
        <EmptyState icon="lock" title="Tu perfil no tiene acceso a esta pantalla">
          Si necesitás usarla, pedíselo a Dirección o a Coordinación.
        </EmptyState>
      </Card>
    </section>
  );
}

/** Lugar de cada pantalla del menú hasta que llegue su historia. */
export function PaginaPendiente({ item }: { item: ItemDeMenu }) {
  const { usuario } = useSesion();
  // El ítem tal como lo ve este perfil (p. ej. «Mis eventos»).
  const propio = usuario ? itemsPara(usuario.roles).find((it) => it.id === item.id) : undefined;
  return (
    <section className="pantalla">
      <Encabezado titulo={propio?.label ?? item.label} antetitulo={item.group} />
      <Card flush>
        <EmptyState icon={item.icon} title="Esta pantalla todavía no está disponible">
          Se construye con su historia de usuario en los próximos sprints.
        </EmptyState>
      </Card>
    </section>
  );
}

/** «Más» de la navegación inferior: todo lo que no entró en los cinco ítems. */
export function PaginaMas() {
  const { usuario } = useSesion();
  const navegar = useNavigate();
  const items = usuario ? itemsPara(usuario.roles) : [];
  return (
    <section className="pantalla">
      <Encabezado titulo="Más" />
      <Card flush>
        <Nav label="Todas las secciones" items={items} onSelect={(id) => {
          const destino = items.find((it) => it.id === id);
          if (destino) navegar(destino.ruta);
        }} className="pagina-mas__nav" footer={<BloqueCuenta />} />
      </Card>
    </section>
  );
}

/** Provisoria hasta UI-03: datos de la sesión y tema de la pantalla. */
export function PaginaMiCuenta() {
  const { usuario } = useSesion();
  const [tema, setTema] = useState<Tema>(temaGuardado());
  if (!usuario) return null;
  return (
    <section className="pantalla">
      <Encabezado titulo="Mi cuenta" />
      <Card title={usuario.nombreCompleto} subtitle={`Usuario: ${usuario.nombreUsuario}`}>
        <div className="pantalla">
          <div className="pantalla__titulos">
            <p className="label">Tema de la pantalla</p>
            <p className="body-sm v-muted">El oscuro es para la operación nocturna en barra y depósito.</p>
          </div>
          <Tabs
            variant="segmented"
            label="Tema de la pantalla"
            value={tema}
            onChange={(id) => {
              setTema(id as Tema);
              aplicarTema(id as Tema);
            }}
            items={[
              { id: 'claro', label: 'Claro', icon: 'sun' },
              { id: 'oscuro', label: 'Oscuro', icon: 'moon' },
              { id: 'sistema', label: 'Como el dispositivo' },
            ]}
          />
        </div>
      </Card>
    </section>
  );
}

/** Lleva a la pantalla de inicio del perfil. */
export function IrAlInicio() {
  const { usuario } = useSesion();
  return usuario ? <Navigate to={rutaDeInicio(usuario.roles)} replace /> : null;
}
