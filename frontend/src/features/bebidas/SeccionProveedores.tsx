import { useState } from 'react';
import { bebidas, type Bebida } from '../../api/bebidas';
import { cuitLegible, proveedores, type Proveedor } from '../../api/proveedores';
import { useDatos, useEnvio } from '../../api/useDatos';
import { Alert, Badge, Button, Card, Checkbox, Dialog, Input, Table, type TableColumn } from '../../ds';
import { Cargando } from '../comun/Cargando';

const cargar = () => Promise.all([proveedores.listar(), bebidas.listar()]).then(([lista, catalogo]) => ({ lista, catalogo }));

/**
 * UI-28, paso 2 · Proveedores: alta, modificación, baja lógica y reactivación, con las bebidas que provee cada uno (las
 * que lo tienen como proveedor habitual). Marcar una bebida que tenía otro proveedor se la cambia, y el diálogo lo avisa.
 */
export function SeccionProveedores() {
  const datos = useDatos(cargar);
  const [editando, setEditando] = useState<Proveedor | 'nuevo' | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const estado = useEnvio();

  /** Reemplaza el proveedor y recarga: cambiar bebidas puede sacárselas a otro. */
  function guardado(p: Proveedor, nuevo: boolean) {
    setEditando(null);
    setAviso(nuevo ? `Proveedor cargado: ${p.razonSocial}.` : `Cambios de ${p.razonSocial} guardados.`);
    datos.recargar();
  }

  async function cambiarEstado(p: Proveedor) {
    const actualizado = await estado.enviar(() => (p.activo ? proveedores.darDeBaja(p.id) : proveedores.reactivar(p.id)));
    if (actualizado) {
      setAviso(actualizado.activo ? `${p.razonSocial} volvió a estar activo.` : `${p.razonSocial} quedó dado de baja. Sus bebidas lo conservan como habitual hasta que les elijas otro.`);
      datos.recargar();
    }
  }

  const columnas: TableColumn<Proveedor>[] = [
    {
      key: 'razonSocial',
      header: 'Proveedor',
      render: (p) => (
        <span className="bebidas__nombre">
          {p.razonSocial}
          {!p.activo && <Badge>De baja</Badge>}
        </span>
      ),
    },
    { key: 'cuit', header: 'CUIT', render: (p) => <span className="numeral-sm">{cuitLegible(p.cuit)}</span> },
    { key: 'contacto', header: 'Contacto', render: (p) => [p.telefono, p.email].filter(Boolean).join(' · ') || '—' },
    { key: 'bebidas', header: 'Bebidas que provee', render: (p) => p.bebidas.map((b) => b.nombre).join(', ') || '—' },
    {
      key: 'acciones',
      header: 'Acciones',
      render: (p) => (
        <span className="bebidas__acciones">
          <Button variant="text" size="sm" icon="pencil" onClick={() => setEditando(p)}>Modificar</Button>
          <Button variant="text" size="sm" tone={p.activo ? 'danger' : 'brand'} icon={p.activo ? undefined : 'refresh-cw'} onClick={() => void cambiarEstado(p)}>
            {p.activo ? 'Dar de baja' : 'Reactivar'}
          </Button>
        </span>
      ),
    },
  ];

  return (
    <>
      {aviso && <Alert tone="success">{aviso}</Alert>}
      {estado.error && <Alert tone="danger">{estado.error.message}</Alert>}
      <div className="bebidas__barra">
        <Button icon="plus" onClick={() => setEditando('nuevo')}>Nuevo proveedor</Button>
      </div>
      <Cargando datos={datos}>
        {({ lista, catalogo }) => (
          <>
            <Card flush>
              <Table caption="Proveedores" columns={columnas} rows={lista} empty="Todavía no hay proveedores cargados." />
            </Card>
            {editando && (
              <DialogoProveedor
                proveedor={editando === 'nuevo' ? null : editando}
                catalogo={catalogo}
                alCerrar={() => setEditando(null)}
                alGuardar={(p) => guardado(p, editando === 'nuevo')}
              />
            )}
          </>
        )}
      </Cargando>
    </>
  );
}

function DialogoProveedor({ proveedor, catalogo, alCerrar, alGuardar }: {
  proveedor: Proveedor | null;
  catalogo: Bebida[];
  alCerrar: () => void;
  alGuardar: (p: Proveedor) => void;
}) {
  const [razonSocial, setRazonSocial] = useState(proveedor?.razonSocial ?? '');
  const [cuit, setCuit] = useState(proveedor?.cuit ? cuitLegible(proveedor.cuit) : '');
  const [telefono, setTelefono] = useState(proveedor?.telefono ?? '');
  const [email, setEmail] = useState(proveedor?.email ?? '');
  const [elegidas, setElegidas] = useState<number[]>(proveedor?.bebidas.map((b) => b.id) ?? []);
  /** Si el alta salió y falló lo de las bebidas, el reintento modifica el ya creado en vez de crear otro. */
  const [creado, setCreado] = useState<Proveedor | null>(null);
  const { guardando, error, enviar } = useEnvio();

  // Las activas, más las que ya provee aunque estén dadas de baja.
  const ofrecidas = catalogo.filter((b) => b.activo || elegidas.includes(b.id));
  /** Bebidas marcadas que hoy tienen otro proveedor habitual: se les cambia. */
  const cambian = ofrecidas.filter((b) => elegidas.includes(b.id) && b.proveedorHabitual && b.proveedorHabitual.id !== proveedor?.id);
  const puedeElegirBebidas = !proveedor || proveedor.activo;

  function alternar(id: number, marcada: boolean) {
    setElegidas((previas) => (marcada ? [...previas, id] : previas.filter((x) => x !== id)));
  }

  async function guardar() {
    const datos = { razonSocial, cuit, telefono, email };
    const resultado = await enviar(async () => {
      const existente = proveedor ?? creado;
      const guardado = existente ? await proveedores.modificar(existente.id, datos) : await proveedores.crear(datos);
      setCreado(guardado);
      return puedeElegirBebidas ? proveedores.fijarBebidas(guardado.id, elegidas) : guardado;
    });
    if (resultado) alGuardar(resultado);
  }

  return (
    <Dialog
      open
      title={proveedor ? 'Modificar proveedor' : 'Nuevo proveedor'}
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="check" loading={guardando} onClick={() => void guardar()}>{proveedor ? 'Guardar cambios' : 'Cargar proveedor'}</Button>
        </>
      }
    >
      <div className="bebidas__form">
        {error && !error.errores.length && <Alert tone="danger">{error.message}</Alert>}
        <Input label="Razón social" value={razonSocial} onChange={(e) => setRazonSocial(e.target.value)} error={error?.errorDe('razonSocial')} required />
        <Input
          label="CUIT"
          optional
          inputMode="numeric"
          placeholder="30-71122233-9"
          value={cuit}
          onChange={(e) => setCuit(e.target.value.replace(/[^\d-]/g, ''))}
          error={error?.errorDe('cuit')}
        />
        <Input label="Teléfono" optional type="tel" inputMode="tel" value={telefono} onChange={(e) => setTelefono(e.target.value)} error={error?.errorDe('telefono')} />
        <Input label="Correo" optional type="email" inputMode="email" value={email} onChange={(e) => setEmail(e.target.value)} error={error?.errorDe('email')} />
        <fieldset className="bebidas__codigos">
          <legend className="label">Bebidas que provee <span className="v-muted">(opcional)</span></legend>
          {puedeElegirBebidas ? (
            <>
              <p className="body-sm v-muted">Queda como proveedor habitual de las bebidas que marques.</p>
              <div className="bebidas__provee">
                {ofrecidas.map((b) => (
                  <Checkbox
                    key={b.id}
                    label={`${b.nombre} ${b.presentacion}`}
                    hint={b.proveedorHabitual && b.proveedorHabitual.id !== proveedor?.id ? `Hoy la provee ${b.proveedorHabitual.razonSocial}` : undefined}
                    checked={elegidas.includes(b.id)}
                    onChange={(e) => alternar(b.id, e.target.checked)}
                  />
                ))}
              </div>
              {cambian.length > 0 && (
                <Alert tone="warning" icon="triangle-alert">
                  <ul className="bebidas__saldos">
                    {cambian.map((b) => (
                      <li key={b.id}>{b.nombre} pasa de {b.proveedorHabitual?.razonSocial} a este proveedor.</li>
                    ))}
                  </ul>
                </Alert>
              )}
            </>
          ) : (
            <p className="body-sm v-muted">Está dado de baja: reactivalo para asignarle bebidas.</p>
          )}
        </fieldset>
      </div>
    </Dialog>
  );
}
