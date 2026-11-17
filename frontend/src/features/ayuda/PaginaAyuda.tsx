import { useEffect } from 'react';
import { Link, useLocation } from 'react-router';
import { Card } from '../../ds';
import { Encabezado } from '../layout/paginas';
import { rutaDeInicio } from '../layout/menu';
import { useSesion } from '../sesion/contexto';
import { ARTICULOS, CATEGORIAS, GLOSARIO } from './articulos';
import { puedeLeer, rutaArticulo } from './modelo';
import './ayuda.css';

export function PaginaAyuda() {
  const { usuario } = useSesion();
  const roles = usuario?.roles ?? [];
  const { hash } = useLocation();
  useEffect(() => {
    const categoria = document.getElementById(hash.slice(1));
    categoria?.scrollIntoView({ block: 'start' });
    categoria?.focus();
  }, [hash]);
  return (
    <section className="pantalla ayuda">
      <Encabezado titulo="Centro de ayuda" />
      <p className="body v-muted">Encontrá instrucciones para usar Vastio. Elegí un artículo de las funciones habilitadas para tu perfil.</p>
      <div className="ayuda__categorias">
        {CATEGORIAS.map((categoria) => {
          const articulos = ARTICULOS.filter((articulo) => articulo.categoria === categoria.id && puedeLeer(articulo, roles));
          if (articulos.length === 0) return null;
          return (
            <Card key={categoria.id} className="ayuda__categoria">
              <h2 className="h3" id={categoria.id} tabIndex={-1}>{categoria.titulo}</h2>
              <ul className="ayuda__lista" aria-labelledby={categoria.id}>
                {articulos.map((articulo) => (
                  <li key={articulo.id}><Link className="ayuda__enlace" to={rutaArticulo(articulo)}>{articulo.titulo}</Link></li>
                ))}
              </ul>
            </Card>
          );
        })}
      </div>
      <Card><Link className="ayuda__enlace" to={rutaArticulo(GLOSARIO)}>{GLOSARIO.titulo}</Link></Card>
      <div><Link className="ayuda__enlace" to={rutaDeInicio(roles)}>Volver a la aplicación</Link></div>
    </section>
  );
}
