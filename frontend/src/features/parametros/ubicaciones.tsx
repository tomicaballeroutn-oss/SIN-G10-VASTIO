import { useCallback, useState, type FormEvent } from 'react';
import { catalogos, type Salon } from '../../api/catalogos';
import { NOMBRE_DE_TIPO, ubicaciones, type TipoUbicacion, type Ubicacion } from '../../api/ubicaciones';
import { useDatos, useEnvio } from '../../api/useDatos';
import { Alert, Button, Input, Select, Switch } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { ListaYEdicion } from './ListaYEdicion';
import { reemplazar } from './lista';

const GRUPO: Record<TipoUbicacion, string> = {
  DEPOSITO: 'Depósito madre',
  TRANSICION: 'Depósitos de transición',
  BARRA: 'Barras',
};

const cargar = () => Promise.all([ubicaciones.listar(), catalogos.salones()]).then(([lista, salones]) => ({ lista, salones }));

/** «Avril · se abastece de Depósito principal · con retiro directo». */
function detalle(u: Ubicacion): string {
  return [
    u.salon?.nombre,
    u.abastecimiento && `se abastece de ${u.abastecimiento.nombre}`,
    u.permiteRetiroDirecto && 'con retiro directo del depósito madre',
  ].filter(Boolean).join(' · ');
}

/**
 * UI-26 · Ubicaciones de stock, como sección de Parámetros (la ven los mismos perfiles que las configuran): depósito
 * madre, depósitos de transición y barras. Cada barra dice desde dónde se abastece; si es una transición, puede tener
 * habilitado el retiro directo del depósito madre como contingencia.
 */
export function SeccionUbicaciones() {
  const datos = useDatos(cargar);
  const fijar = useCallback(
    (u: Ubicacion) => datos.fijar((previos) => ({ salones: previos?.salones ?? [], lista: reemplazar(previos?.lista, u) })),
    [datos],
  );
  return (
    <Cargando datos={datos}>
      {({ lista, salones }) => (
        <ListaYEdicion<Ubicacion>
          items={lista}
          grupo={(u) => GRUPO[u.tipo]}
          fila={(u) => (
            <span className="catalogo__texto">
              <span className="label">{u.nombre}</span>
              {detalle(u) && <span className="body-sm v-muted">{detalle(u)}</span>}
            </span>
          )}
          etiqueta={(u) => u.nombre}
          inactivo={(u) => !u.activo}
          agregar="Agregar ubicación"
          titulo={(u) => (u ? `Editar ${NOMBRE_DE_TIPO[u.tipo].toLowerCase()}` : 'Nueva ubicación')}
          alGuardar={fijar}
          formulario={(u, listo) => <FormularioUbicacion ubicacion={u} todas={lista} salones={salones} listo={listo} />}
        />
      )}
    </Cargando>
  );
}

function FormularioUbicacion({ ubicacion, todas, salones, listo }: {
  ubicacion: Ubicacion | undefined;
  todas: Ubicacion[];
  salones: Salon[];
  listo: (u: Ubicacion) => void;
}) {
  const hayDeposito = todas.some((u) => u.tipo === 'DEPOSITO' && u.activo);
  const [tipo, setTipo] = useState<TipoUbicacion | ''>(ubicacion?.tipo ?? '');
  const [nombre, setNombre] = useState(ubicacion?.nombre ?? '');
  const [salonId, setSalonId] = useState(ubicacion?.salon ? String(ubicacion.salon.id) : '');
  const deposito = todas.find((u) => u.tipo === 'DEPOSITO' && u.activo);
  const [origenId, setOrigenId] = useState(String(ubicacion?.abastecimiento?.id ?? deposito?.id ?? ''));
  const [retiroDirecto, setRetiroDirecto] = useState(ubicacion?.permiteRetiroDirecto ?? false);
  const guardado = useEnvio();
  const estado = useEnvio();
  const error = guardado.error ?? estado.error;

  const origenes = todas.filter((u) => u.activo && (u.tipo === 'DEPOSITO' || u.tipo === 'TRANSICION'));
  const origen = origenes.find((u) => String(u.id) === origenId);
  // Un salón dado de baja solo aparece si ya es el de esta ubicación.
  const opcionesDeSalon = salones
    .filter((s) => s.activo || s.id === ubicacion?.salon?.id)
    .map((s) => ({ value: String(s.id), label: s.activo ? s.nombre : `${s.nombre} (dado de baja)` }));

  async function guardar(e: FormEvent) {
    e.preventDefault();
    const datos = {
      nombre,
      tipo: tipo || undefined,
      salonId: salonId ? Number(salonId) : null,
      abastecimientoId: tipo === 'BARRA' && origenId ? Number(origenId) : null,
      permiteRetiroDirecto: tipo === 'BARRA' && origen?.tipo === 'TRANSICION' && retiroDirecto,
    };
    const resultado = await guardado.enviar(() => (ubicacion ? ubicaciones.modificar(ubicacion.id, datos) : ubicaciones.crear(datos)));
    if (resultado) listo(resultado);
  }

  async function cambiarEstado(u: Ubicacion) {
    const resultado = await estado.enviar(() => (u.activo ? ubicaciones.darDeBaja(u.id) : ubicaciones.reactivar(u.id)));
    if (resultado) listo(resultado);
  }

  return (
    <form className="catalogo__form" onSubmit={(e) => void guardar(e)} noValidate>
      {error && !error.errores.length && <Alert tone="danger">{error.message}</Alert>}
      {ubicacion ? (
        <p className="body-sm v-muted">{NOMBRE_DE_TIPO[ubicacion.tipo]}. El tipo no se cambia: si hace falta otro, dala de baja y creá una nueva.</p>
      ) : (
        <Select
          label="Tipo"
          placeholder="Elegí el tipo"
          options={(['DEPOSITO', 'TRANSICION', 'BARRA'] as const)
            .filter((t) => t !== 'DEPOSITO' || !hayDeposito)
            .map((t) => ({ value: t, label: NOMBRE_DE_TIPO[t] }))}
          value={tipo}
          onChange={(e) => setTipo(e.target.value as TipoUbicacion)}
          hint={hayDeposito ? 'Ya hay un depósito madre: solo puede haber uno activo.' : undefined}
          required
        />
      )}
      <Input label="Nombre" placeholder="Por ejemplo, Barra Avril" value={nombre} onChange={(e) => setNombre(e.target.value)} error={error?.errorDe('nombre')} required />
      {(tipo === 'BARRA' || tipo === 'TRANSICION') && (
        <Select
          label="Salón"
          optional={tipo === 'TRANSICION'}
          placeholder={tipo === 'BARRA' ? 'Elegí el salón' : undefined}
          options={[...(tipo === 'TRANSICION' ? [{ value: '', label: 'Sin salón' }] : []), ...opcionesDeSalon]}
          value={salonId}
          onChange={(e) => setSalonId(e.target.value)}
          hint={tipo === 'BARRA' ? 'Una barra por salón; Avril, hasta dos.' : undefined}
          required={tipo === 'BARRA'}
        />
      )}
      {tipo === 'BARRA' && (
        <>
          <Select
            label="Se abastece desde"
            options={origenes.map((u) => ({ value: String(u.id), label: `${u.nombre} (${NOMBRE_DE_TIPO[u.tipo].toLowerCase()})` }))}
            value={origenId}
            onChange={(e) => setOrigenId(e.target.value)}
            hint="De donde salen normalmente las entregas a esta barra."
          />
          {origen?.tipo === 'TRANSICION' && (
            <Switch
              label="Permitir retiro directo del depósito madre"
              hint="Para cuando la operación lo requiera: la barra puede retirar directo del depósito madre, sin pasar por la transición."
              checked={retiroDirecto}
              onChange={(e) => setRetiroDirecto(e.target.checked)}
            />
          )}
        </>
      )}
      <div className="catalogo__acciones ubicaciones__acciones">
        {ubicacion && ubicacion.tipo !== 'DEPOSITO' && (
          <Button variant="outline" tone={ubicacion.activo ? 'danger' : 'brand'} loading={estado.guardando} onClick={() => void cambiarEstado(ubicacion)}>
            {ubicacion.activo ? 'Dar de baja' : 'Reactivar'}
          </Button>
        )}
        <Button type="submit" loading={guardado.guardando}>{ubicacion ? 'Guardar cambios' : 'Agregar ubicación'}</Button>
      </div>
    </form>
  );
}
