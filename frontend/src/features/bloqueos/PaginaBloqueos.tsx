import { useState, type FormEvent } from 'react';
import { bloqueos, MAXIMO_DIAS_DE_BLOQUEO, type Bloqueo } from '../../api/bloqueos';
import { catalogos, type Motivo, type Salon, type Turno } from '../../api/catalogos';
import { useDatos, useEnvio } from '../../api/useDatos';
import { Actor, Alert, Button, Card, Checkbox, Dialog, Input, Select, Table, type TableColumn } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta, fechaHora } from '../comun/formato';
import { Encabezado } from '../layout/paginas';
import './bloqueos.css';

/** Valor del selector de turnos que bloquea todos. */
const AMBOS = 'ambos';

interface Errores {
  salones?: string;
  desde?: string;
  hasta?: string;
  turno?: string;
  motivo?: string;
}

/** «sáb 25 dic 2026» o, si abarca varios días, «lun 4 abr 2033 al vie 8 abr 2033». */
function rangoLegible(desde: string, hasta: string): string {
  return desde === hasta ? fechaCorta(desde, true) : `${fechaCorta(desde, true)} al ${fechaCorta(hasta, true)}`;
}

/** «Ambos» cuando cubre todos los turnos; si no, sus nombres. */
function turnosLegibles(bloqueo: Bloqueo, cantidadDeTurnos: number): string {
  return bloqueo.turnos.length === cantidadDeTurnos && cantidadDeTurnos > 1 ? 'Ambos' : bloqueo.turnos.map((t) => t.nombre).join(' y ');
}

const salonesLegibles = (bloqueo: Bloqueo) => bloqueo.salones.map((s) => s.nombre).join(' · ');

/**
 * UI-21 · Bloqueo de unidades (A1-02): saca de la venta salones × fechas × turnos por mantenimiento, feriado o evento
 * propio. Lista los bloqueos activos y permite desbloquear con confirmación. Administración, Coordinación y Dirección.
 * Una unidad con evento activo o ya bloqueada no se bloquea: el servidor lo rechaza y nombra el evento.
 */
export function PaginaBloqueos() {
  const lista = useDatos(bloqueos.listar);
  const salones = useDatos(catalogos.salones);
  const turnos = useDatos(catalogos.turnos);
  const motivos = useDatos(catalogos.motivos);
  const [aDesbloquear, setADesbloquear] = useState<Bloqueo | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  function registrado(nuevo: Bloqueo) {
    setAviso(`Bloqueo registrado: ${salonesLegibles(nuevo)}, ${rangoLegible(nuevo.desde, nuevo.hasta)}.`);
    lista.recargar();
  }

  function desbloqueado(bloqueo: Bloqueo) {
    setADesbloquear(null);
    setAviso(`Se desbloqueó ${salonesLegibles(bloqueo)}, ${rangoLegible(bloqueo.desde, bloqueo.hasta)}. Las fechas vuelven a estar disponibles.`);
    lista.recargar();
  }

  const cantidadDeTurnos = turnos.datos?.length ?? 0;
  const columnas: TableColumn<Bloqueo>[] = [
    { key: 'salon', header: 'Salón', render: (b) => <span className="bloqueos__salon">{salonesLegibles(b)}</span> },
    { key: 'rango', header: 'Rango de fechas', render: (b) => rangoLegible(b.desde, b.hasta) },
    { key: 'turnos', header: 'Turnos', render: (b) => turnosLegibles(b, cantidadDeTurnos) },
    {
      key: 'motivo',
      header: 'Motivo',
      render: (b) => (
        <>
          {b.motivo.nombre}
          {b.detalle && <span className="v-muted"> · {b.detalle}</span>}
        </>
      ),
    },
    {
      key: 'registro',
      header: 'Registro',
      render: (b) => <Actor name={b.registradoPor} action="registró el bloqueo" at={fechaHora(b.fechaRegistro)} size="sm" />,
    },
    {
      key: 'acciones',
      header: 'Acciones',
      render: (b) => (
        <Button variant="text" size="sm" icon="lock-open" onClick={() => setADesbloquear(b)}>Desbloquear</Button>
      ),
    },
  ];

  return (
    <section className="pantalla">
      <Encabezado titulo="Bloqueo de unidades" antetitulo="Agenda" />
      {aviso && <Alert tone="success">{aviso}</Alert>}
      <Card title="Nuevo bloqueo" subtitle="En la agenda, un bloqueo se ve rayado con el ícono de candado.">
        <Cargando datos={salones}>
          {(salonesDelCatalogo) => (
            <Cargando datos={turnos}>
              {(turnosDelCatalogo) => (
                <Cargando datos={motivos}>
                  {(motivosDelCatalogo) => (
                    <FormularioBloqueo
                      salones={salonesDelCatalogo.filter((s) => s.activo)}
                      turnos={turnosDelCatalogo}
                      motivos={motivosDelCatalogo.filter((m) => m.ambito === 'BLOQUEO' && m.activo)}
                      alRegistrar={registrado}
                    />
                  )}
                </Cargando>
              )}
            </Cargando>
          )}
        </Cargando>
      </Card>
      <Cargando datos={lista}>
        {(activos) => (
          <Card flush title="Bloqueos activos">
            <Table caption="Bloqueos activos" columns={columnas} rows={activos} empty="No hay bloqueos activos." />
          </Card>
        )}
      </Cargando>
      {aDesbloquear && (
        <DialogoDesbloquear
          bloqueo={aDesbloquear}
          cantidadDeTurnos={cantidadDeTurnos}
          alCerrar={() => setADesbloquear(null)}
          alDesbloquear={desbloqueado}
        />
      )}
    </section>
  );
}

function FormularioBloqueo({ salones, turnos, motivos, alRegistrar }: {
  salones: Salon[];
  turnos: Turno[];
  motivos: Motivo[];
  alRegistrar: (bloqueo: Bloqueo) => void;
}) {
  const [salonIds, setSalonIds] = useState<number[]>([]);
  const [desde, setDesde] = useState('');
  const [hasta, setHasta] = useState('');
  const [turno, setTurno] = useState('');
  const [motivo, setMotivo] = useState('');
  const [detalle, setDetalle] = useState('');
  const [errores, setErrores] = useState<Errores>({});
  const { guardando, error, enviar, limpiarError } = useEnvio();

  function alternar(id: number, marcado: boolean) {
    setSalonIds((previos) => (marcado ? [...previos, id] : previos.filter((s) => s !== id)));
  }

  async function registrar(e: FormEvent) {
    e.preventDefault();
    limpiarError();
    const faltan: Errores = {
      salones: salonIds.length ? undefined : 'Elegí al menos un salón.',
      desde: desde ? undefined : 'Elegí la fecha desde la que se bloquea.',
      hasta: hasta && desde && hasta < desde ? 'La fecha final no puede ser anterior a la inicial. Revisá el rango.' : undefined,
      turno: turno ? undefined : 'Elegí el turno.',
      motivo: motivo ? undefined : 'Elegí el motivo.',
    };
    setErrores(faltan);
    if (Object.values(faltan).some(Boolean)) return;

    const creado = await enviar(() =>
      bloqueos.registrar({
        salonIds,
        desde,
        hasta: hasta || null,
        turnoIds: turno === AMBOS ? turnos.map((t) => t.id) : [Number(turno)],
        motivoId: Number(motivo),
        detalle: detalle.trim() || null,
      }),
    );
    if (!creado) return;
    setSalonIds([]);
    setDesde('');
    setHasta('');
    setTurno('');
    setMotivo('');
    setDetalle('');
    setErrores({});
    alRegistrar(creado);
  }

  const delServidor = (campo: string) => error?.errorDe(campo);
  // Un error del servidor que no es de un campo (conflicto con un evento, rango muy largo…) va arriba del formulario.
  const general = error && !error.errores.length ? error : undefined;

  return (
    <form className="bloqueos__form" onSubmit={(e) => void registrar(e)} noValidate>
      {general && <Alert tone="danger" title="No pudimos registrar el bloqueo">{general.message}</Alert>}
      <fieldset className="bloqueos__salones">
        <legend className="label">Salones</legend>
        <div className="bloqueos__opciones">
          {salones.map((s) => (
            <Checkbox key={s.id} label={s.nombre} checked={salonIds.includes(s.id)} onChange={(e) => alternar(s.id, e.target.checked)} />
          ))}
        </div>
        {(errores.salones ?? delServidor('salonIds')) && (
          <p className="body-sm v-field__error" role="alert">{errores.salones ?? delServidor('salonIds')}</p>
        )}
      </fieldset>
      <div className="bloqueos__campos">
        <Input
          label="Desde"
          type="date"
          value={desde}
          onChange={(e) => setDesde(e.target.value)}
          error={errores.desde ?? delServidor('desde')}
        />
        <Input
          label="Hasta"
          optional
          type="date"
          hint={`Si lo dejás vacío, se bloquea un solo día. Hasta ${MAXIMO_DIAS_DE_BLOQUEO} días por vez.`}
          value={hasta}
          onChange={(e) => setHasta(e.target.value)}
          error={errores.hasta ?? delServidor('hasta')}
        />
        <Select
          label="Turnos"
          placeholder="Elegí el turno"
          options={[...turnos.map((t) => ({ value: String(t.id), label: t.nombre })), { value: AMBOS, label: 'Ambos' }]}
          value={turno}
          onChange={(e) => setTurno(e.target.value)}
          error={errores.turno ?? delServidor('turnoIds')}
        />
        <Select
          label="Motivo"
          placeholder="Elegí el motivo"
          options={motivos.map((m) => ({ value: String(m.id), label: m.nombre }))}
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          error={errores.motivo ?? delServidor('motivoId')}
        />
      </div>
      <Input
        label="Detalle"
        optional
        hint="Por ejemplo, qué se hace o quién lo pidió."
        value={detalle}
        onChange={(e) => setDetalle(e.target.value)}
        error={delServidor('detalle')}
      />
      <div className="bloqueos__acciones">
        <Button type="submit" icon="lock" loading={guardando}>Registrar bloqueo</Button>
      </div>
    </form>
  );
}

function DialogoDesbloquear({ bloqueo, cantidadDeTurnos, alCerrar, alDesbloquear }: {
  bloqueo: Bloqueo;
  cantidadDeTurnos: number;
  alCerrar: () => void;
  alDesbloquear: (bloqueo: Bloqueo) => void;
}) {
  const { guardando, error, enviar } = useEnvio();

  async function desbloquear() {
    const hecho = await enviar(async () => {
      await bloqueos.desbloquear(bloqueo.id);
      return true;
    });
    if (hecho) alDesbloquear(bloqueo);
  }

  return (
    <Dialog
      open
      title="Desbloquear unidades"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="lock-open" loading={guardando} onClick={() => void desbloquear()}>Desbloquear</Button>
        </>
      }
    >
      <div className="bloqueos__dialogo">
        {error && <Alert tone="danger">{error.message}</Alert>}
        <p className="body-lg">Las fechas vuelven a estar disponibles en la agenda para pre-reservar. El bloqueo queda registrado.</p>
        <p className="body-sm v-muted">
          {salonesLegibles(bloqueo)} · {rangoLegible(bloqueo.desde, bloqueo.hasta)} · {turnosLegibles(bloqueo, cantidadDeTurnos)} · {bloqueo.motivo.nombre}
        </p>
      </div>
    </Dialog>
  );
}
