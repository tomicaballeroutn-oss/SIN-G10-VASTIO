import { useState } from 'react';
import { catalogos } from '../../api/catalogos';
import { fichas, type Ficha } from '../../api/eventos';
import { useDatos, useEnvio } from '../../api/useDatos';
import { Alert, Button, Dialog, Input, Select } from '../../ds';
import { Cargando } from '../comun/Cargando';

/** «Otro» pide contar qué pasó (mismo criterio que el backend). */
function pideDetalle(nombre: string | undefined): boolean {
  return ['otro', 'otra'].includes((nombre ?? '').trim().toLowerCase());
}

/**
 * UI-17 · Cancelar evento: motivo de la lista (Parámetros) y detalle, obligatorio si el motivo es «Otro».
 * La fecha vuelve a estar disponible y se avisa a todas las áreas.
 */
export function DialogoCancelar({ ficha, alCerrar, alCancelar }: {
  ficha: Ficha;
  alCerrar: () => void;
  alCancelar: (ficha: Ficha) => void;
}) {
  const motivos = useDatos(catalogos.motivos);
  const [motivoId, setMotivoId] = useState('');
  const [detalle, setDetalle] = useState('');
  const [faltan, setFaltan] = useState<{ motivo?: string; detalle?: string }>({});
  const { guardando, error, enviar } = useEnvio();

  const deCancelacion = (motivos.datos ?? []).filter((m) => m.ambito === 'CANCELACION' && m.activo);
  const elegido = deCancelacion.find((m) => String(m.id) === motivoId);
  const detalleObligatorio = pideDetalle(elegido?.nombre);

  async function cancelar() {
    const pendientes = {
      motivo: motivoId ? undefined : 'Elegí un motivo.',
      detalle: detalleObligatorio && !detalle.trim() ? `Contanos qué pasó: el motivo es «${elegido?.nombre}».` : undefined,
    };
    setFaltan(pendientes);
    if (pendientes.motivo || pendientes.detalle) return;
    const nueva = await enviar(() => fichas.cancelar(ficha.id, Number(motivoId), detalle));
    if (nueva) alCancelar(nueva);
  }

  const errorDetalle = faltan.detalle ?? error?.errorDe('detalle') ?? (error?.codigo === 'FALTA_DETALLE' ? error.message : undefined);
  const errorGeneral = error && !error.errores.length && error.codigo !== 'FALTA_DETALLE' ? error.message : undefined;

  return (
    <Dialog
      open
      title="Cancelar evento"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Volver</Button>
          <Button tone="danger" icon="calendar-x" loading={guardando} onClick={() => void cancelar()}>Cancelar evento</Button>
        </>
      }
    >
      <div className="cancelar">
        <p className="body-sm v-muted">{ficha.nombre} · {ficha.salon.nombre} · {ficha.turno.nombre}</p>
        {errorGeneral && <Alert tone="danger">{errorGeneral}</Alert>}
        <Cargando datos={motivos}>
          {() => (
            <Select
              label="Motivo"
              placeholder="Elegí un motivo"
              options={deCancelacion.map((m) => ({ value: String(m.id), label: m.nombre }))}
              value={motivoId}
              onChange={(e) => {
                setMotivoId(e.target.value);
                setFaltan({});
              }}
              error={faltan.motivo ?? error?.errorDe('motivoId') ?? (error?.codigo === 'MOTIVO_INVALIDO' ? error.message : undefined)}
              required
            />
          )}
        </Cargando>
        <Input
          label="Detalle"
          optional={!detalleObligatorio}
          required={detalleObligatorio}
          multiline
          rows={3}
          placeholder="Contanos qué pasó"
          value={detalle}
          onChange={(e) => setDetalle(e.target.value)}
          error={errorDetalle}
        />
        <p className="body-sm">La fecha vuelve a estar disponible y se avisa a las áreas.</p>
      </div>
    </Dialog>
  );
}
