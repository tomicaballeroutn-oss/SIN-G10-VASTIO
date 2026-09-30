import { useEffect, useState } from 'react';
import { fechaLarga, type Agenda } from '../../api/agenda';
import { catalogos, hora } from '../../api/catalogos';
import { eventos, nombrePropuesto, type Cliente, type PreReservaRegistrada } from '../../api/eventos';
import { useDatos, useEnvio } from '../../api/useDatos';
import { usuarios } from '../../api/usuarios';
import { Alert, Button, Combobox, Dialog, Input, SalonTag, Select, type ComboboxOption } from '../../ds';
import { useSesion } from '../sesion/contexto';

const NUEVO = 'nuevo';
const cargarVendedoras = () => usuarios.personas('VENDEDORA');

export interface UnidadElegida {
  fecha: string;
  salonId: number;
  turnoId: number;
}

/**
 * UI-08 · Registrar pre-reserva desde una unidad libre de la agenda: salón, fecha y turno llegan elegidos.
 * Pide cliente (de la lista o nuevo), tipo y nombre del evento (se propone solo y se puede cambiar).
 * La vendedora pre-reserva a su nombre; Coordinación y Dirección eligen la vendedora interviniente.
 */
export function DialogoPreReserva({ agenda, unidad, alCerrar, alRegistrar, alChocar }: {
  agenda: Agenda;
  unidad: UnidadElegida;
  alCerrar: () => void;
  alRegistrar: (registrada: PreReservaRegistrada) => void;
  /** La unidad ya no está libre: la agenda tiene que volver a cargarse. */
  alChocar: () => void;
}) {
  const { usuario } = useSesion();
  const eligeVendedora = !!usuario?.roles.some((r) => r === 'DIRECCION' || r === 'COORDINACION');
  const tipos = useDatos(catalogos.tiposEvento);
  const vendedoras = useDatos(eligeVendedora ? cargarVendedoras : sinVendedoras);

  const [texto, setTexto] = useState('');
  const [cliente, setCliente] = useState<Cliente | 'nuevo' | null>(null);
  const [sugerencias, setSugerencias] = useState<Cliente[]>([]);
  const [buscando, setBuscando] = useState(false);
  const [documento, setDocumento] = useState('');
  const [telefono, setTelefono] = useState('');
  const [email, setEmail] = useState('');
  const [tipoId, setTipoId] = useState('');
  const [nombre, setNombre] = useState('');
  const [nombreTocado, setNombreTocado] = useState(false);
  const [vendedoraId, setVendedoraId] = useState(eligeVendedora && usuario?.roles.includes('VENDEDORA') ? String(usuario.id) : '');
  const { guardando, error, enviar } = useEnvio();

  const salon = agenda.salones.find((s) => s.id === unidad.salonId);
  const turno = agenda.turnos.find((t) => t.id === unidad.turnoId);
  const tiposActivos = (tipos.datos ?? []).filter((t) => t.activo);
  const tipo = tiposActivos.find((t) => String(t.id) === tipoId);
  const nombreCliente = cliente === NUEVO ? texto : cliente?.nombre ?? '';
  const nombreFinal = nombreTocado ? nombre : nombrePropuesto(tipo?.nombre, nombreCliente);

  // Busca mientras se escribe, con una pausa corta para no pedir por cada tecla.
  useEffect(() => {
    if (cliente !== null || texto.trim().length < 2) return undefined;
    let vigente = true;
    const espera = setTimeout(() => {
      setBuscando(true);
      eventos.buscarClientes(texto.trim()).then(
        (lista) => vigente && setSugerencias(lista),
        () => vigente && setSugerencias([]),
      ).finally(() => vigente && setBuscando(false));
    }, 250);
    return () => {
      vigente = false;
      clearTimeout(espera);
    };
  }, [texto, cliente]);

  const opciones: ComboboxOption[] = [
    ...(texto.trim().length >= 2 ? sugerencias : []).map((c) => ({
      value: String(c.id),
      label: c.nombre,
      detail: [c.documento && `Doc. ${c.documento}`, c.telefono, c.email].filter(Boolean).join(' · ') || undefined,
    })),
    ...(texto.trim() ? [{ value: NUEVO, label: `Cargar «${texto.trim()}» como cliente nuevo`, icon: 'user-plus' as const }] : []),
  ];

  function elegirCliente(opcion: ComboboxOption) {
    if (opcion.value === NUEVO) {
      setCliente(NUEVO);
      return;
    }
    const elegido = sugerencias.find((c) => String(c.id) === opcion.value);
    if (!elegido) return;
    setCliente(elegido);
    setTexto(elegido.nombre);
    setTelefono(elegido.telefono ?? '');
    setEmail(elegido.email ?? '');
  }

  function escribirCliente(valor: string) {
    setTexto(valor);
    if (cliente !== null && cliente !== NUEVO) {
      // Cambió el texto de un cliente elegido: se vuelve a buscar.
      setCliente(null);
      setTelefono('');
      setEmail('');
    }
  }

  async function registrar() {
    const registrada = await enviar(() =>
      eventos.preReservar({
        ...unidad,
        tipoEventoId: tipoId ? Number(tipoId) : undefined,
        nombre: nombreFinal,
        vendedoraId: eligeVendedora && vendedoraId ? Number(vendedoraId) : undefined,
        cliente: cliente !== null && cliente !== NUEVO
          ? { id: cliente.id, telefono, email }
          : { nombre: cliente === NUEVO ? texto : '', documento, telefono, email },
      }),
    );
    if (registrada) alRegistrar(registrada);
  }

  const ocupada = error?.codigo === 'FECHA_TOMADA' || error?.codigo === 'UNIDAD_BLOQUEADA';
  const errorCliente = error?.errorDe('cliente.identificado');

  return (
    <Dialog
      open
      title="Registrar pre-reserva"
      onClose={ocupada ? alChocar : alCerrar}
      actions={
        ocupada ? (
          <Button onClick={alChocar}>Volver a la agenda</Button>
        ) : (
          <>
            <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
            <Button icon="calendar" loading={guardando} onClick={() => void registrar()}>Registrar pre-reserva</Button>
          </>
        )
      }
    >
      <div className="prereserva">
        <div className="prereserva__unidad">
          {salon && <SalonTag salon={salon.codigo} label={salon.nombre} />}
          <span className="label">{fechaLarga(unidad.fecha, true)}</span>
          {turno && <span className="body-sm v-muted">{turno.nombre} · {hora(turno.horaInicio)} a {hora(turno.horaFin)}</span>}
        </div>
        {error && (
          <Alert tone="danger" title={ocupada ? 'No se pudo pre-reservar' : undefined}>
            {error.message}
          </Alert>
        )}
        {!ocupada && (
          <>
            <Combobox
              label="Cliente"
              icon="search"
              placeholder="Nombre o documento"
              hint={cliente === NUEVO ? 'Cliente nuevo: se carga con esta pre-reserva.' : 'Buscalo por nombre o documento; si no está, cargalo nuevo.'}
              value={texto}
              onInputChange={escribirCliente}
              options={cliente === null ? opciones : []}
              onSelect={elegirCliente}
              loading={buscando}
              error={errorCliente}
            />
            {cliente === NUEVO && (
              <Input
                label="Documento"
                optional
                inputMode="numeric"
                hint="DNI o CUIT, sin puntos ni guiones."
                value={documento}
                onChange={(e) => setDocumento(e.target.value.replace(/\D/g, ''))}
                error={error?.errorDe('cliente.documento')}
              />
            )}
            {cliente !== null && (
              <div className="prereserva__par">
                <Input label="Teléfono" optional type="tel" inputMode="tel" value={telefono} onChange={(e) => setTelefono(e.target.value)} error={error?.errorDe('cliente.telefono')} />
                <Input label="Correo" optional type="email" inputMode="email" value={email} onChange={(e) => setEmail(e.target.value)} error={error?.errorDe('cliente.email')} />
              </div>
            )}
            <Select
              label="Tipo de evento"
              placeholder="Elegí el tipo"
              options={tiposActivos.map((t) => ({ value: String(t.id), label: t.nombre }))}
              value={tipoId}
              onChange={(e) => setTipoId(e.target.value)}
              error={error?.errorDe('tipoEventoId')}
            />
            <Input
              label="Nombre del evento"
              hint={nombreTocado ? undefined : 'Se arma con el tipo y el cliente. Podés cambiarlo.'}
              value={nombreFinal}
              onChange={(e) => {
                setNombreTocado(true);
                setNombre(e.target.value);
              }}
              error={error?.errorDe('nombre')}
            />
            {eligeVendedora && (
              <Select
                label="Vendedora interviniente"
                placeholder="Elegí la vendedora"
                options={(vendedoras.datos ?? []).map((v) => ({ value: String(v.id), label: v.nombreCompleto }))}
                value={vendedoraId}
                onChange={(e) => setVendedoraId(e.target.value)}
              />
            )}
          </>
        )}
      </div>
    </Dialog>
  );
}

function sinVendedoras() {
  return Promise.resolve([]);
}
