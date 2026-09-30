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
