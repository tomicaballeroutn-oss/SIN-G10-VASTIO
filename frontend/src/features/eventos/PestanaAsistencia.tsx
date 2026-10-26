import { useState } from 'react';
import { fichas, type Asistencia, type Ficha, type Modificacion, type SegmentoAsistencia } from '../../api/eventos';
import { useEnvio } from '../../api/useDatos';
import { Actor, Alert, Button, Card, Dialog, Input, Stat, Table } from '../../ds';
import { fechaHora } from '../comun/formato';

/** Prefijo del historial para los cambios de la asistencia prevista («asistencia_prevista.Entradas anticipadas»). */
export const CAMPO_PREVISTA = 'asistencia_prevista.';

/**
 * UI-19 · Asistencia por segmento de un evento de egresados (docs/sprint-4.md, decisiones 1 a 5). Los invitados son los
 * comensales de la cena; los demás segmentos suman el público de la fiesta posterior. Sobre el total se dimensiona la
 * bebida.
 */
export function PestanaAsistencia({ ficha, asistencia, alGuardar }: {
  ficha: Ficha;
  asistencia: Asistencia;
  alGuardar: (ficha: Ficha) => void;
}) {
  const [dialogo, setDialogo] = useState(false);
  const [aviso, setAviso] = useState<string>();
  const ultimo = ficha.historial
    .filter((e): e is Modificacion => e.tipo === 'MODIFICACION' && e.campo.startsWith(CAMPO_PREVISTA))
    .at(-1);

  return (
    <Card
      title="Asistencia prevista"
      subtitle="Los invitados son los comensales de la cena; los demás segmentos, el público de la fiesta."
      actions={ficha.acciones.registrarAsistenciaPrevista && (
        <Button variant="outline" size="sm" icon="users" onClick={() => setDialogo(true)}>Registrar asistencia prevista</Button>
      )}
    >
      <div className="asistencia">
        {aviso && <Alert tone="success">{aviso}</Alert>}
        <Table<SegmentoAsistencia>
          caption="Asistencia prevista por segmento"
          columns={[
            { key: 'nombre', header: 'Segmento', render: (s) => nombreSegmento(s) },
            { key: 'prevista', header: 'Previsto', numeric: true, render: (s) => s.prevista ?? '—' },
          ]}
          rows={asistencia.segmentos}
        />
        <Stat label="Total previsto" value={asistencia.totalPrevisto ?? '—'} unit={asistencia.totalPrevisto != null ? 'personas' : undefined} />
        {ultimo && <Actor name={ultimo.usuario.nombre} action="la cambió por última vez" at={fechaHora(ultimo.fechaHora)} size="sm" />}
      </div>
      {dialogo && (
        <DialogoAsistenciaPrevista
          ficha={ficha}
          asistencia={asistencia}
          alCerrar={() => setDialogo(false)}
          alGuardar={(nueva) => {
            setDialogo(false);
            setAviso('Asistencia prevista guardada.');
            alGuardar(nueva);
          }}
        />
      )}
    </Card>
  );
}

function nombreSegmento(s: SegmentoAsistencia): string {
  if (!s.activo) return `${s.nombre} (dado de baja)`;
  return s.equivaleInvitados ? `${s.nombre} (los invitados)` : s.nombre;
}

/** Entero de 0 a 5000, o vacío. */
function valido(texto: string): boolean {
  return texto === '' || (/^\d{1,4}$/.test(texto) && Number(texto) <= 5000);
}

function DialogoAsistenciaPrevista({ ficha, asistencia, alCerrar, alGuardar }: {
  ficha: Ficha;
  asistencia: Asistencia;
  alCerrar: () => void;
  alGuardar: (ficha: Ficha) => void;
}) {
  const editables = asistencia.segmentos.filter((s) => s.activo && !s.equivaleInvitados);
  const comensales = asistencia.segmentos.find((s) => s.equivaleInvitados);
  const [valores, setValores] = useState<Record<number, string>>(() =>
    Object.fromEntries(editables.map((s) => [s.id, s.prevista == null ? '' : String(s.prevista)])),
  );
  const [revisar, setRevisar] = useState(false);
  const { guardando, error, enviar } = useEnvio();

  const total = (comensales?.prevista ?? 0) + editables.reduce((suma, s) => suma + (Number(valores[s.id]) || 0), 0);

  async function guardar() {
    const invalidos = editables.some((s) => !valido(valores[s.id]));
    setRevisar(invalidos);
    if (invalidos) return;
    const segmentos = editables.map((s) => ({ segmentoId: s.id, cantidad: valores[s.id] === '' ? null : Number(valores[s.id]) }));
    const nueva = await enviar(() => fichas.registrarAsistenciaPrevista(ficha.id, ficha.version, segmentos));
    if (nueva) alGuardar(nueva);
  }

  return (
    <Dialog
      open
      title="Asistencia prevista"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="users" loading={guardando} onClick={() => void guardar()}>Guardar asistencia</Button>
        </>
      }
    >
      <div className="asistencia">
        {error && <Alert tone="danger">{error.message}</Alert>}
        {comensales && (
          <p className="body-sm v-muted">
            {comensales.nombre}: <span className="numeral">{comensales.prevista ?? 'sin definir'}</span>. Es la cantidad de
            invitados; se cambia desde «Cantidad de invitados».
          </p>
        )}
        {editables.map((s) => (
          <Input
            key={s.id}
            label={s.nombre}
            inputMode="numeric"
            optional
            value={valores[s.id]}
            onChange={(e) => setValores((previos) => ({ ...previos, [s.id]: e.target.value.trim() }))}
            error={revisar && !valido(valores[s.id]) ? 'Escribí un número entero de 0 a 5000, o dejalo vacío.' : undefined}
          />
        ))}
        <Stat label="Total previsto" value={total} unit="personas" />
      </div>
    </Dialog>
  );
}
