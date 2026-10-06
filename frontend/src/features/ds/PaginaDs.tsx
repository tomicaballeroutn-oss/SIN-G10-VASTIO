import { useState, type ReactNode } from 'react';
import {
  Actor, AgendaGrid, Alert, Badge, Button, Card, Checkbox, Combobox, Dialog, EmptyState, EventCard, Icon, IconButton, Input,
  MovementCard, Nav, SalonTag, Select, SelectorArchivos, Stat, StatusChip, Stepper, StockLevel, Switch, Table, Tabs, Timeline,
  ESTADOS_DEL_EVENTO, ORDEN_SALONES, SALONES, aplicarTema, temaGuardado, type AgendaEvent, type EventStatus, type Tema,
} from '../../ds';
import './pagina-ds.css';

/** Los nueve estados del evento (máquina de estados) y los dos de la unidad sin evento. */
const ESTADOS_EVENTO: EventStatus[] = [...ESTADOS_DEL_EVENTO, 'bloqueado', 'disponible'];

const EVENTOS_DEMO: AgendaEvent[] = [
  { date: '2026-09-05', salon: 'avril', turno: 'noche', status: 'cerrado' },
  { date: '2026-09-05', salon: 'club', turno: 'noche', status: 'cerrado' },
  { date: '2026-09-12', salon: 'santa-barbara', turno: 'mediodia', status: 'realizado' },
  { date: '2026-09-12', salon: 'avril', turno: 'noche', status: 'realizado' },
  { date: '2026-09-19', salon: 'club', turno: 'mediodia', status: 'bloqueado' },
  { date: '2026-09-28', salon: 'santa-barbara', turno: 'mediodia', status: 'en-curso' },
  { date: '2026-10-03', salon: 'avril', turno: 'noche', status: 'contratado' },
  { date: '2026-09-26', salon: 'avril', turno: 'noche', status: 'confirmado' },
  { date: '2026-09-26', salon: 'club', turno: 'noche', status: 'confirmado' },
  { date: '2026-09-26', salon: 'santa-barbara', turno: 'noche', status: 'contratado' },
  { date: '2026-09-30', salon: 'club', turno: 'noche', status: 'senado' },
  { date: '2026-09-30', salon: 'avril', turno: 'noche', status: 'prereserva' },
];

const CLIENTES_DEMO = [
  { value: '1', label: 'Delfina Ríos', detail: 'DNI 40123456 · 351 555-1234' },
  { value: '2', label: 'Estudio Ríos y Asociados', detail: 'CUIT 30711222334' },
];

/** Buscador de clientes: filtra la lista de ejemplo y ofrece cargar uno nuevo. */
function ComboboxDeMuestra() {
  const [texto, setTexto] = useState('');
  const coinciden = CLIENTES_DEMO.filter((c) => c.label.toLowerCase().includes(texto.trim().toLowerCase()));
  return (
    <Combobox
      label="Cliente"
      icon="search"
      placeholder="Nombre o documento"
      value={texto}
      onInputChange={setTexto}
      onSelect={(o) => setTexto(o.value === 'nuevo' ? texto : o.label)}
      options={[...coinciden, { value: 'nuevo', label: `Cargar «${texto.trim()}» como cliente nuevo`, icon: 'user-plus' }]}
      hint="Probá con «ríos»."
    />
  );
}

/** Selector con un archivo de ejemplo ya elegido, para ver la lista. */
function SelectorDeMuestra() {
  const [archivos, setArchivos] = useState<File[]>(() => [new File([new Uint8Array(870_000)], 'contrato hoja 1.jpg', { type: 'image/jpeg' })]);
  return (
    <SelectorArchivos
      label="Contrato digitalizado"
      prompt="Sacá una foto o elegí un archivo de la galería"
      hint="PDF, JPG o PNG, hasta 10 MB cada uno."
      accept="application/pdf,image/jpeg,image/png"
      multiple
      files={archivos}
      onChange={setArchivos}
    />
  );
}

function Seccion({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="ds-seccion">
      <h2 className="h3">{titulo}</h2>
      <div className="ds-muestra">{children}</div>
    </section>
  );
}

/** Catálogo del sistema de diseño en /_ds. Sirve para revisar cada componente en los dos temas. */
export function PaginaDs() {
  const [tema, setTema] = useState<Tema>(temaGuardado());
  const [dialogo, setDialogo] = useState(false);
  const [invitados, setInvitados] = useState(180);
  const [nav, setNav] = useState('agenda');
  const [dia, setDia] = useState('2026-09-26');

  function cambiarTema(t: Tema) {
    setTema(t);
    aplicarTema(t);
  }

  return (
    <main className="ds-pagina">
      <header className="ds-cabecera">
        <div>
          <p className="overline v-muted">Sprint 0</p>
          <h1>Sistema de diseño</h1>
        </div>
        <Tabs
          variant="segmented"
          label="Tema"
          value={tema}
          onChange={(id) => cambiarTema(id as Tema)}
          items={[
            { id: 'claro', label: 'Claro', icon: 'sun' },
            { id: 'oscuro', label: 'Oscuro', icon: 'moon' },
            { id: 'sistema', label: 'Sistema' },
          ]}
        />
      </header>

      <Seccion titulo="Tipografía">
        <div className="ds-columna">
          <p className="display-sm">Agenda de eventos</p>
          <p className="overline">Salón Avril</p>
          <p className="h1">Eventos de septiembre</p>
          <p className="h2">Próximas entregas a barra</p>
          <p className="h3">Sábado 14 · Avril</p>
          <p className="h4">Datos del cliente</p>
          <p className="body-lg">Registrá la seña para confirmar la fecha.</p>
          <p className="body">La pre-reserva aparta la fecha mientras negociás con el cliente.</p>
          <p className="body-sm v-muted">Cargado por Melina · hoy 14:32</p>
          <p><span className="stat">128</span> <span className="numeral">36 cajones</span> <span className="numeral-sm">14/09 · 21:00</span></p>
        </div>
      </Seccion>

      <Seccion titulo="Icon">
        {['calendar', 'wine', 'package', 'warehouse', 'truck', 'users', 'bell', 'settings', 'house', 'martini', 'scan-line', 'printer'].map((n) => (
          <span key={n} className="ds-icono"><Icon name={n} size={24} /><span className="caption v-muted">{n}</span></span>
        ))}
      </Seccion>

      <Seccion titulo="Button e IconButton">
        <Button icon="banknote">Registrar seña</Button>
        <Button variant="outline">Ver ficha</Button>
        <Button variant="text">Ver historial</Button>
        <Button tone="danger" icon="circle-x">Cancelar evento</Button>
        <Button variant="outline" tone="danger">Liberar pre-reserva</Button>
        <Button loading>Guardando</Button>
        <Button size="lg" icon="arrow-right-left">Entregar a barra</Button>
        <Button size="sm" disabled>Deshabilitado</Button>
        <IconButton icon="pencil" label="Modificar" />
        <IconButton icon="plus" label="Agregar" variant="solid" />
        <IconButton icon="trash-2" label="Quitar" variant="text" tone="danger" />
      </Seccion>

      <Seccion titulo="Input, Select, Combobox, SelectorArchivos, Checkbox, Switch y Stepper">
        <div className="ds-formulario">
          <Input label="Nombre del cliente" placeholder="Nombre y apellido" hint="Como figura en el DNI" />
          <Input label="Importe de la seña" icon="banknote" suffix="pesos" inputMode="decimal" defaultValue="150.000" />
          <Input label="DNI del firmante" error="Escribí el DNI sin puntos." defaultValue="30.123" />
          <Input label="Observaciones" optional multiline />
          <Select
            label="Turno"
            placeholder="Elegí un turno"
            options={[{ value: 'mediodia', label: 'Mediodía' }, { value: 'noche', label: 'Noche' }]}
          />
          <ComboboxDeMuestra />
          <SelectorDeMuestra />
          <Stepper label="Invitados" value={invitados} onChange={setInvitados} step={10} unit="personas" />
          <Checkbox label="Invitados definitivos" hint="Ya no se esperan cambios" defaultChecked />
          <Switch label="Tema oscuro en barra" />
        </div>
      </Seccion>

      <Seccion titulo="Alert">
        <div className="ds-columna">
          <Alert tone="info" title="Tu sesión va a expirar pronto" icon="clock">Guardá los cambios que tengas pendientes.</Alert>
          <Alert tone="success" title="Seña registrada">El evento pasó a Señado.</Alert>
          <Alert tone="warning" title="Stock bajo">Ya hay 6 cajones de Fernet comprometidos por la agenda.</Alert>
          <Alert tone="danger" title="Esa fecha ya está tomada" action={<Button variant="outline" size="sm">Ver agenda</Button>}>
            Elegí otro salón, otra fecha u otro turno.
          </Alert>
        </div>
      </Seccion>

      <Seccion titulo="Dialog y EmptyState">
        <Button variant="outline" onClick={() => setDialogo(true)}>Abrir diálogo</Button>
        <Dialog
          open={dialogo}
          title="Cancelar evento"
          onClose={() => setDialogo(false)}
          actions={
            <>
              <Button variant="outline" onClick={() => setDialogo(false)}>Volver</Button>
              <Button tone="danger" onClick={() => setDialogo(false)}>Cancelar evento</Button>
            </>
          }
        >
          La fecha queda libre y se avisa a todas las áreas.
        </Dialog>
        <Card flush>
          <EmptyState icon="calendar" title="Todavía no hay eventos este mes" action={<Button icon="plus">Registrar pre-reserva</Button>}>
            Cuando una vendedora aparte una fecha, aparece acá.
          </EmptyState>
        </Card>
      </Seccion>

      <Seccion titulo="Badge">
        {(['neutral', 'brand', 'accent', 'success', 'warning', 'danger', 'info'] as const).map((t) => (
          <Badge key={t} tone={t}>{t}</Badge>
        ))}
        <Badge tone="accent" icon="sparkles">3 novedades</Badge>
      </Seccion>

      <Seccion titulo="StatusChip: estados del evento">
        {ESTADOS_EVENTO.map((s) => <StatusChip key={s} status={s} />)}
      </Seccion>

      <Seccion titulo="Rellenos de la agenda">
        <table className="ds-rellenos body-sm">
          <thead>
            <tr>
              <th scope="col" className="label">Estado</th>
              {ORDEN_SALONES.map((s) => <th key={s} scope="col" className="label">{SALONES[s]}</th>)}
            </tr>
          </thead>
          <tbody>
            {ESTADOS_EVENTO.map((estado) => (
              <tr key={estado}>
                <th scope="row"><StatusChip status={estado} size="sm" /></th>
                {ORDEN_SALONES.map((s) => (
                  <td key={s}><span className={`v-pip v-pip--${s} v-pip--${estado}`} /></td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        <p className="body-sm v-muted ds-nota">
          Liberada y cancelado no ocupan la unidad: en la agenda el turno vuelve a verse disponible.
        </p>
      </Seccion>

      <Seccion titulo="StatusChip: stock">
        <StatusChip status="ok" />
        <StatusChip status="bajo" />
        <StatusChip status="sin-stock" size="sm" />
      </Seccion>

      <Seccion titulo="SalonTag">
        <SalonTag salon="avril" />
        <SalonTag salon="club" />
        <SalonTag salon="santa-barbara" />
        <SalonTag salon="avril" variant="dot" />
        <SalonTag salon="club" variant="dot" />
        <SalonTag salon="santa-barbara" variant="dot" />
      </Seccion>

      <Seccion titulo="Card, Actor y Stat">
        <Card eyebrow="Casamiento" title="Bruno y Martina" subtitle="sáb 26 sep · noche" actions={<IconButton icon="ellipsis" label="Más acciones" variant="text" />}
          footer={<Actor name="Lucía Ferreyra" action="apartó la fecha" at="hoy 14:32" size="sm" />}>
          <p className="body">180 invitados · Avril</p>
        </Card>
        <Stat label="Ocupación de septiembre" value="72" unit="%" delta="+8 puntos" deltaTone="success" deltaIcon="arrow-right" hint="Contra agosto" />
        <Stat label="Consumo real" value="36" unit="cajones" />
      </Seccion>

      <Seccion titulo="Table">
        <Table
          caption="Ingresos al depósito"
          columns={[
            { key: 'bebida', header: 'Bebida' },
            { key: 'fecha', header: 'Fecha', numeric: true },
            { key: 'cantidad', header: 'Cantidad', numeric: true },
          ]}
          rows={[
            { id: 1, bebida: 'Fernet Branca 750 ml', fecha: '14/09', cantidad: '12 cajones' },
            { id: 2, bebida: 'Malbec reserva', fecha: '15/09', cantidad: '8 cajones' },
          ]}
        />
      </Seccion>

      <Seccion titulo="Tabs">
        <Tabs items={[{ id: 'datos', label: 'Datos' }, { id: 'servicios', label: 'Servicios', count: 4 }, { id: 'historial', label: 'Historial', icon: 'history' }]} />
      </Seccion>

      <Seccion titulo="Timeline">
        <Timeline
          items={[
            { title: 'Pre-reserva registrada', to: 'prereserva', actor: 'Lucía Ferreyra', action: 'apartó la fecha', at: '02/09 10:15', icon: 'hourglass', tone: 'warning' },
            { title: 'Seña registrada', from: 'prereserva', to: 'senado', detail: 'Importe y DNI del firmante cargados', actor: 'Lucía Ferreyra', at: '05/09 18:40', icon: 'banknote', tone: 'info' },
            { title: 'Evento confirmado', from: 'senado', to: 'confirmado', actor: 'Ana Sosa', at: 'hoy 09:12', icon: 'circle-check', tone: 'success' },
          ]}
        />
      </Seccion>

      <Seccion titulo="Nav">
        <div className="ds-nav-lateral">
          <Nav
            value={nav}
            onSelect={setNav}
            logo={<img src="/brand/vastio-logo.png" alt="Vastio" className="v-logo" />}
            items={[
              { id: 'agenda', label: 'Agenda', icon: 'calendar', group: 'Agenda' },
              { id: 'eventos', label: 'Eventos', icon: 'clipboard-list', group: 'Agenda', badge: 2 },
              { id: 'existencias', label: 'Existencias', icon: 'package', group: 'Bebidas' },
              { id: 'usuarios', label: 'Usuarios y perfiles', icon: 'users', group: 'Gestión' },
            ]}
          />
        </div>
        <div className="ds-nav-inferior">
          <Nav
            layout="bottom"
            value={nav}
            onSelect={setNav}
            items={[
              { id: 'agenda', label: 'Agenda', icon: 'calendar' },
              { id: 'eventos', label: 'Eventos', icon: 'clipboard-list', badge: 2 },
              { id: 'existencias', label: 'Existencias', icon: 'package' },
              { id: 'movimientos', label: 'Movimientos', icon: 'history' },
              { id: 'mas', label: 'Más', icon: 'menu' },
            ]}
          />
        </div>
      </Seccion>

      <Seccion titulo="AgendaGrid">
        <div className="ds-agenda">
          <AgendaGrid year={2026} month={8} events={EVENTOS_DEMO} today="2026-09-28" selected={dia} onSelectDay={setDia} onPrev={() => {}} onNext={() => {}} />
        </div>
      </Seccion>

      <Seccion titulo="EventCard">
        <EventCard salon="avril" status="confirmado" tipo="Casamiento" title="Bruno y Martina" fecha="sáb 26 sep" turno="Noche" invitados={180} vendedora="Lucía Ferreyra" planner="Ana Sosa" onOpen={() => {}} />
        <EventCard salon="club" status="senado" tipo="Quince" title="Delfina Ríos" fecha="dom 4 oct" turno="Mediodía" invitados={90} vendedora="Melina Sifón" planner={null} />
      </Seccion>

      <Seccion titulo="StockLevel y MovementCard">
        <StockLevel name="Fernet Branca" presentacion="750 ml" ubicacion="Depósito principal" cantidad={14} comprometido={6} />
        <StockLevel name="Malbec reserva" presentacion="750 ml" ubicacion="Depósito principal" cantidad={3} comprometido={8} />
        <MovementCard
          tipo="entrega"
          evento="Bruno y Martina"
          salon="avril"
          desde="Depósito principal"
          hasta="Barra Avril"
          items={[{ nombre: 'Fernet Branca 750 ml', cantidad: 6 }, { nombre: 'Coca-Cola 2,25 l', cantidad: 1 }]}
          actor="Nadir Gómez"
          at="hoy 20:05"
        />
      </Seccion>
    </main>
  );
}
