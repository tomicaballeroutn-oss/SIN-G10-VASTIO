import { useCallback, useState } from 'react';
import type { Rol } from '../../api/cliente';
import { fichas } from '../../api/eventos';
import { useDatos, useEnvio } from '../../api/useDatos';
import { NOMBRE_DE_ROL, ROLES, usuarios, type Usuario } from '../../api/usuarios';
import { Alert, Badge, Button, Card, Checkbox, Dialog, Input, Select, Table, type TableColumn } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta } from '../comun/formato';
import { Encabezado } from '../layout/paginas';
import { useSesion } from '../sesion/contexto';
import './usuarios.css';

/**
 * UI-05 · Administrar usuarios: listado con búsqueda y filtro por perfil, alta, modificación (nombre, perfiles y
 * contacto; el usuario no cambia), baja lógica y reactivación. Antes de una baja se muestran los eventos activos de
 * la persona. Solo Dirección y Coordinación.
 */
export function PaginaUsuarios() {
  const datos = useDatos(usuarios.listar);
  const { usuario: yo } = useSesion();
  const [buscar, setBuscar] = useState('');
  const [perfil, setPerfil] = useState<Rol | ''>('');
  const [alta, setAlta] = useState(false);
  const [aDarDeBaja, setADarDeBaja] = useState<Usuario | null>(null);
  const [aModificar, setAModificar] = useState<Usuario | null>(null);
  const reactivacion = useEnvio();
  const [aviso, setAviso] = useState<string | null>(null);

  function agregado(nuevo: Usuario) {
    datos.fijar((previos) => [nuevo, ...(previos ?? [])]);
    setAlta(false);
    setAviso(`Usuario creado. Pasale a ${nuevo.nombreCompleto} su usuario y la contraseña inicial: la va a cambiar al ingresar.`);
  }

  function reemplazar(actualizado: Usuario) {
    datos.fijar((previos) => (previos ?? []).map((u) => (u.id === actualizado.id ? actualizado : u)));
  }

  function dadoDeBaja(actualizado: Usuario) {
    reemplazar(actualizado);
    setADarDeBaja(null);
    setAviso(`Se dio de baja a ${actualizado.nombreCompleto}. Lo que registró se conserva.`);
  }

  function modificado(actualizado: Usuario) {
    reemplazar(actualizado);
    setAModificar(null);
    setAviso(`Datos de ${actualizado.nombreCompleto} guardados.`);
  }

  async function reactivar(u: Usuario) {
    const actualizado = await reactivacion.enviar(() => usuarios.reactivar(u.id));
    if (actualizado) {
      reemplazar(actualizado);
      setAviso(`Se reactivó a ${actualizado.nombreCompleto}. Ya puede ingresar con su contraseña de siempre.`);
    }
  }

  const columnas: TableColumn<Usuario>[] = [
    {
      key: 'nombreCompleto',
      header: 'Nombre',
      render: (u) => (
        <span className="usuarios__nombre">
          {u.nombreCompleto}
          {!u.activo && <Badge>De baja</Badge>}
        </span>
      ),
    },
    { key: 'nombreUsuario', header: 'Usuario' },
    { key: 'roles', header: 'Perfiles', render: (u) => u.roles.map((r) => NOMBRE_DE_ROL[r]).join(' · ') },
    { key: 'contacto', header: 'Contacto', render: (u) => [u.email, u.telefono].filter(Boolean).join(' · ') || '—' },
    {
      key: 'acciones',
      header: 'Acciones',
      render: (u) => (
        <span className="usuarios__acciones">
          <Button variant="text" size="sm" icon="pencil" onClick={() => setAModificar(u)}>Modificar</Button>
          {u.activo && u.id !== yo?.id && (
            <Button variant="text" tone="danger" size="sm" onClick={() => setADarDeBaja(u)}>Dar de baja</Button>
          )}
          {!u.activo && (
            <Button variant="text" size="sm" icon="refresh-cw" onClick={() => void reactivar(u)}>Reactivar</Button>
          )}
        </span>
      ),
    },
  ];

  return (
    <section className="pantalla">
      <Encabezado titulo="Usuarios y perfiles" antetitulo="Gestión" />
      {aviso && <Alert tone="success">{aviso}</Alert>}
      {reactivacion.error && <Alert tone="danger">{reactivacion.error.message}</Alert>}
      <div className="usuarios__filtros">
        <Input label="Buscar" icon="search" placeholder="Nombre o usuario" value={buscar} onChange={(e) => setBuscar(e.target.value)} />
        <Select
          label="Perfil"
          options={[{ value: '', label: 'Todos los perfiles' }, ...ROLES.map((r) => ({ value: r, label: NOMBRE_DE_ROL[r] }))]}
          value={perfil}
          onChange={(e) => setPerfil(e.target.value as Rol | '')}
        />
        <Button icon="user-plus" onClick={() => setAlta(true)}>Nuevo usuario</Button>
      </div>
      <Cargando datos={datos}>
        {(lista) => {
          const texto = buscar.trim().toLowerCase();
          const filtrados = lista.filter(
            (u) =>
              (!perfil || u.roles.includes(perfil)) &&
              (!texto || u.nombreCompleto.toLowerCase().includes(texto) || u.nombreUsuario.includes(texto)),
          );
          return (
            <Card flush>
              <Table caption="Usuarios" columns={columnas} rows={filtrados} empty="No hay usuarios con esos filtros." />
            </Card>
          );
        }}
      </Cargando>
      {alta && <DialogoAlta alCerrar={() => setAlta(false)} alCrear={agregado} />}
      {aDarDeBaja && <DialogoBaja usuario={aDarDeBaja} alCerrar={() => setADarDeBaja(null)} alConfirmar={dadoDeBaja} />}
      {aModificar && (
        <DialogoModificar usuario={aModificar} esYo={aModificar.id === yo?.id} alCerrar={() => setAModificar(null)} alGuardar={modificado} />
      )}
    </section>
  );
}

function DialogoAlta({ alCerrar, alCrear }: { alCerrar: () => void; alCrear: (u: Usuario) => void }) {
  const [nombreCompleto, setNombreCompleto] = useState('');
  const [nombreUsuario, setNombreUsuario] = useState('');
  const [roles, setRoles] = useState<Rol[]>([]);
  const [email, setEmail] = useState('');
  const [telefono, setTelefono] = useState('');
  const [contrasenaInicial, setContrasenaInicial] = useState('');
  const { guardando, error, enviar } = useEnvio();

  function alternar(rol: Rol, marcado: boolean) {
    setRoles((previos) => (marcado ? [...previos, rol] : previos.filter((r) => r !== rol)));
  }

  async function crear() {
    const creado = await enviar(() => usuarios.crear({ nombreCompleto, nombreUsuario, roles, email, telefono, contrasenaInicial }));
    if (creado) alCrear(creado);
  }

  return (
    <Dialog
      open
      title="Nuevo usuario"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="user-plus" loading={guardando} onClick={() => void crear()}>Dar de alta usuario</Button>
        </>
      }
    >
      <div className="usuarios__form">
        {error && <Alert tone="danger">{error.message}</Alert>}
        <Input label="Nombre y apellido" value={nombreCompleto} onChange={(e) => setNombreCompleto(e.target.value)} error={error?.errorDe('nombreCompleto')} required />
        <Input
          label="Usuario"
          hint="Para ingresar. En minúsculas, p. ej. lucia.ferreyra."
          autoComplete="off"
          value={nombreUsuario}
          onChange={(e) => setNombreUsuario(e.target.value.toLowerCase())}
          error={error?.errorDe('nombreUsuario')}
          required
        />
        <Perfiles roles={roles} alternar={alternar} error={error?.errorDe('roles')} />
        <Input label="Correo" optional type="email" inputMode="email" value={email} onChange={(e) => setEmail(e.target.value)} error={error?.errorDe('email')} />
        <Input label="Teléfono" optional type="tel" inputMode="tel" value={telefono} onChange={(e) => setTelefono(e.target.value)} error={error?.errorDe('telefono')} />
        <Input
          label="Contraseña inicial"
          hint="8 caracteres o más. La persona la cambia al ingresar por primera vez."
          type="password"
          autoComplete="new-password"
          value={contrasenaInicial}
          onChange={(e) => setContrasenaInicial(e.target.value)}
          error={error?.errorDe('contrasenaInicial')}
          required
        />
      </div>
    </Dialog>
  );
}

/** Perfiles como casillas: un usuario puede tener varios. */
function Perfiles({ roles, alternar, error, ayuda }: {
  roles: Rol[];
  alternar: (rol: Rol, marcado: boolean) => void;
  error?: string;
  ayuda?: string;
}) {
  return (
    <fieldset className="usuarios__perfiles">
      <legend className="label">Perfiles</legend>
      <p className="body-sm v-muted">{ayuda ?? 'Si tiene más de uno, puede hacer todo lo de cada perfil.'}</p>
      {ROLES.map((rol) => (
        <Checkbox key={rol} label={NOMBRE_DE_ROL[rol]} checked={roles.includes(rol)} onChange={(e) => alternar(rol, e.target.checked)} />
      ))}
      {error && <p className="body-sm v-field__error" role="alert">{error}</p>}
    </fieldset>
  );
}

function DialogoModificar({ usuario, esYo, alCerrar, alGuardar }: {
  usuario: Usuario;
  esYo: boolean;
  alCerrar: () => void;
  alGuardar: (u: Usuario) => void;
}) {
  const [nombreCompleto, setNombreCompleto] = useState(usuario.nombreCompleto);
  const [roles, setRoles] = useState<Rol[]>(usuario.roles);
  const [email, setEmail] = useState(usuario.email ?? '');
  const [telefono, setTelefono] = useState(usuario.telefono ?? '');
  const { guardando, error, enviar } = useEnvio();

  function alternar(rol: Rol, marcado: boolean) {
    setRoles((previos) => (marcado ? [...previos, rol] : previos.filter((r) => r !== rol)));
  }

  async function guardar() {
    const guardado = await enviar(() => usuarios.modificar(usuario.id, { nombreCompleto, roles, email, telefono }));
    if (guardado) alGuardar(guardado);
  }

  return (
    <Dialog
      open
      title="Modificar usuario"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="check" loading={guardando} onClick={() => void guardar()}>Guardar cambios</Button>
        </>
      }
    >
      <div className="usuarios__form">
        {error && !error.errores.length && <Alert tone="danger">{error.message}</Alert>}
        <Input label="Nombre y apellido" value={nombreCompleto} onChange={(e) => setNombreCompleto(e.target.value)} error={error?.errorDe('nombreCompleto')} required />
        <Input label="Usuario" value={usuario.nombreUsuario} readOnly hint="No se cambia: es con lo que ingresa." />
        <Perfiles
          roles={roles}
          alternar={alternar}
          error={error?.errorDe('roles')}
          ayuda={esYo ? 'No podés quitarte tu propio perfil de Dirección o Coordinación.' : undefined}
        />
        <Input label="Correo" optional type="email" inputMode="email" value={email} onChange={(e) => setEmail(e.target.value)} error={error?.errorDe('email')} />
        <Input label="Teléfono" optional type="tel" inputMode="tel" value={telefono} onChange={(e) => setTelefono(e.target.value)} error={error?.errorDe('telefono')} />
      </div>
    </Dialog>
  );
}

function DialogoBaja({ usuario, alCerrar, alConfirmar }: { usuario: Usuario; alCerrar: () => void; alConfirmar: (u: Usuario) => void }) {
  const { guardando, error, enviar } = useEnvio();
  const cargar = useCallback(() => fichas.afectados(usuario.id), [usuario.id]);
  const afectados = useDatos(cargar);

  async function confirmar() {
    const actualizado = await enviar(() => usuarios.darDeBaja(usuario.id));
    if (actualizado) alConfirmar(actualizado);
  }

  return (
    <Dialog
      open
      title="Dar de baja usuario"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button tone="danger" loading={guardando} onClick={() => void confirmar()}>Dar de baja usuario</Button>
        </>
      }
    >
      <div className="usuarios__form">
        {error && <Alert tone="danger">{error.message}</Alert>}
        <p>
          {usuario.nombreCompleto} no va a poder ingresar más al sistema. Lo que registró (eventos, movimientos, historial) se conserva.
        </p>
        <Cargando datos={afectados}>
          {(lista) =>
            lista.length > 0 ? (
              <Alert
                tone="warning"
                icon="triangle-alert"
                title={lista.length === 1 ? 'Tiene 1 evento activo' : `Tiene ${lista.length} eventos activos`}
              >
                <ul className="usuarios__afectados">
                  {lista.map((e) => (
                    <li key={e.id}>
                      {e.nombre} · {e.salon.nombre} · {fechaCorta(e.fecha, true)} · {e.turno.nombre}
                      {' '}({[e.vendedora.id === usuario.id && 'vendedora titular', e.planner?.id === usuario.id && 'planner'].filter(Boolean).join(' y ')})
                    </li>
                  ))}
                </ul>
                Podés darlo de baja igual; esos eventos van a necesitar otra persona.
              </Alert>
            ) : (
              <p className="body-sm v-muted">No tiene eventos activos a su cargo.</p>
            )
          }
        </Cargando>
      </div>
    </Dialog>
  );
}
