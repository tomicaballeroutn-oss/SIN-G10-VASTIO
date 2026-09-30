import type { ReactNode } from 'react';
import type { RouteObject } from 'react-router';
import { PaginaAgenda } from './features/agenda/PaginaAgenda';
import { PaginaDs } from './features/ds/PaginaDs';
import { PaginaEvento } from './features/eventos/PaginaEvento';
import { PaginaEventos } from './features/eventos/PaginaEventos';
import { Layout } from './features/layout/Layout';
import { MENU } from './features/layout/menu';
import { ConPermiso, IrAlInicio, PaginaMas, PaginaMiCuenta, PaginaPendiente } from './features/layout/paginas';
import { PaginaParametros } from './features/parametros/PaginaParametros';
import { PaginaUsuarios } from './features/usuarios/PaginaUsuarios';
import { PaginaIngreso } from './features/sesion/PaginaIngreso';

/** Pantallas ya construidas, por id del menú. El resto muestra PaginaPendiente hasta que llegue su historia. */
const PANTALLAS: Partial<Record<string, ReactNode>> = {
  agenda: <PaginaAgenda />,
  eventos: <PaginaEventos />,
  parametros: <PaginaParametros />,
  usuarios: <PaginaUsuarios />,
};

export const rutas: RouteObject[] = [
  { path: '/login', element: <PaginaIngreso /> },
  { path: '/_ds', element: <PaginaDs /> },
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <IrAlInicio /> },
      ...MENU.map((item) => ({
        path: item.ruta.slice(1),
        element: <ConPermiso item={item}>{PANTALLAS[item.id] ?? <PaginaPendiente item={item} />}</ConPermiso>,
      })),
      {
        path: 'eventos/:id',
        element: <ConPermiso item={MENU.find((it) => it.id === 'eventos')!}><PaginaEvento /></ConPermiso>,
      },
      { path: 'mas', element: <PaginaMas /> },
      { path: 'mi-cuenta', element: <PaginaMiCuenta /> },
      { path: '*', element: <IrAlInicio /> },
    ],
  },
];
