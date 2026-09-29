import { createBrowserRouter, RouterProvider } from 'react-router';
import { PaginaDs } from './features/ds/PaginaDs';
import { Inicio } from './features/inicio/Inicio';

const router = createBrowserRouter([
  { path: '/', element: <Inicio /> },
  { path: '/_ds', element: <PaginaDs /> },
]);

export function App() {
  return <RouterProvider router={router} />;
}
