import type { SalonId } from '../ds';
import type { EstadoEvento } from './agenda';
import { api } from './cliente';

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

/** Un dato que cambió al registrar el evento. Sin valor anterior: se agregó; sin nuevo: se quitó. */
export interface Modificacion {
  tipo: 'MODIFICACION';
  campo: string;
  valorAnterior?: string;
  valorNuevo?: string;
  usuario: Nombre;
  fechaHora: string;
}

export type EntradaHistorial = CambioDeEstado | Modificacion;

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
  cantidadInvitados: number | null;
  observacionesInternas: string;
  cliente: { nombre: string; documento: string; telefono: string; email: string };
  contactos: { id?: number; nombre: string; vinculo: string; telefono: string; email: string }[];
}

/** Lo que la persona puede hacer, calculado por el backend según perfil y estado. */
export interface Acciones {
  modificar: boolean;
  liberar: boolean;
  registrarSena: boolean;
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
  cliente: Cliente;
  contactos: Contacto[];
  vendedora: Nombre;
  planner: Nombre | null;
  cantidadInvitados: number | null;
  invitadosDefinitivos: boolean;
  observacionesInternas: string | null;
  sena: Sena | null;
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
};
