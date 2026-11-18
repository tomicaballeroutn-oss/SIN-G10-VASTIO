import type { ArticuloAyuda } from './modelo';

// Permisos de acciones de Evento, definidos en Permisos.java y aplicados en la ficha.
export const ROLES_PRE_RESERVA = ['DIRECCION', 'COORDINACION', 'VENDEDORA'] as const;
export const ROLES_CONFIRMAR = ['DIRECCION', 'COORDINACION', 'PLANNER'] as const;
export const ROLES_GESTIONAR_ORDEN = ['DIRECCION', 'COORDINACION', 'COMPRAS'] as const;

export const CATEGORIAS = [
  { id: 'primeros-pasos', titulo: 'Primeros pasos' },
  { id: 'agenda', titulo: 'Agenda y eventos' },
  { id: 'bebidas', titulo: 'Bebidas y stock' },
  { id: 'barra', titulo: 'Operación de barra' },
  { id: 'reportes', titulo: 'Reportes y estadísticas' },
  { id: 'gestion', titulo: 'Gestión y cocina' },
] as const;

export const ARTICULOS: readonly ArticuloAyuda[] = [
  { categoria: 'primeros-pasos', id: 'ingresar', titulo: 'Ingresar a Vastio' },
  { categoria: 'primeros-pasos', id: 'mi-cuenta', titulo: 'Mi cuenta y contraseña' },
  { categoria: 'primeros-pasos', id: 'notificaciones', titulo: 'Notificaciones' },
  { categoria: 'agenda', id: 'utilizar-agenda', titulo: 'Cómo utilizar la agenda', pantalla: 'agenda' },
  { categoria: 'agenda', id: 'filtros-estados', titulo: 'Filtros y estados de la agenda', pantalla: 'agenda' },
  { categoria: 'agenda', id: 'ficha-historial', titulo: 'Ficha e historial del evento', pantalla: 'eventos' },
  { categoria: 'agenda', id: 'pre-reservas-senas', titulo: 'Pre-reservas y señas', pantalla: 'agenda', roles: ROLES_PRE_RESERVA },
  { categoria: 'agenda', id: 'confirmacion-reprogramacion', titulo: 'Confirmación y reprogramación', pantalla: 'eventos', roles: [...ROLES_CONFIRMAR, ...ROLES_PRE_RESERVA] },
  { categoria: 'bebidas', id: 'existencias', titulo: 'Consultar existencias', pantalla: 'existencias' },
  { categoria: 'bebidas', id: 'ingresos-ajustes', titulo: 'Registrar ingresos y ajustes', pantalla: 'ingreso' },
  { categoria: 'bebidas', id: 'ordenes-preparacion', titulo: 'Órdenes y preparación de bebidas', pantalla: 'ordenes' },
  { categoria: 'barra', id: 'retiros', titulo: 'Registrar retiros de bebidas', pantalla: 'barra' },
  { categoria: 'barra', id: 'devoluciones', titulo: 'Devoluciones y remanentes', pantalla: 'barra' },
  { categoria: 'barra', id: 'cierre', titulo: 'Cerrar un evento', pantalla: 'barra' },
  { categoria: 'reportes', id: 'ocupacion', titulo: 'Reporte de ocupación', pantalla: 'reportes' },
  { categoria: 'reportes', id: 'estadisticas-consumo', titulo: 'Estadísticas de consumo', pantalla: 'estadisticas' },
  { categoria: 'gestion', id: 'usuarios-perfiles', titulo: 'Usuarios y perfiles', pantalla: 'usuarios' },
  { categoria: 'gestion', id: 'parametros', titulo: 'Parámetros del sistema', pantalla: 'parametros' },
  { categoria: 'gestion', id: 'cocina', titulo: 'Vista de cocina', pantalla: 'cocina' },
];

export const GLOSARIO: ArticuloAyuda = { categoria: 'primeros-pasos', id: 'glosario', titulo: 'Glosario de términos' };
