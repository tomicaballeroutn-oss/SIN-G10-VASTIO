import { fichas, type Ficha } from '../../api/eventos';
import { useEnvio } from '../../api/useDatos';
import { Alert, Button, Dialog } from '../../ds';
import { fechaCorta } from '../comun/formato';

/**
 * UI-10 · Liberar pre-reserva: la fecha vuelve a la agenda y queda en el historial como liberación.
 * No es una cancelación.
 */
export function DialogoLiberar({ ficha, alCerrar, alLiberar }: {
  ficha: Ficha;
  alCerrar: () => void;
  alLiberar: (ficha: Ficha) => void;
}) {
  const { guardando, error, enviar } = useEnvio();

  async function liberar() {
    const nueva = await enviar(() => fichas.liberar(ficha.id));
    if (nueva) alLiberar(nueva);
  }

  return (
    <Dialog
      open
      title="Liberar pre-reserva"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="lock-open" loading={guardando} onClick={() => void liberar()}>Liberar pre-reserva</Button>
        </>
      }
    >
      {error && <Alert tone="danger">{error.message}</Alert>}
      <p className="body-lg">
        La fecha vuelve a estar disponible en la agenda y queda registrada en el historial del evento como liberación.
      </p>
      <p className="body-sm v-muted">
        {ficha.nombre} · {ficha.salon.nombre} · {fechaCorta(ficha.fecha, true)} · {ficha.turno.nombre}
      </p>
    </Dialog>
  );
}
