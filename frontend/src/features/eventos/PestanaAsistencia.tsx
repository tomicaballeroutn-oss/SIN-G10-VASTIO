import { useState } from 'react';
import { fichas, type Asistencia, type Ficha, type Modificacion, type SegmentoAsistencia } from '../../api/eventos';
import { useEnvio } from '../../api/useDatos';
import { Actor, Alert, Button, Card, Dialog, Input, Stat, Table } from '../../ds';
import { fechaHora } from '../comun/formato';

/** Prefijos del historial («asistencia_prevista.Entradas anticipadas», «asistencia_real.Venta en puerta»). */
export const CAMPO_PREVISTA = 'asistencia_prevista.';
export const CAMPO_REAL = 'asistencia_real.';

/**
 * UI-19 y UI-20 · Asistencia por segmento de un evento de egresados (docs/sprint-4.md, decisiones 1 a 8). Los invitados
 * son los comensales de la cena; los demás segmentos suman el público de la fiesta posterior. Sobre el total previsto
 * se dimensiona la bebida; con la real completa se calcula el consumo por asistente.
 */
export function PestanaAsistencia({ ficha, asistencia, alGuardar }: {
  ficha: Ficha;
  asistencia: Asistencia;
  alGuardar: (ficha: Ficha) => void;
}) {
  const [dialogo, setDialogo] = useState<'prevista' | 'real' | null>(null);
  const [aviso, setAviso] = useState<string>();
  const ultimo = (prefijo: string) => ficha.historial
    .filter((e): e is Modificacion => e.tipo === 'MODIFICACION' && e.campo.startsWith(prefijo))
    .at(-1);
  const ultimaPrevista = ultimo(CAMPO_PREVISTA);
  const ultimaReal = ultimo(CAMPO_REAL);
  const terminada = ficha.estado === 'REALIZADO' || ficha.estado === 'CERRADO';
  const { acciones } = ficha;

  function guardada(nueva: Ficha, mensaje: string) {
    setDialogo(null);
    setAviso(mensaje);
    alGuardar(nueva);
  }

  return (
    <Card
      title="Asistencia por segmento"
      subtitle="Los invitados son los comensales de la cena; los demás segmentos, el público de la fiesta."
      actions={(acciones.registrarAsistenciaPrevista || acciones.registrarAsistenciaReal) && (
        <div className="asistencia__acciones">
          {acciones.registrarAsistenciaPrevista && (
            <Button variant="outline" size="sm" icon="users" onClick={() => setDialogo('prevista')}>Registrar asistencia prevista</Button>
          )}
          {acciones.registrarAsistenciaReal && (
            <Button size="sm" icon="clipboard-check" onClick={() => setDialogo('real')}>Registrar asistencia real</Button>
          )}
        </div>
      )}
    >
      <div className="asistencia">
        {aviso && <Alert tone="success">{aviso}</Alert>}
        {terminada && asistencia.totalReal == null && (
          <Alert tone="warning" title="Falta la asistencia real">
            Mientras no se cargue la de todos los segmentos, el consumo por asistente se calcula sobre los invitados.
          </Alert>
        )}
        <Table<SegmentoAsistencia>
          caption="Asistencia por segmento"
          columns={[
            { key: 'nombre', header: 'Segmento', render: (s) => nombreSegmento(s) },
            { key: 'prevista', header: 'Previsto', numeric: true, render: (s) => s.prevista ?? '—' },
            { key: 'real', header: 'Real', numeric: true, render: (s) => s.real ?? '—' },
          ]}
          rows={asistencia.segmentos}
        />
        <div className="asistencia__totales">
          <Stat label="Total previsto" value={asistencia.totalPrevisto ?? '—'} unit={asistencia.totalPrevisto != null ? 'personas' : undefined} />
          <Stat label="Total real" value={asistencia.totalReal ?? '—'} unit={asistencia.totalReal != null ? 'personas' : undefined} />
        </div>
        {ultimaPrevista && <Actor name={ultimaPrevista.usuario.nombre} action="cambió la prevista" at={fechaHora(ultimaPrevista.fechaHora)} size="sm" />}
        {ultimaReal && <Actor name={ultimaReal.usuario.nombre} action="cargó la real" at={fechaHora(ultimaReal.fechaHora)} size="sm" />}
      </div>
      {dialogo === 'prevista' && (
        <DialogoAsistenciaPrevista
          ficha={ficha}
          asistencia={asistencia}
          alCerrar={() => setDialogo(null)}
          alGuardar={(nueva) => guardada(nueva, 'Asistencia prevista guardada.')}
        />
      )}
      {dialogo === 'real' && (
        <DialogoAsistenciaReal
          ficha={ficha}
          asistencia={asistencia}
          alCerrar={() => setDialogo(null)}
          alGuardar={(nueva) => guardada(nueva, 'Asistencia real guardada. El consumo por asistente se calcula sobre ella.')}
        />
      )}
    </Card>
  );
}

function nombreSegmento(s: SegmentoAsistencia): string {
  if (!s.activo) return `${s.nombre} (dado de baja)`;
  return s.equivaleInvitados ? `${s.nombre} (los invitados)` : s.nombre;
}

/** Entero de 0 a 5000. */
function entero(texto: string): boolean {
  return /^\d{1,4}$/.test(texto) && Number(texto) <= 5000;
}

/** Los valores en edición, por segmento. */
function valoresDe(segmentos: SegmentoAsistencia[], de: (s: SegmentoAsistencia) => number | null): Record<number, string> {
  return Object.fromEntries(segmentos.map((s) => [s.id, de(s) == null ? '' : String(de(s))]));
}

function sumar(segmentos: SegmentoAsistencia[], valores: Record<number, string>): number {
  return segmentos.reduce((suma, s) => suma + (Number(valores[s.id]) || 0), 0);
}

function DialogoAsistenciaPrevista({ ficha, asistencia, alCerrar, alGuardar }: {
  ficha: Ficha;
  asistencia: Asistencia;
  alCerrar: () => void;
  alGuardar: (ficha: Ficha) => void;
}) {
  const editables = asistencia.segmentos.filter((s) => s.activo && !s.equivaleInvitados);
  const comensales = asistencia.segmentos.find((s) => s.equivaleInvitados);
  const [valores, setValores] = useState(() => valoresDe(editables, (s) => s.prevista));
  const [revisar, setRevisar] = useState(false);
  const { guardando, error, enviar } = useEnvio();
  const valido = (texto: string) => texto === '' || entero(texto);

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
        <Stat label="Total previsto" value={(comensales?.prevista ?? 0) + sumar(editables, valores)} unit="personas" />
      </div>
    </Dialog>
  );
}

/** UI-20 · La real de todos los segmentos a la vez, del control de acceso y de la venta en puerta. */
function DialogoAsistenciaReal({ ficha, asistencia, alCerrar, alGuardar }: {
  ficha: Ficha;
  asistencia: Asistencia;
  alCerrar: () => void;
  alGuardar: (ficha: Ficha) => void;
}) {
  const activos = asistencia.segmentos.filter((s) => s.activo);
  const [valores, setValores] = useState(() => valoresDe(activos, (s) => s.real));
  const [revisar, setRevisar] = useState(false);
  const { guardando, error, enviar } = useEnvio();

  async function guardar() {
    const invalidos = activos.some((s) => !entero(valores[s.id]));
    setRevisar(invalidos);
    if (invalidos) return;
    const segmentos = activos.map((s) => ({ segmentoId: s.id, cantidad: Number(valores[s.id]) }));
    const nueva = await enviar(() => fichas.registrarAsistenciaReal(ficha.id, ficha.version, segmentos));
    if (nueva) alGuardar(nueva);
  }

  return (
    <Dialog
      open
      title="Asistencia real de cierre"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="clipboard-check" loading={guardando} onClick={() => void guardar()}>Guardar asistencia real</Button>
        </>
      }
    >
      <div className="asistencia">
        {error && <Alert tone="danger">{error.message}</Alert>}
        <p className="body-sm v-muted">
          Cargá lo que contó la plataforma de control de acceso y lo vendido en puerta. Todos los segmentos: 0 si no vino nadie.
        </p>
        {activos.map((s) => (
          <Input
            key={s.id}
            label={s.nombre}
            inputMode="numeric"
            hint={s.prevista != null ? `Previsto: ${s.prevista}` : undefined}
            value={valores[s.id]}
            onChange={(e) => setValores((previos) => ({ ...previos, [s.id]: e.target.value.trim() }))}
            error={revisar && !entero(valores[s.id]) ? 'Escribí un número entero de 0 a 5000.' : undefined}
          />
        ))}
        <Stat label="Total real" value={sumar(activos, valores)} unit="personas" />
      </div>
    </Dialog>
  );
}
