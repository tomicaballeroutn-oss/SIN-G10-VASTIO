import { Link, useLocation } from 'react-router';
import { Popover } from '../../ds';
import { useSesion } from '../sesion/contexto';
import { ARTICULOS } from './articulos';
import { puedeLeer, rutaArticulo } from './modelo';
import './ayuda.css';

export interface AyudaContextualProps {
  titulo: string;
  explicacion: string;
  articuloId: string;
}

export function AyudaContextual({ titulo, explicacion, articuloId }: AyudaContextualProps) {
  const { usuario } = useSesion();
  const ubicacion = useLocation();
  const articulo = ARTICULOS.find((item) => item.id === articuloId);
  if (!usuario || !articulo || !puedeLeer(articulo, usuario.roles)) return null;
  const volverAyuda = `${ubicacion.pathname}${ubicacion.search}${ubicacion.hash}`;
  return (
    <Popover title={titulo} label={`Ayuda sobre ${titulo.toLowerCase()}`}>
      <p>{explicacion}</p>
      <Link className="ayuda__enlace" to={rutaArticulo(articulo)} state={{ volverAyuda }}>Saber más</Link>
    </Popover>
  );
}
