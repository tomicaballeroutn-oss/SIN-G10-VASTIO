import { fichas, type Ficha } from '../../api/eventos';
import { useEnvio } from '../../api/useDatos';
import { Alert, Button, Dialog, Icon } from '../../ds';

/**
 * UI-15 · Confirmar evento: lista los requisitos (planner, invitados definitivos, servicios requeridos, fecha).
 * Si falta alguno, el botón queda deshabilitado y la fila dice qué falta, no solo se atenúa.
 */
export function DialogoConfirmar({ ficha, alCerrar, alConfirmar }: {
  ficha: Ficha;
  alCerrar: () => void;
  alConfirmar: (ficha: Ficha) => void;
}) {
  const requisitos = ficha.requisitosConfirmacion ?? [];
  const listo = requisitos.length > 0 && requisitos.every((r) => r.cumplido);
  const { guardando, error, enviar } = useEnvio();

  async function confirmar() {
    const nueva = await enviar(() => fichas.confirmar(ficha.id));
    if (nueva) alConfirmar(nueva);
  }

  return (
    <Dialog
      open
      title="Confirmar evento"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="circle-check" loading={guardando} disabled={!listo} onClick={() => void confirmar()}>Confirmar evento</Button>
        </>
      }
    >
      <div className="confirmar">
        {error && <Alert tone="danger">{error.message}</Alert>}
        <ul className="confirmar__requisitos" aria-label="Requisitos para confirmar">
          {requisitos.map((r) => (
            <li key={r.codigo} className={r.cumplido ? 'confirmar__requisito' : 'confirmar__requisito is-pendiente'}>
              <Icon name={r.cumplido ? 'circle-check' : 'circle-alert'} size={20} />
              <span className="body">{r.titulo}</span>
              <span className="body-sm">
                <span className="v-sr">{r.cumplido ? 'Listo: ' : 'Pendiente: '}</span>
                {r.detalle}
              </span>
            </li>
          ))}
        </ul>
        <p className="body-sm v-muted">
          {listo
            ? 'Al confirmar se avisa a Compras y Cocina, y ya se puede preparar la bebida.'
            : 'Completá lo que falta para poder confirmar el evento.'}
        </p>
      </div>
    </Dialog>
  );
}
