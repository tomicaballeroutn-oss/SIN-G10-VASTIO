import type { RouteObject } from 'react-router';
import { PaginaDs } from './features/ds/PaginaDs';
import { Layout } from './features/layout/Layout';
import { MENU } from './features/layout/menu';
import { IrAlInicio, PaginaMas, PaginaMiCuenta, PaginaPendiente } from './features/layout/paginas';
import { PaginaIngreso } from './features/sesion/PaginaIngreso';


export const rutas: RouteObject[] = [
  { path: '/login', element: <PaginaIngreso /> },
  { path: '/_ds', element: <PaginaDs /> },
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <IrAlInicio /> },
      // Cada ítem del menú tiene su ruta; las pantallas reales reemplazan a PaginaPendiente con su historia.
      ...MENU.map((item) => ({ path: item.ruta.slice(1), element: <PaginaPendiente item={item} /> })),
      { path: 'mas', element: <PaginaMas /> },
      { path: 'mi-cuenta', element: <PaginaMiCuenta /> },
      { path: '*', element: <IrAlInicio /> },
    ],
  },
];
