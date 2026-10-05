import { useCallback, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router';
import { catalogos } from '../../api/catalogos';
import { fichas, type DatosEvento, type Ficha } from '../../api/eventos';
import { useDatos, useEnvio } from '../../api/useDatos';
import { Alert, Button, Card, IconButton, Input, SalonTag, Select } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta } from '../comun/formato';
import { Encabezado } from '../layout/paginas';
import './eventos.css';

/**
 * UI-08 · Registrar evento: completa los datos básicos (cliente, contactos, tipo, nombre y observaciones internas).
 * La cantidad de invitados se registra desde la ficha (UI-14). Salón, fecha y turno no se cambian acá: eso es reprogramar.
 */
export function PaginaDatosEvento() {
  const { id } = useParams();
  const cargar = useCallback(() => fichas.ficha(Number(id)), [id]);
  const ficha = useDatos(cargar);
  const tipos = useDatos(catalogos.tiposEvento);

  return (
    <section className="pantalla">
      <Encabezado titulo="Datos del evento" antetitulo="Registrar evento" />
      <Cargando datos={ficha}>
        {(f) =>
          f.acciones.modificar ? (
            <Formulario ficha={f} tipos={tipos.datos ?? []} />
          ) : (
            <Alert tone="warning" title="No podés modificar este evento">
              Lo modifican la vendedora titular, la planner asignada, Coordinación y Dirección, entre Pre-reserva y Confirmado.
            </Alert>
          )
        }
      </Cargando>
    </section>
  );
}

type ContactoEditable = DatosEvento['contactos'][number] & { clave: string };

let claves = 0;
const nuevaClave = () => `nuevo-${++claves}`;

function Formulario({ ficha, tipos }: { ficha: Ficha; tipos: { id: number; nombre: string; activo: boolean }[] }) {
  const navegar = useNavigate();
  const [nombre, setNombre] = useState(ficha.nombre);
  const [tipoId, setTipoId] = useState(String(ficha.tipo.id));
  const [observaciones, setObservaciones] = useState(ficha.observacionesInternas ?? '');
  const [cliente, setCliente] = useState({
    nombre: ficha.cliente.nombre,
    documento: ficha.cliente.documento ?? '',
    telefono: ficha.cliente.telefono ?? '',
    email: ficha.cliente.email ?? '',
  });
  const [contactos, setContactos] = useState<ContactoEditable[]>(
    ficha.contactos.map((c) => ({
      clave: `c${c.id}`, id: c.id, nombre: c.nombre, vinculo: c.vinculo ?? '', telefono: c.telefono ?? '', email: c.email ?? '',
    })),
  );
  const { guardando, error, enviar } = useEnvio();

  // El tipo actual se ofrece aunque esté dado de baja: el evento lo conserva.
  const opcionesTipo = tipos.filter((t) => t.activo || t.id === ficha.tipo.id).map((t) => ({ value: String(t.id), label: t.nombre }));

  function cambiarContacto(clave: string, campo: keyof DatosEvento['contactos'][number], valor: string) {
    setContactos((lista) => lista.map((c) => (c.clave === clave ? { ...c, [campo]: valor } : c)));
  }

  async function guardar(e: FormEvent) {
    e.preventDefault();
    const guardada = await enviar(() =>
      fichas.registrarDatos(ficha.id, {
        version: ficha.version,
        nombre,
        tipoEventoId: Number(tipoId),
        observacionesInternas: observaciones,
        cliente,
        contactos: contactos.map((c) => ({ id: c.id, nombre: c.nombre, vinculo: c.vinculo, telefono: c.telefono, email: c.email })),
      }),
    );
    if (guardada) navegar(`/eventos/${ficha.id}`, { replace: true, state: { aviso: 'Datos del evento guardados.' } });
  }

  const errorDe = (campo: string) => error?.errorDe(campo);

  return (
    <form className="datos-evento" onSubmit={guardar} noValidate>
      <div className="ficha__etiquetas">
        <SalonTag salon={ficha.salon.codigo} label={ficha.salon.nombre} variant="dot" />
        <span className="body">{fechaCorta(ficha.fecha, true)} · {ficha.turno.nombre}</span>
        <span className="body-sm v-muted">{ficha.codigo}</span>
      </div>
      {error && (
        <Alert tone="danger" title={error.codigo === 'EVENTO_MODIFICADO' ? 'No se guardó' : undefined}>
          {error.message}
        </Alert>
      )}

      <Card title="Evento">
        <div className="datos-evento__campos">
          <Select label="Tipo de evento" options={opcionesTipo} value={tipoId} onChange={(e) => setTipoId(e.target.value)} error={errorDe('tipoEventoId')} />
          <Input label="Nombre del evento" value={nombre} onChange={(e) => setNombre(e.target.value)} error={errorDe('nombre')} required />
          <Input
            label="Observaciones internas"
            optional
            multiline
            rows={4}
            hint="No se muestran en la vista de cocina."
            value={observaciones}
            onChange={(e) => setObservaciones(e.target.value)}
            error={errorDe('observacionesInternas')}
          />
        </div>
      </Card>

      <Card title="Cliente" subtitle="Los cambios valen para todos los eventos de este cliente.">
        <div className="datos-evento__campos datos-evento__campos--par">
          <Input label="Nombre" value={cliente.nombre} onChange={(e) => setCliente({ ...cliente, nombre: e.target.value })} error={errorDe('cliente.nombre')} required />
          <Input
            label="Documento"
            optional
            inputMode="numeric"
            hint="DNI o CUIT, sin puntos ni guiones."
            value={cliente.documento}
            onChange={(e) => setCliente({ ...cliente, documento: e.target.value.replace(/\D/g, '') })}
            error={errorDe('cliente.documento')}
          />
          <Input label="Teléfono" optional type="tel" inputMode="tel" value={cliente.telefono} onChange={(e) => setCliente({ ...cliente, telefono: e.target.value })} error={errorDe('cliente.telefono')} />
          <Input label="Correo" optional type="email" inputMode="email" value={cliente.email} onChange={(e) => setCliente({ ...cliente, email: e.target.value })} error={errorDe('cliente.email')} />
        </div>
      </Card>

      <Card
        title="Otros contactos"
        subtitle="Madre, padre, organizador: quien más hable con el salón."
        actions={
          <Button variant="outline" size="sm" icon="plus" onClick={() => setContactos((l) => [...l, { clave: nuevaClave(), nombre: '', vinculo: '', telefono: '', email: '' }])}>
            Agregar contacto
          </Button>
        }
      >
        {contactos.length === 0 ? (
          <p className="body-sm v-muted">Sin contactos además del cliente.</p>
        ) : (
          <ul className="datos-evento__contactos">
            {contactos.map((c, i) => (
              <li key={c.clave} className="datos-evento__contacto">
                <div className="datos-evento__campos datos-evento__campos--par">
                  <Input label="Nombre" value={c.nombre} onChange={(e) => cambiarContacto(c.clave, 'nombre', e.target.value)} error={errorDe(`contactos[${i}].nombre`)} required />
                  <Input label="Vínculo" optional placeholder="Madre, padre, organizador" value={c.vinculo} onChange={(e) => cambiarContacto(c.clave, 'vinculo', e.target.value)} />
                  <Input label="Teléfono" optional type="tel" inputMode="tel" value={c.telefono} onChange={(e) => cambiarContacto(c.clave, 'telefono', e.target.value)} />
                  <Input label="Correo" optional type="email" inputMode="email" value={c.email} onChange={(e) => cambiarContacto(c.clave, 'email', e.target.value)} error={errorDe(`contactos[${i}].email`)} />
                </div>
                <IconButton
                  icon="trash-2"
                  tone="danger"
                  variant="text"
                  label={`Quitar contacto ${c.nombre || i + 1}`}
                  onClick={() => setContactos((l) => l.filter((x) => x.clave !== c.clave))}
                />
              </li>
            ))}
          </ul>
        )}
      </Card>

      <div className="datos-evento__acciones">
        <Button variant="outline" onClick={() => navegar(`/eventos/${ficha.id}`)}>Cancelar</Button>
        <Button type="submit" icon="check" loading={guardando}>Guardar datos del evento</Button>
      </div>
    </form>
  );
}
