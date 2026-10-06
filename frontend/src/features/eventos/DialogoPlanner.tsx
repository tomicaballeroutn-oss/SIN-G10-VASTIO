import { useCallback, useState } from 'react';
import { fichas, type Ficha } from '../../api/eventos';
import { useDatos, useEnvio } from '../../api/useDatos';
import { Alert, Button, Dialog, Select } from '../../ds';
import { Cargando } from '../comun/Cargando';

/** Valor del Select para «sin planner». */
const SIN_PLANNER = 'ninguna';

/**
 * UI-16 · Asignar planner: la responsable de la jornada. Si ya tiene otro evento esa fecha se advierte y se puede
 * asignar igual. Quitarla solo se puede antes de confirmar.
 */
export function DialogoPlanner({ ficha, alCerrar, alAsignar }: {
  ficha: Ficha;
  alCerrar: () => void;
  alAsignar: (ficha: Ficha) => void;
}) {
  const cargar = useCallback(() => fichas.planners(ficha.id), [ficha.id]);
  const candidatas = useDatos(cargar);
  const [elegida, setElegida] = useState(ficha.planner ? String(ficha.planner.id) : '');
  const { guardando, error, enviar } = useEnvio();
  const puedeQuitar = ficha.estado === 'CONTRATADO' && ficha.planner !== null;

  async function asignar() {
    const nueva = await enviar(() => fichas.asignarPlanner(ficha.id, elegida === SIN_PLANNER ? null : Number(elegida)));
    if (nueva) alAsignar(nueva);
  }

  const quitando = elegida === SIN_PLANNER;
  const sinCambios = elegida === '' || elegida === String(ficha.planner?.id ?? '');

  return (
    <Dialog
      open
      title="Asignar planner"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button
            icon={quitando ? 'x' : 'user-plus'}
            tone={quitando ? 'danger' : undefined}
            loading={guardando}
            disabled={sinCambios}
            onClick={() => void asignar()}
          >
            {quitando ? 'Quitar planner' : 'Asignar planner'}
          </Button>
        </>
      }
    >
      <div className="planner">
        {error && <Alert tone="danger">{error.message}</Alert>}
        <Cargando datos={candidatas}>
          {(lista) => {
            const seleccionada = lista.find((c) => String(c.id) === elegida);
            return (
              <>
                <Select
                  label="Planner"
                  placeholder="Elegí una planner"
                  options={[
                    ...lista.map((c) => ({ value: String(c.id), label: c.nombre })),
                    ...(puedeQuitar ? [{ value: SIN_PLANNER, label: 'Sin planner' }] : []),
                  ]}
                  value={elegida}
                  onChange={(e) => setElegida(e.target.value)}
                  hint={lista.length === 0 ? 'No hay planners activas. Dalas de alta en Usuarios.' : undefined}
                />
                {seleccionada && seleccionada.otrosEventos.length > 0 && seleccionada.id !== ficha.planner?.id && (
                  <Alert tone="warning" icon="triangle-alert" title={`${seleccionada.nombre} ya tiene otro evento esa jornada`}>
                    {seleccionada.otrosEventos.join(' · ')}. Podés asignarla igual si te parece bien.
                  </Alert>
                )}
              </>
            );
          }}
        </Cargando>
      </div>
    </Dialog>
  );
}
