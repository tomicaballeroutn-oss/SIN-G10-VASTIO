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

const V01 = { videoUrl: 'https://youtu.be/HaqrhVjUWCg', videoRelacionAspecto: 1898 / 1170 };

const V02 = { videoUrl: 'https://youtu.be/_IvVyLp1BkI', videoRelacionAspecto: 1898 / 1170 };

const V03 = { videoUrl: 'https://youtu.be/I7zpEWDAP-I', videoRelacionAspecto: 1898 / 1170 };

const V04 = { videoUrl: 'https://youtu.be/kpOcrk667Ic', videoRelacionAspecto: 1898 / 1170 };

const V05 = { videoUrl: 'https://youtu.be/CnYJ7_p4fmg', videoRelacionAspecto: 1898 / 1170 };

const V06 = { videoUrl: 'https://www.youtube.com/shorts/NVNaKzVUP3w', videoRelacionAspecto: 1080 / 2162 };

const V07 = { videoUrl: 'https://www.youtube.com/shorts/qygweTi5ZvQ', videoRelacionAspecto: 1080 / 1368 };

const V08 = { videoUrl: 'https://youtu.be/bWLLdKbdjXw', videoRelacionAspecto: 1898 / 1170 };

export const ARTICULOS: readonly ArticuloAyuda[] = [
  { categoria: 'primeros-pasos', id: 'ingresar', titulo: 'Ingresar a Vastio' },
  { categoria: 'primeros-pasos', id: 'mi-cuenta', titulo: 'Mi cuenta y contraseña' },
  { categoria: 'primeros-pasos', id: 'notificaciones', titulo: 'Notificaciones' },
  { categoria: 'agenda', id: 'utilizar-agenda', titulo: 'Cómo utilizar la agenda', pantalla: 'agenda', ...V01 },
  { categoria: 'agenda', id: 'filtros-estados', titulo: 'Filtros y estados de la agenda', pantalla: 'agenda' },
  { categoria: 'agenda', id: 'ficha-historial', titulo: 'Ficha e historial del evento', pantalla: 'eventos', ...V02 },
  { categoria: 'agenda', id: 'pre-reservas-senas', titulo: 'Pre-reservas y señas', pantalla: 'agenda', roles: ROLES_PRE_RESERVA, ...V03 },
  { categoria: 'agenda', id: 'confirmacion-reprogramacion', titulo: 'Confirmación y reprogramación', pantalla: 'eventos', roles: [...ROLES_CONFIRMAR, ...ROLES_PRE_RESERVA], ...V04 },
  { categoria: 'bebidas', id: 'existencias', titulo: 'Consultar existencias', pantalla: 'existencias', ...V05 },
  { categoria: 'bebidas', id: 'ingresos-ajustes', titulo: 'Registrar ingresos y ajustes', pantalla: 'ingreso', ...V05 },
  { categoria: 'bebidas', id: 'ordenes-preparacion', titulo: 'Órdenes y preparación de bebidas', pantalla: 'ordenes' },
  { categoria: 'barra', id: 'retiros', titulo: 'Registrar retiros de bebidas', pantalla: 'barra', ...V06 },
  { categoria: 'barra', id: 'devoluciones', titulo: 'Devoluciones y remanentes', pantalla: 'barra', ...V06 },
  { categoria: 'barra', id: 'cierre', titulo: 'Cerrar un evento', pantalla: 'barra', ...V07 },
  { categoria: 'reportes', id: 'ocupacion', titulo: 'Reporte de ocupación', pantalla: 'reportes', ...V08 },
  { categoria: 'reportes', id: 'estadisticas-consumo', titulo: 'Estadísticas de consumo', pantalla: 'estadisticas', ...V08 },
  { categoria: 'gestion', id: 'usuarios-perfiles', titulo: 'Usuarios y perfiles', pantalla: 'usuarios' },
  { categoria: 'gestion', id: 'parametros', titulo: 'Parámetros del sistema', pantalla: 'parametros' },
  { categoria: 'gestion', id: 'cocina', titulo: 'Vista de cocina', pantalla: 'cocina' },
];

export const GLOSARIO: ArticuloAyuda = { categoria: 'primeros-pasos', id: 'glosario', titulo: 'Glosario de términos' };
