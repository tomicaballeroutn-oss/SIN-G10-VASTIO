import { useState } from 'react';
import { fichas, type Ficha, type Modificacion } from '../../api/eventos';
import { useEnvio } from '../../api/useDatos';
import { Actor, Alert, Button, Checkbox, Dialog, Stepper } from '../../ds';
import { fechaHora } from '../comun/formato';

/**
 * UI-14 · Cantidad de invitados: el número y si ya es definitivo (hace falta para confirmar el evento).
 * Superar la capacidad del salón se advierte pero no se impide: hay eventos más grandes que el salón.
 */
export function DialogoInvitados({ ficha, alCerrar, alGuardar }: {
  ficha: Ficha;
  alCerrar: () => void;
  alGuardar: (ficha: Ficha) => void;
}) {
  const confirmado = ficha.estado === 'CONFIRMADO';
  const [cantidad, setCantidad] = useState(ficha.cantidadInvitados ?? 0);
  const [definitivos, setDefinitivos] = useState(ficha.invitadosDefinitivos || confirmado);
  const [faltaCantidad, setFaltaCantidad] = useState(false);
  const { guardando, error, enviar } = useEnvio();

  const ultimoCambio = ficha.historial
    .filter((e): e is Modificacion => e.tipo === 'MODIFICACION' && e.campo === 'cantidad_invitados')
    .at(-1);
  const capacidad = ficha.salon.capacidad;
  const supera = capacidad != null && cantidad > capacidad;

  async function guardar() {
    setFaltaCantidad(cantidad <= 0);
    if (cantidad <= 0) return;
    const nueva = await enviar(() => fichas.registrarInvitados(ficha.id, ficha.version, cantidad, definitivos));
    if (nueva) alGuardar(nueva);
  }

  return (
    <Dialog
      open
      title="Cantidad de invitados"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="users" loading={guardando} onClick={() => void guardar()}>Guardar cantidad</Button>
        </>
      }
    >
      <div className="invitados">
        {error && !error.errores.length && <Alert tone="danger">{error.message}</Alert>}
        <Stepper
          label="Cantidad de invitados"
          value={cantidad}
          onChange={(n) => {
            setCantidad(n);
            setFaltaCantidad(false);
          }}
          step={10}
          max={5000}
          unit="invitados"
          error={faltaCantidad ? 'La cantidad de invitados tiene que ser mayor a 0.' : error?.errorDe('cantidad')}
        />
        {ficha.cantidadInvitados != null && (
          <p className="body-sm v-muted">
            Cantidad anterior: <span className="numeral">{ficha.cantidadInvitados}</span>
            {ficha.invitadosDefinitivos ? ' (definitiva)' : ' (prevista)'}
          </p>
        )}
        {ultimoCambio && (
          <Actor name={ultimoCambio.usuario.nombre} action="la cambió por última vez" at={fechaHora(ultimoCambio.fechaHora)} size="sm" />
        )}
        {supera && (
          <Alert tone="warning" icon="triangle-alert" title={`Supera la capacidad de ${ficha.salon.nombre} (${capacidad} personas)`}>
            Podés guardarla igual si ya lo hablaron con el salón.
          </Alert>
        )}
        <Checkbox
          label="Cantidad definitiva"
          hint={confirmado
            ? 'El evento está confirmado: la cantidad sigue siendo definitiva.'
            : 'Marcala cuando el cliente confirme el número final. Hace falta para confirmar el evento.'}
          checked={definitivos}
          disabled={confirmado}
          onChange={(e) => setDefinitivos(e.target.checked)}
        />
      </div>
    </Dialog>
  );
}
