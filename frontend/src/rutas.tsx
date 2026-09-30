import type { ReactNode } from 'react';
import type { RouteObject } from 'react-router';
import { PaginaDs } from './features/ds/PaginaDs';
import { Layout } from './features/layout/Layout';
import { MENU } from './features/layout/menu';
import { ConPermiso, IrAlInicio, PaginaMas, PaginaMiCuenta, PaginaPendiente } from './features/layout/paginas';
import { PaginaParametros } from './features/parametros/PaginaParametros';
import { PaginaUsuarios } from './features/usuarios/PaginaUsuarios';
import { PaginaIngreso } from './features/sesion/PaginaIngreso';

/** Pantallas ya construidas, por id del menú. El resto muestra PaginaPendiente hasta que llegue su historia. */
const PANTALLAS: Partial<Record<string, ReactNode>> = {
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
      { path: 'mas', element: <PaginaMas /> },
      { path: 'mi-cuenta', element: <PaginaMiCuenta /> },
      { path: '*', element: <IrAlInicio /> },
    ],
  },
];
