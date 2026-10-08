import { useState } from 'react';
import { MOTIVO_RECUENTO, MOTIVO_ROTURA, ajustes, type ResultadoAjuste } from '../../api/ajustes';
import type { Motivo } from '../../api/catalogos';
import type { RenglonStock } from '../../api/stock';
import { useEnvio } from '../../api/useDatos';
import { Alert, Button, CantidadEnCajas, Dialog, Input, Select, cantidadLegible } from '../../ds';

interface Props {
  ubicacion: { id: number; nombre: string };
  renglon: RenglonStock;
  /** Motivos activos del ámbito Ajuste. */
  motivos: Motivo[];
  alCerrar: () => void;
  alRegistrar: (mensaje: string) => void;
}

const pideDetalle = (m: Motivo | undefined) => !!m && ['otro', 'otra'].includes(m.nombre.trim().toLowerCase());

/** «+4 botellas», «−1 caja y 2 botellas». */
function conSigno(n: number, r: RenglonStock) {
  return (n > 0 ? '+' : '') + cantidadLegible(n, r.bebida.unidadesPorBulto, r.bebida.unidad);
}

function CamposMotivo({ motivos, motivoId, setMotivoId, detalle, setDetalle, error }: {
  motivos: Motivo[];
  motivoId: string;
  setMotivoId: (v: string) => void;
  detalle: string;
  setDetalle: (v: string) => void;
  error?: string;
}) {
  const motivo = motivos.find((m) => String(m.id) === motivoId);
  return (
    <>
      <Select label="Motivo" options={motivos.map((m) => ({ value: String(m.id), label: m.nombre }))} value={motivoId} onChange={(e) => setMotivoId(e.target.value)} />
      <Input
        label="Detalle"
        optional={!pideDetalle(motivo)}
        multiline
        rows={2}
        value={detalle}
        onChange={(e) => setDetalle(e.target.value)}
        hint={pideDetalle(motivo) ? 'Con «Otro», contá qué pasó.' : undefined}
        error={error}
      />
    </>
  );
}

/** El motivo preseleccionado, si está activo; si no, el primero. */
function inicial(motivos: Motivo[], id: number) {
  return String((motivos.find((m) => m.id === id) ?? motivos[0])?.id ?? '');
}

/**
 * Registrar recuento (UI-38): lo contado reemplaza al saldo teórico. Antes de guardar se ve la diferencia que se va a
 * registrar como ajuste; si coincide, no se registra nada.
 */
export function DialogoRecuento({ ubicacion, renglon, motivos, alCerrar, alRegistrar }: Props) {
  const [contado, setContado] = useState(Math.max(0, Math.round(renglon.cantidad)));
  const [motivoId, setMotivoId] = useState(inicial(motivos, MOTIVO_RECUENTO));
  const [detalle, setDetalle] = useState('');
  const { guardando, error, enviar } = useEnvio();
  const diferencia = contado - renglon.cantidad;
  const nombre = `${renglon.bebida.nombre} ${renglon.bebida.presentacion}`;

  async function registrar() {
    const r: ResultadoAjuste | undefined = await enviar(() =>
      ajustes.recuento({ ubicacionId: ubicacion.id, bebidaId: renglon.bebida.id, cantidadContada: contado, motivoId: Number(motivoId), detalle }),
    );
    if (!r) return;
    alRegistrar(r.registrado
      ? `Recuento de ${nombre} registrado: ajuste de ${conSigno(r.diferencia, renglon)} en ${ubicacion.nombre}.`
      : `El recuento de ${nombre} coincide con el saldo: no hay nada para registrar.`);
  }

  return (
    <Dialog
      open
      title="Registrar recuento"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="check" loading={guardando} onClick={() => void registrar()}>Registrar recuento</Button>
        </>
      }
    >
      <div className="existencias__form">
        {error && <Alert tone="danger">{error.message}</Alert>}
        <p>
          {nombre} en {ubicacion.nombre}. Saldo teórico: <strong>{cantidadLegible(renglon.cantidad, renglon.bebida.unidadesPorBulto, renglon.bebida.unidad)}</strong>.
        </p>
        <CantidadEnCajas label="Cantidad contada" value={contado} onChange={setContado} porBulto={renglon.bebida.unidadesPorBulto} unidad={renglon.bebida.unidad} />
        <p className="body-sm" role="status">
          {diferencia === 0 ? 'Coincide con el saldo: no hay nada para registrar.' : `Se registra un ajuste de ${conSigno(diferencia, renglon)}.`}
        </p>
        <CamposMotivo motivos={motivos} motivoId={motivoId} setMotivoId={setMotivoId} detalle={detalle} setDetalle={setDetalle} error={error?.errorDe('detalle')} />
      </div>
    </Dialog>
  );
}

/** Declarar rotura (UI-38): sale de la ubicación. Simple y sin castigo; si el saldo queda negativo, se avisa. */
export function DialogoRotura({ ubicacion, renglon, motivos, alCerrar, alRegistrar }: Props) {
  const [cantidad, setCantidad] = useState(0);
  const [motivoId, setMotivoId] = useState(inicial(motivos, MOTIVO_ROTURA));
  const [detalle, setDetalle] = useState('');
  const [falta, setFalta] = useState<string | undefined>();
  const { guardando, error, enviar } = useEnvio();
  const nombre = `${renglon.bebida.nombre} ${renglon.bebida.presentacion}`;

  async function declarar() {
    if (cantidad <= 0) {
      setFalta('Cargá cuántas botellas se rompieron.');
      return;
    }
    const r = await enviar(() =>
      ajustes.rotura({ ubicacionId: ubicacion.id, bebidaId: renglon.bebida.id, cantidad, motivoId: Number(motivoId), detalle }),
    );
    if (!r) return;
    const texto = `Rotura de ${nombre} registrada en ${ubicacion.nombre}: ${cantidadLegible(cantidad, renglon.bebida.unidadesPorBulto, renglon.bebida.unidad)}.`;
    alRegistrar(r.avisoSaldoNegativo ? `${texto} El saldo quedó negativo: avisamos a Compras y Administración para que se registre lo que falta.` : texto);
  }

  return (
    <Dialog
      open
      title="Declarar rotura"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="check" loading={guardando} onClick={() => void declarar()}>Declarar rotura</Button>
        </>
      }
    >
      <div className="existencias__form">
        {error && <Alert tone="danger">{error.message}</Alert>}
        <p>
          {nombre} en {ubicacion.nombre}. Declarar lo que se rompió sirve para que el saldo refleje lo que hay.
        </p>
        <CantidadEnCajas
          label="Cantidad rota"
          value={cantidad}
          onChange={(n) => {
            setCantidad(n);
            setFalta(undefined);
          }}
          porBulto={renglon.bebida.unidadesPorBulto}
          unidad={renglon.bebida.unidad}
          error={falta}
        />
        <CamposMotivo motivos={motivos} motivoId={motivoId} setMotivoId={setMotivoId} detalle={detalle} setDetalle={setDetalle} error={error?.errorDe('detalle')} />
      </div>
    </Dialog>
  );
}
