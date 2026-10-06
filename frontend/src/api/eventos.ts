import type { SalonId } from '../ds';
import type { EstadoEvento } from './agenda';
import { api, descargarArchivo } from './cliente';

export interface Cliente {
  id: number;
  nombre: string;
  documento: string | null;
  telefono: string | null;
  email: string | null;
}

/** Cliente existente (`id`) o nuevo (`nombre`). Teléfono y correo, si vienen, actualizan los del existente. */
export interface ClientePedido {
  id?: number;
  nombre?: string;
  documento?: string;
  telefono?: string;
  email?: string;
}

export interface PedidoPreReserva {
  salonId: number;
  /** yyyy-mm-dd */
  fecha: string;
  turnoId: number;
  /** Sin elegir, el backend responde con el error del campo. */
  tipoEventoId?: number;
  /** Vacío: el backend lo arma con el tipo y el cliente. */
  nombre?: string;
  /** Solo Coordinación y Dirección; la vendedora pre-reserva a su nombre. */
  vendedoraId?: number;
  cliente: ClientePedido;
}

export interface PreReservaRegistrada {
  id: number;
  codigo: string;
  nombre: string;
  estado: EstadoEvento;
}

export const eventos = {
  preReservar: (pedido: PedidoPreReserva) => api<PreReservaRegistrada>('/eventos', { metodo: 'POST', cuerpo: pedido }),
  buscarClientes: (texto: string) => api<Cliente[]>(`/clientes?buscar=${encodeURIComponent(texto)}`),
};

/** Nombre propuesto: «Quince de Delfina Ríos». */
export function nombrePropuesto(tipo: string | undefined, cliente: string): string {
  return tipo && cliente.trim() ? `${tipo} de ${cliente.trim()}` : '';
}

export interface Nombre {
  id: number;
  nombre: string;
}

export interface SalonEvento {
  id: number;
  codigo: SalonId;
  nombre: string;
  /** Para advertir (sin bloquear) si los invitados la superan. */
  capacidad: number | null;
}

export interface TurnoEvento {
  id: number;
  codigo: 'mediodia' | 'noche';
  nombre: string;
  horaInicio: string;
  horaFin: string;
}

/** `importe` solo llega a quien ve datos económicos. */
export interface Sena {
  importe?: number;
  fecha: string;
  firmanteNombre: string | null;
  firmanteDni: string | null;
  firmanteContacto: string | null;
}

/** Cambio de estado. Sin `estadoAnterior`, es la creación. */
export interface CambioDeEstado {
  tipo: 'ESTADO';
  estadoAnterior?: EstadoEvento;
  estadoNuevo: EstadoEvento;
  usuario?: Nombre;
  fechaHora: string;
  observacion?: string;
}

/** Cambio de salón, fecha o turno («Avril · sáb 10/10 · Noche»), con el motivo y el detalle en `observacion`. */
export interface CambioDeUnidad {
  tipo: 'REPROGRAMACION';
  campo: 'unidad';
  valorAnterior: string;
  valorNuevo: string;
  usuario: Nombre;
  fechaHora: string;
  observacion?: string;
}

/** Un dato que cambió al registrar el evento. Sin valor anterior: se agregó; sin nuevo: se quitó. */
export interface Modificacion {
  tipo: 'MODIFICACION';
  campo: string;
  valorAnterior?: string;
  valorNuevo?: string;
  usuario: Nombre;
  fechaHora: string;
}

export type EntradaHistorial = CambioDeEstado | Modificacion | CambioDeUnidad;

export interface Contacto {
  id: number;
  nombre: string;
  vinculo: string | null;
  telefono: string | null;
  email: string | null;
}

/** Lo que manda «Registrar evento»: el estado completo de los datos. Los contactos que faltan se quitan. */
export interface DatosEvento {
  version: number;
  nombre: string;
  tipoEventoId: number;
  /** HH:mm; null: la del turno. */
  horaInicio: string | null;
  observacionesInternas: string;
  cliente: { nombre: string; documento: string; telefono: string; email: string };
  contactos: { id?: number; nombre: string; vinculo: string; telefono: string; email: string }[];
}

/** Lo que la persona puede hacer, calculado por el backend según perfil y estado. */
export interface Acciones {
  modificar: boolean;
  liberar: boolean;
  registrarSena: boolean;
  registrarFirma: boolean;
  asignarPlanner: boolean;
  confirmar: boolean;
  cancelar: boolean;
  reprogramar: boolean;
}

/** Una condición de «Confirmar evento»: lo que hay («Ana Sosa», «180») o lo que falta. */
export interface RequisitoConfirmacion {
  codigo: 'PLANNER' | 'INVITADOS' | 'SERVICIOS' | 'FECHA';
  titulo: string;
  cumplido: boolean;
  detalle: string;
}

/** Planner activa para asignar, con los otros eventos que ya tiene esa fecha (se advierte, no se impide). */
export interface PlannerCandidata {
  id: number;
  nombre: string;
  otrosEventos: string[];
}

/**
 * Una categoría de servicio de la ficha, en el orden configurado. `activa = false`: categoría dada de baja con algo
 * cargado (se ve, no se edita). Sin servicio, `descripcion` es null.
 */
export interface ServicioFicha {
  categoriaId: number;
  categoria: string;
  activa: boolean;
  requeridaParaConfirmar: boolean;
  visibleEnCocina: boolean;
  descripcion: string | null;
  usuario: Nombre | null;
  fechaModificacion: string | null;
}

/** Archivo del legajo (p. ej. una hoja del contrato). */
export interface DocumentoLegajo {
  id: number;
  tipo: 'CONTRATO' | 'PRESUPUESTO' | 'OTRO';
  nombreArchivo: string;
  mimeType: string;
  tamanoBytes: number;
  usuario: Nombre;
  fechaCarga: string;
}

export interface Ficha {
  id: number;
  codigo: string;
  estado: EstadoEvento;
  nombre: string;
  tipo: Nombre;
  salon: SalonEvento;
  fecha: string;
  turno: TurnoEvento;
  /** HH:mm:ss; null: la del turno. */
  horaInicio: string | null;
  /** Unidad original si el evento se reprogramó («Avril · sáb 26/9 · Noche»). */
  reprogramadoDesde: string | null;
  cliente: Cliente;
  contactos: Contacto[];
  vendedora: Nombre;
  planner: Nombre | null;
  cantidadInvitados: number | null;
  invitadosDefinitivos: boolean;
  observacionesInternas: string | null;
  sena: Sena | null;
  /** yyyy-mm-dd, desde Contratado. */
  fechaFirmaContrato: string | null;
  /** Solo en Cancelado. */
  cancelacion: { motivo: string; detalle: string | null } | null;
  /** null para quien no ve el legajo: el contrato tiene importes. */
  documentos: DocumentoLegajo[] | null;
  servicios: ServicioFicha[];
  /** Solo en Contratado: lo que muestra el diálogo «Confirmar evento». */
  requisitosConfirmacion: RequisitoConfirmacion[] | null;
  fechaCreacion: string;
  /** Se manda al guardar, para no pisar cambios de otra persona. */
  version: number;
  acciones: Acciones;
  historial: EntradaHistorial[];
}

export interface EventoResumen {
  id: number;
  codigo: string;
  estado: EstadoEvento;
  nombre: string;
  tipo: string;
  salon: SalonEvento;
  fecha: string;
  turno: TurnoEvento;
  cliente: string;
  vendedora: Nombre;
  planner: Nombre | null;
  cantidadInvitados: number | null;
}

/** Registrar seña: importe en pesos, fecha del pago (yyyy-mm-dd) y firmante. */
export interface PedidoSena {
  importe: number | null;
  fechaPago: string;
  firmanteNombre: string;
  firmanteDni: string;
  firmanteContacto: string;
}

export const fichas = {
  ficha: (id: number) => api<Ficha>(`/eventos/${id}`),
  proximos: () => api<EventoResumen[]>('/eventos'),
  registrarDatos: (id: number, datos: DatosEvento) => api<Ficha>(`/eventos/${id}`, { metodo: 'PUT', cuerpo: datos }),
  liberar: (id: number) => api<Ficha>(`/eventos/${id}/liberacion`, { metodo: 'POST' }),
  registrarSena: (id: number, sena: PedidoSena) => api<Ficha>(`/eventos/${id}/sena`, { metodo: 'POST', cuerpo: sena }),
  registrarFirma: (id: number, fechaFirma: string, archivos: File[]) => {
    const formulario = new FormData();
    formulario.append('fechaFirma', fechaFirma);
    archivos.forEach((a) => formulario.append('archivos', a));
    return api<Ficha>(`/eventos/${id}/contrato`, { metodo: 'POST', cuerpo: formulario });
  },
  reprogramar: (id: number, pedido: { salonId: number; fecha: string; turnoId: number; motivoId: number | null; detalle: string }) =>
    api<Ficha>(`/eventos/${id}/reprogramacion`, { metodo: 'POST', cuerpo: pedido }),
  cancelar: (id: number, motivoId: number, detalle: string) =>
    api<Ficha>(`/eventos/${id}/cancelacion`, { metodo: 'POST', cuerpo: { motivoId, detalle } }),
  confirmar: (id: number) => api<Ficha>(`/eventos/${id}/confirmacion`, { metodo: 'POST' }),
  planners: (id: number) => api<PlannerCandidata[]>(`/eventos/${id}/planners`),
  /** `plannerId` null quita la planner (solo en Contratado). */
  asignarPlanner: (id: number, plannerId: number | null) =>
    api<Ficha>(`/eventos/${id}/planner`, { metodo: 'PUT', cuerpo: { plannerId } }),
  registrarInvitados: (id: number, version: number, cantidad: number, definitivos: boolean) =>
    api<Ficha>(`/eventos/${id}/invitados`, { metodo: 'PUT', cuerpo: { version, cantidad, definitivos } }),
  registrarServicios: (id: number, version: number, servicios: { categoriaId: number; descripcion: string }[]) =>
    api<Ficha>(`/eventos/${id}/servicios`, { metodo: 'PUT', cuerpo: { version, servicios } }),
  descargarDocumento: (id: number, documentoId: number) => descargarArchivo(`/eventos/${id}/documentos/${documentoId}`),
};

/** Lo que acepta «Registrar firma de contrato». */
export const CONTRATO_TIPOS = 'application/pdf,image/jpeg,image/png';
export const CONTRATO_MAXIMO_ARCHIVOS = 10;
export const CONTRATO_MAXIMO_BYTES = 10 * 1024 * 1024;
