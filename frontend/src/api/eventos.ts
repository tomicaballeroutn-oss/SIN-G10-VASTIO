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

/** Entrada del historial: por ahora, cambios de estado. */
export interface EntradaHistorial {
  tipo: 'ESTADO';
  estadoAnterior: EstadoEvento | null;
  estadoNuevo: EstadoEvento;
  usuario: Nombre | null;
  fechaHora: string;
  observacion: string | null;
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
  vendedora: Nombre;
  planner: Nombre | null;
  cantidadInvitados: number | null;
  invitadosDefinitivos: boolean;
  observacionesInternas: string | null;
  sena: Sena | null;
  fechaCreacion: string;
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

export const fichas = {
  ficha: (id: number) => api<Ficha>(`/eventos/${id}`),
  proximos: () => api<EventoResumen[]>('/eventos'),
};
