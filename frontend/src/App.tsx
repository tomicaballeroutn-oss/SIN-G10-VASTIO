import { createBrowserRouter, RouterProvider } from 'react-router';
import { Bienvenida } from './features/layout/Bienvenida';
import { SesionProvider } from './features/sesion/SesionProvider';
import { rutas } from './rutas';

const router = createBrowserRouter(rutas);

export function App() {
  return (
    <SesionProvider>
      <RouterProvider router={router} />
      <Bienvenida />
    </SesionProvider>
  );
}
