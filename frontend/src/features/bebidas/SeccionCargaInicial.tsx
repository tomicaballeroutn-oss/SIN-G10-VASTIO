import { useCallback, useState } from 'react';
import { bebidas, presentacionDeBulto, type Bebida } from '../../api/bebidas';
import { inventarioInicial, type CargaInicial } from '../../api/inventarioInicial';
import { NOMBRE_DE_TIPO, ubicaciones } from '../../api/ubicaciones';
import { useDatos, useEnvio } from '../../api/useDatos';
import { Actor, Alert, Button, CantidadEnCajas, Card, Select, cantidadLegible } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaHora } from '../comun/formato';

const cargarBase = () => Promise.all([ubicaciones.listar(), bebidas.listar()]).then(([lista, catalogo]) => ({
  ubicaciones: lista.filter((u) => u.activo),
  catalogo,
}));

/**
 * UI-28, paso 1 · Carga inicial de inventario por ubicación (depósito madre, transiciones y barras), en cajas y
 * botellas sueltas. Se guarda las veces que haga falta para retomarla; lo que cambia queda como corrección. Si la
 * ubicación ya tiene otros movimientos, se muestra en solo lectura y se corrige con un recuento.
 */
export function SeccionCargaInicial({ alSeguir }: { alSeguir: () => void }) {
  const base = useDatos(cargarBase);
  const [ubicacionId, setUbicacionId] = useState('');
  return (
    <Cargando datos={base}>
      {({ ubicaciones: lista, catalogo }) => (
        <>
          <p className="overline v-muted">Paso 1 de 2</p>
          <Select
            label="Ubicación"
            placeholder="Elegí una ubicación"
            className="bebidas__ubicacion"
            options={lista.map((u) => ({ value: String(u.id), label: `${u.nombre} (${NOMBRE_DE_TIPO[u.tipo].toLowerCase()})` }))}
            value={ubicacionId}
            onChange={(e) => setUbicacionId(e.target.value)}
          />
          {ubicacionId ? (
            <CargaDeUbicacion key={ubicacionId} ubicacionId={Number(ubicacionId)} catalogo={catalogo} alSeguir={alSeguir} />
          ) : (
            <p className="body-sm v-muted">
              Elegí una ubicación para cargar lo que tiene hoy. El avance se guarda por ubicación, así podés retomarlo después.
            </p>
          )}
        </>
      )}
    </Cargando>
  );
}

function CargaDeUbicacion({ ubicacionId, catalogo, alSeguir }: { ubicacionId: number; catalogo: Bebida[]; alSeguir: () => void }) {
  const cargar = useCallback(() => inventarioInicial.consultar(ubicacionId), [ubicacionId]);
  const datos = useDatos(cargar);
  return (
    <Cargando datos={datos}>
      {(carga) => (
        <Formulario
          carga={carga}
          catalogo={catalogo}
          alGuardar={(nueva) => datos.fijar(() => nueva)}
          alSeguir={alSeguir}
        />
      )}
    </Cargando>
  );
}

function Formulario({ carga, catalogo, alGuardar, alSeguir }: {
  carga: CargaInicial;
  catalogo: Bebida[];
  alGuardar: (c: CargaInicial) => void;
  alSeguir: () => void;
}) {
  const cargado = new Map(carga.renglones.map((r) => [r.bebidaId, r.cantidad]));
  const [valores, setValores] = useState<Map<number, number>>(() => new Map(cargado));
  const [aviso, setAviso] = useState<string | null>(null);
  const { guardando, error, enviar } = useEnvio();

  // Las activas, más las dadas de baja que ya tienen algo cargado (se pueden corregir).
  const filas = catalogo.filter((b) => b.activo || (cargado.get(b.id) ?? 0) !== 0);
  const cambios = filas
    .filter((b) => (valores.get(b.id) ?? 0) !== (cargado.get(b.id) ?? 0))
    .map((b) => ({ bebidaId: b.id, cantidad: valores.get(b.id) ?? 0 }));

  async function guardar(): Promise<boolean> {
    if (cambios.length === 0) return true;
    const nueva = await enviar(() => inventarioInicial.guardar(carga.ubicacion.id, cambios));
    if (!nueva) return false;
    alGuardar(nueva);
    setAviso(`Carga inicial de ${nueva.ubicacion.nombre} guardada.`);
    return true;
  }

  async function seguir() {
    if (await guardar()) alSeguir();
  }

  if (carga.cerrada) {
    return (
      <>
        <Alert tone="info" icon="info">
          La carga inicial de {carga.ubicacion.nombre} ya está cerrada porque tiene otros movimientos. Para corregir un saldo, registrá un recuento.
        </Alert>
        <Card flush>
          <ul className="bebidas__carga">
            {catalogo.filter((b) => (cargado.get(b.id) ?? 0) !== 0).map((b) => (
              <li key={b.id} className="bebidas__carga-fila">
                <span>{b.nombre} {b.presentacion}</span>
                <span className="numeral-sm">{cantidadLegible(cargado.get(b.id) ?? 0, b.unidadesPorBulto, b.unidad.nombre)}</span>
              </li>
            ))}
          </ul>
        </Card>
      </>
    );
  }

  return (
    <>
      {aviso && <Alert tone="success">{aviso}</Alert>}
      {error && <Alert tone="danger">{error.message}</Alert>}
      {carga.usuario && carga.ultimaModificacion && (
        <Actor name={carga.usuario.nombre} action="guardó la carga" at={fechaHora(carga.ultimaModificacion)} size="sm" />
      )}
      <Card flush>
        <ul className="bebidas__carga">
          {filas.map((b) => (
            <li key={b.id} className="bebidas__carga-fila">
              <span className="bebidas__carga-bebida">
                <span className="label">{b.nombre}</span>
                <span className="body-sm v-muted">{b.presentacion} · {presentacionDeBulto(b)}{!b.activo && ' · dada de baja'}</span>
              </span>
              <CantidadEnCajas
                label={`Cantidad inicial de ${b.nombre}`}
                value={valores.get(b.id) ?? 0}
                onChange={(n) => {
                  setValores((previos) => new Map(previos).set(b.id, n));
                  setAviso(null);
                }}
                porBulto={b.unidadesPorBulto}
                unidad={b.unidad.nombre}
                className="bebidas__carga-cantidad"
              />
            </li>
          ))}
        </ul>
      </Card>
      <p className="body-sm v-muted">
        {cambios.length > 0
          ? `Tenés ${cambios.length === 1 ? '1 cambio' : `${cambios.length} cambios`} sin guardar. Guardalos antes de cambiar de ubicación.`
          : 'Lo que cambies después de guardar queda registrado como corrección de la carga inicial.'}
      </p>
      <div className="bebidas__acciones-carga">
        <Button variant="outline" loading={guardando} disabled={cambios.length === 0} onClick={() => void guardar()}>Guardar y continuar después</Button>
        <Button iconEnd="arrow-right" onClick={() => void seguir()}>Siguiente: proveedores</Button>
      </div>
    </>
  );
}
