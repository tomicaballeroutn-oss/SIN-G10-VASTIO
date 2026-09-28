import { createBrowserRouter, RouterProvider } from 'react-router';
import { SesionProvider } from './features/sesion/SesionProvider';
import { rutas } from './rutas';

const router = createBrowserRouter(rutas);

export function App() {
  return (
    <SesionProvider>
      <RouterProvider router={router} />
    </SesionProvider>
  );
}
