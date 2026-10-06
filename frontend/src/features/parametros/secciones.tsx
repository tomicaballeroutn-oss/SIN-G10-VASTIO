import { useState, type FormEvent, type ReactNode } from 'react';
import {
  AMBITOS, catalogos, hora,
  type AmbitoMotivo, type Categoria, type ClaveParametro, type Motivo, type Parametro, type Salon, type Segmento, type TipoEvento, type Turno,
} from '../../api/catalogos';
import type { ProblemaApi } from '../../api/problema';
import { useDatos, useEnvio } from '../../api/useDatos';
import { Alert, Button, Card, Checkbox, Input, SalonTag, Select, Switch } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { ListaYEdicion } from './ListaYEdicion';
import { reemplazar } from './lista';

// ---------- piezas comunes de los formularios ----------

function Formulario({ alEnviar, error, children, accion, guardando }: {
  alEnviar: () => void;
  error: ProblemaApi | null;
  children: ReactNode;
  accion: string;
  guardando: boolean;
}) {
  function enviar(e: FormEvent) {
    e.preventDefault();
    alEnviar();
  }
  return (
    <form className="catalogo__form" onSubmit={enviar} noValidate>
      {error && <Alert tone="danger">{error.message}</Alert>}
      {children}
      <div className="catalogo__acciones">
        <Button type="submit" loading={guardando}>{accion}</Button>
      </div>
    </form>
  );
}

function SwitchActivo({ activo, onChange, ayuda }: { activo: boolean; onChange: (v: boolean) => void; ayuda: string }) {
  return <Switch label="Activo" hint={ayuda} checked={activo} onChange={(e) => onChange(e.target.checked)} />;
}

const AYUDA_BAJA = 'Si lo desactivás, deja de ofrecerse en eventos nuevos. Lo ya registrado lo conserva.';

/** Solo dígitos: evita mandar NaN al backend. */
function soloDigitos(valor: string): string {
  return valor.replace(/\D/g, '');
}

// ---------- salones ----------

export function SeccionSalones() {
  const datos = useDatos(catalogos.salones);
  return (
    <Cargando datos={datos}>
      {(salones) => (
        <ListaYEdicion<Salon>
          items={salones}
          fila={(s) => <SalonTag salon={s.codigo} label={s.nombre} />}
          etiqueta={(s) => s.nombre}
          inactivo={(s) => !s.activo}
          titulo={() => 'Editar salón'}
          alGuardar={(s) => datos.fijar((previos) => reemplazar(previos, s))}
          formulario={(s, listo) => s && <FormularioSalon salon={s} listo={listo} />}
        />
      )}
    </Cargando>
  );
}

function FormularioSalon({ salon, listo }: { salon: Salon; listo: (s: Salon) => void }) {
  const [nombre, setNombre] = useState(salon.nombre);
  const [capacidad, setCapacidad] = useState(salon.capacidad?.toString() ?? '');
  const [activo, setActivo] = useState(salon.activo);
  const { guardando, error, enviar } = useEnvio();

  async function guardar() {
    const guardado = await enviar(() =>
      catalogos.actualizarSalon(salon.id, { nombre, capacidad: capacidad ? Number(capacidad) : null, activo }),
    );
    if (guardado) listo(guardado);
  }

  return (
    <Formulario alEnviar={guardar} error={error} accion="Guardar cambios" guardando={guardando}>
      <Input label="Nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} error={error?.errorDe('nombre')} required />
      <Input
        label="Capacidad"
        optional
        inputMode="numeric"
        suffix="invitados"
        value={capacidad}
        onChange={(e) => setCapacidad(soloDigitos(e.target.value))}
        error={error?.errorDe('capacidad')}
      />
      <SwitchActivo activo={activo} onChange={setActivo} ayuda="Un salón dado de baja no acepta pre-reservas nuevas. Sus eventos siguen en la agenda." />
    </Formulario>
  );
}

// ---------- turnos ----------

export function SeccionTurnos() {
  const datos = useDatos(catalogos.turnos);
  return (
    <Cargando datos={datos}>
      {(turnos) => (
        <ListaYEdicion<Turno>
          items={turnos}
          fila={(t) => (
            <span className="catalogo__texto">
              <span className="label">{t.nombre}</span>
              <span className="body-sm v-muted">
                {hora(t.horaInicio)} a {hora(t.horaFin)}{t.cruzaMedianoche ? ' del día siguiente' : ''}
              </span>
            </span>
          )}
          etiqueta={(t) => t.nombre}
          titulo={() => 'Editar turno'}
          alGuardar={(t) => datos.fijar((previos) => reemplazar(previos, t))}
          formulario={(t, listo) => t && <FormularioTurno turno={t} listo={listo} />}
        />
      )}
    </Cargando>
  );
}

function FormularioTurno({ turno, listo }: { turno: Turno; listo: (t: Turno) => void }) {
  const [nombre, setNombre] = useState(turno.nombre);
  const [inicio, setInicio] = useState(hora(turno.horaInicio));
  const [fin, setFin] = useState(hora(turno.horaFin));
  const { guardando, error, enviar } = useEnvio();
  const cruza = inicio && fin && fin < inicio;

  async function guardar() {
    const guardado = await enviar(() => catalogos.actualizarTurno(turno.id, { nombre, horaInicio: inicio, horaFin: fin }));
    if (guardado) listo(guardado);
  }

  return (
    <Formulario alEnviar={guardar} error={error} accion="Guardar cambios" guardando={guardando}>
      <p className="body-sm v-muted">Podés cambiar el horario aunque haya eventos futuros: vale para todos los eventos de este turno.</p>
      <Input label="Nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} error={error?.errorDe('nombre')} required />
      <div className="catalogo__par">
        <Input label="Hora de inicio" type="time" value={inicio} onChange={(e) => setInicio(e.target.value)} error={error?.errorDe('horaInicio')} required />
        <Input
          label="Hora de fin"
          type="time"
          value={fin}
          onChange={(e) => setFin(e.target.value)}
          error={error?.errorDe('horaFin')}
          hint={cruza ? 'Termina al día siguiente.' : undefined}
          required
        />
      </div>
    </Formulario>
  );
}

// ---------- tipos de evento ----------

export function SeccionTiposEvento() {
  const datos = useDatos(catalogos.tiposEvento);
  return (
    <Cargando datos={datos}>
      {(tipos) => (
        <ListaYEdicion<TipoEvento>
          items={tipos}
          fila={(t) => <span className="label">{t.nombre}</span>}
          etiqueta={(t) => t.nombre}
          inactivo={(t) => !t.activo}
          agregar="Agregar tipo de evento"
          titulo={(t) => (t ? 'Editar tipo de evento' : 'Agregar tipo de evento')}
          alGuardar={(t) => datos.fijar((previos) => reemplazar(previos, t))}
          formulario={(t, listo) => <FormularioTipoEvento tipo={t} listo={listo} />}
        />
      )}
    </Cargando>
  );
}

function FormularioTipoEvento({ tipo, listo }: { tipo?: TipoEvento; listo: (t: TipoEvento) => void }) {
  const [nombre, setNombre] = useState(tipo?.nombre ?? '');
  const [usaSegmentos, setUsaSegmentos] = useState(tipo?.usaSegmentos ?? false);
  const [activo, setActivo] = useState(tipo?.activo ?? true);
  const { guardando, error, enviar } = useEnvio();

  async function guardar() {
    const guardado = await enviar(() =>
      tipo
        ? catalogos.actualizarTipoEvento(tipo.id, { nombre, usaSegmentos, activo })
        : catalogos.crearTipoEvento({ nombre, usaSegmentos }),
    );
    if (guardado) listo(guardado);
  }

  return (
    <Formulario alEnviar={guardar} error={error} accion={tipo ? 'Guardar cambios' : 'Agregar tipo de evento'} guardando={guardando}>
      <Input label="Nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} error={error?.errorDe('nombre')} required />
      <Checkbox
        label="Cuenta la asistencia por segmento"
        hint="Como en egresados: comensales de la cena, entradas anticipadas y venta en puerta."
        checked={usaSegmentos}
        onChange={(e) => setUsaSegmentos(e.target.checked)}
      />
      {tipo && <SwitchActivo activo={activo} onChange={setActivo} ayuda={AYUDA_BAJA} />}
    </Formulario>
  );
}

// ---------- segmentos de asistencia ----------

export function SeccionSegmentos() {
  const datos = useDatos(catalogos.segmentos);
  return (
    <Cargando datos={datos}>
      {(segmentos) => (
        <ListaYEdicion<Segmento>
          items={segmentos}
          fila={(s) => <span className="label">{s.nombre}</span>}
          etiqueta={(s) => s.nombre}
          inactivo={(s) => !s.activo}
          agregar="Agregar segmento"
          titulo={(s) => (s ? 'Editar segmento' : 'Agregar segmento')}
          alGuardar={(s) => datos.fijar((previos) => reemplazar(previos, s))}
          formulario={(s, listo) => <FormularioSegmento segmento={s} listo={listo} />}
        />
      )}
    </Cargando>
  );
}

function FormularioSegmento({ segmento, listo }: { segmento?: Segmento; listo: (s: Segmento) => void }) {
  const [nombre, setNombre] = useState(segmento?.nombre ?? '');
  const [activo, setActivo] = useState(segmento?.activo ?? true);
  const { guardando, error, enviar } = useEnvio();

  async function guardar() {
    const guardado = await enviar(() =>
      segmento ? catalogos.actualizarSegmento(segmento.id, { nombre, activo }) : catalogos.crearSegmento({ nombre }),
    );
    if (guardado) listo(guardado);
  }

  return (
    <Formulario alEnviar={guardar} error={error} accion={segmento ? 'Guardar cambios' : 'Agregar segmento'} guardando={guardando}>
      <Input label="Nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} error={error?.errorDe('nombre')} required />
      {segmento && <SwitchActivo activo={activo} onChange={setActivo} ayuda={AYUDA_BAJA} />}
    </Formulario>
  );
}

// ---------- categorías de servicio ----------

export function SeccionCategorias() {
  const datos = useDatos(catalogos.categorias);
  return (
    <Cargando datos={datos}>
      {(categorias) => (
        <ListaYEdicion<Categoria>
          items={categorias}
          fila={(c) => (
            <span className="catalogo__texto">
              <span className="label">{c.nombre}</span>
              <span className="body-sm v-muted">
                {[c.visibleEnCocina && 'Se ve en cocina', c.requeridaParaConfirmar && 'Hace falta para confirmar', c.avisaACompras && 'Avisa a compras']
                  .filter(Boolean).join(' · ') || 'Solo comercial'}
              </span>
            </span>
          )}
          etiqueta={(c) => c.nombre}
          inactivo={(c) => !c.activo}
          agregar="Agregar categoría"
          titulo={(c) => (c ? 'Editar categoría' : 'Agregar categoría')}
          alGuardar={(c) => datos.fijar((previos) => reemplazar(previos, c).sort((a, b) => a.orden - b.orden || a.nombre.localeCompare(b.nombre)))}
          formulario={(c, listo) => (
            <FormularioCategoria categoria={c} siguienteOrden={Math.max(0, ...categorias.map((x) => x.orden)) + 1} listo={listo} />
          )}
        />
      )}
    </Cargando>
  );
}

function FormularioCategoria({ categoria, siguienteOrden, listo }: {
  categoria?: Categoria;
  siguienteOrden: number;
  listo: (c: Categoria) => void;
}) {
  const [nombre, setNombre] = useState(categoria?.nombre ?? '');
  const [orden, setOrden] = useState(String(categoria?.orden ?? siguienteOrden));
  const [visibleEnCocina, setVisibleEnCocina] = useState(categoria?.visibleEnCocina ?? false);
  const [requerida, setRequerida] = useState(categoria?.requeridaParaConfirmar ?? false);
  const [avisaACompras, setAvisaACompras] = useState(categoria?.avisaACompras ?? false);
  const [activo, setActivo] = useState(categoria?.activo ?? true);
  const { guardando, error, enviar } = useEnvio();

  async function guardar() {
    const datos = { nombre, orden: Number(orden), visibleEnCocina, requeridaParaConfirmar: requerida, avisaACompras };
    const guardado = await enviar(() =>
      categoria ? catalogos.actualizarCategoria(categoria.id, { ...datos, activo }) : catalogos.crearCategoria(datos),
    );
    if (guardado) listo(guardado);
  }

  return (
    <Formulario alEnviar={guardar} error={error} accion={categoria ? 'Guardar cambios' : 'Agregar categoría'} guardando={guardando}>
      <Input label="Nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} error={error?.errorDe('nombre')} required />
      <Input
        label="Orden en la ficha"
        inputMode="numeric"
        value={orden}
        onChange={(e) => setOrden(soloDigitos(e.target.value))}
        error={error?.errorDe('orden')}
        required
      />
      <Checkbox label="Se ve en la vista de cocina" checked={visibleEnCocina} onChange={(e) => setVisibleEnCocina(e.target.checked)} />
      <Checkbox
        label="Hace falta para confirmar el evento"
        hint="El evento no se confirma sin un servicio cargado en esta categoría."
        checked={requerida}
        onChange={(e) => setRequerida(e.target.checked)}
      />
      <Checkbox
        label="Avisa a compras cuando cambia"
        hint="Para las categorías de bebida: un cambio en un evento confirmado le llega a Compras."
        checked={avisaACompras}
        onChange={(e) => setAvisaACompras(e.target.checked)}
      />
      {categoria && <SwitchActivo activo={activo} onChange={setActivo} ayuda={AYUDA_BAJA} />}
    </Formulario>
  );
}

// ---------- motivos ----------

const ORDEN_AMBITOS = Object.keys(AMBITOS) as AmbitoMotivo[];

/** Cancelación, reprogramación, bloqueo y ajuste, como en AMBITOS; dentro de cada uno, por antigüedad. */
function porAmbito(a: Motivo, b: Motivo): number {
  return ORDEN_AMBITOS.indexOf(a.ambito) - ORDEN_AMBITOS.indexOf(b.ambito) || a.id - b.id;
}

export function SeccionMotivos() {
  const datos = useDatos(catalogos.motivos);
  return (
    <Cargando datos={datos}>
      {(motivos) => (
        <ListaYEdicion<Motivo>
          items={[...motivos].sort(porAmbito)}
          grupo={(m) => AMBITOS[m.ambito]}
          fila={(m) => <span className="label">{m.nombre}</span>}
          etiqueta={(m) => `${m.nombre} (${AMBITOS[m.ambito]})`}
          inactivo={(m) => !m.activo}
          agregar="Agregar motivo"
          titulo={(m) => (m ? 'Editar motivo' : 'Agregar motivo')}
          alGuardar={(m) => datos.fijar((previos) => reemplazar(previos, m))}
          formulario={(m, listo) => <FormularioMotivo motivo={m} listo={listo} />}
        />
      )}
    </Cargando>
  );
}

function FormularioMotivo({ motivo, listo }: { motivo?: Motivo; listo: (m: Motivo) => void }) {
  const [ambito, setAmbito] = useState<AmbitoMotivo | ''>(motivo?.ambito ?? '');
  const [nombre, setNombre] = useState(motivo?.nombre ?? '');
  const [activo, setActivo] = useState(motivo?.activo ?? true);
  const { guardando, error, enviar } = useEnvio();

  async function guardar() {
    const guardado = await enviar(() =>
      motivo
        ? catalogos.actualizarMotivo(motivo.id, { nombre, activo })
        : catalogos.crearMotivo({ ambito: ambito as AmbitoMotivo, nombre }),
    );
    if (guardado) listo(guardado);
  }

  return (
    <Formulario alEnviar={guardar} error={error} accion={motivo ? 'Guardar cambios' : 'Agregar motivo'} guardando={guardando}>
      {motivo ? (
        <p className="body-sm v-muted">Se usa en: {AMBITOS[motivo.ambito]}. Eso no se cambia.</p>
      ) : (
        <Select
          label="Se usa en"
          placeholder="Elegí dónde se usa"
          options={ORDEN_AMBITOS.map((a) => ({ value: a, label: AMBITOS[a] }))}
          value={ambito}
          onChange={(e) => setAmbito(e.target.value as AmbitoMotivo)}
          error={error?.errorDe('ambito')}
          required
        />
      )}
      <Input label="Motivo" value={nombre} onChange={(e) => setNombre(e.target.value)} error={error?.errorDe('nombre')} required />
      {motivo && <SwitchActivo activo={activo} onChange={setActivo} ayuda="Si lo desactivás, deja de ofrecerse. Lo ya registrado lo conserva." />}
    </Formulario>
  );
}

// ---------- sesión y cocina ----------

const PARAMETROS: { clave: ClaveParametro; etiqueta: string; unidad: string }[] = [
  { clave: 'MINUTOS_EXPIRACION_SESION', etiqueta: 'Sesión sin uso antes de pedir volver a ingresar', unidad: 'minutos' },
  { clave: 'MINUTOS_AVISO_EXPIRACION', etiqueta: 'Aviso antes de que venza la sesión', unidad: 'minutos' },
  { clave: 'HORIZONTE_COCINA_DIAS', etiqueta: 'Días que muestra la vista de cocina', unidad: 'días' },
];

export function SeccionGenerales() {
  const datos = useDatos(catalogos.parametros);
  return (
    <Cargando datos={datos}>
      {(parametros) => <FormularioGenerales parametros={parametros} alGuardar={(p) => datos.fijar(() => p)} />}
    </Cargando>
  );
}

function FormularioGenerales({ parametros, alGuardar }: { parametros: Parametro[]; alGuardar: (p: Parametro[]) => void }) {
  const actual = (clave: ClaveParametro) => parametros.find((p) => p.clave === clave);
  const [valores, setValores] = useState<Record<string, string>>(() =>
    Object.fromEntries(parametros.map((p) => [p.clave, p.valor])),
  );
  const [errores, setErrores] = useState<Partial<Record<ClaveParametro, string>>>({});
  const [guardado, setGuardado] = useState(false);
  const [guardando, setGuardando] = useState(false);

  /**
   * El aviso tiene que ser menor que la sesión, y el backend lo valida contra el valor ya guardado.
   * Si el aviso sube, se guarda primero la sesión; si baja, primero el aviso.
   */
  function orden(): ClaveParametro[] {
    const avisoSube = Number(valores.MINUTOS_AVISO_EXPIRACION) > Number(actual('MINUTOS_AVISO_EXPIRACION')?.valor);
    return avisoSube
      ? ['MINUTOS_EXPIRACION_SESION', 'MINUTOS_AVISO_EXPIRACION', 'HORIZONTE_COCINA_DIAS']
      : ['MINUTOS_AVISO_EXPIRACION', 'MINUTOS_EXPIRACION_SESION', 'HORIZONTE_COCINA_DIAS'];
  }

  async function guardar() {
    setGuardando(true);
    setGuardado(false);
    setErrores({});
    let lista = parametros;
    const nuevosErrores: Partial<Record<ClaveParametro, string>> = {};
    for (const clave of orden()) {
      if (valores[clave] === actual(clave)?.valor) continue;
      try {
        const p = await catalogos.actualizarParametro(clave, valores[clave]);
        lista = lista.map((x) => (x.clave === p.clave ? p : x));
      } catch (e) {
        nuevosErrores[clave] = (e as Error).message;
      }
    }
    setGuardando(false);
    setErrores(nuevosErrores);
    alGuardar(lista);
    if (Object.keys(nuevosErrores).length === 0) setGuardado(true);
  }

  return (
    <Card title="Sesión y cocina" className="catalogo__generales">
      {guardado && <Alert tone="success">Cambios guardados.</Alert>}
      <Formulario alEnviar={guardar} error={null} accion="Guardar cambios" guardando={guardando}>
        {PARAMETROS.map(({ clave, etiqueta, unidad }) => {
          const p = actual(clave);
          if (!p) return null;
          return (
            <Input
              key={clave}
              label={etiqueta}
              inputMode="numeric"
              suffix={unidad}
              value={valores[clave] ?? ''}
              onChange={(e) => setValores((v) => ({ ...v, [clave]: soloDigitos(e.target.value) }))}
              hint={p.minimo != null && p.maximo != null ? `Entre ${p.minimo} y ${p.maximo}.` : undefined}
              error={errores[clave]}
            />
          );
        })}
      </Formulario>
    </Card>
  );
}
