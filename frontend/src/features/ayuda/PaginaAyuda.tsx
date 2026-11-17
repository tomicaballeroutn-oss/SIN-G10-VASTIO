import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { Button, Card, EmptyState, Icon, Input } from '../../ds';
import { Encabezado } from '../layout/paginas';
import { rutaDeInicio } from '../layout/menu';
import { useSesion } from '../sesion/contexto';
import { ARTICULOS, CATEGORIAS, GLOSARIO } from './articulos';
import { buscarArticulos } from './busqueda';
import { puedeLeer, rutaArticulo } from './modelo';
import './ayuda.css';

export function PaginaAyuda() {
  const { usuario } = useSesion();
  const roles = usuario?.roles ?? [];
  const [busqueda, setBusqueda] = useState('');
  const buscando = busqueda.trim().length > 0;
  const resultados = buscando ? buscarArticulos(busqueda, roles) : [];
  const { hash } = useLocation();
  useEffect(() => {
    const categoria = document.getElementById(hash.slice(1));
    categoria?.scrollIntoView({ block: 'start' });
    categoria?.focus();
  }, [hash]);

  function limpiarBusqueda() {
    setBusqueda('');
    document.getElementById('ayuda-busqueda')?.focus();
  }

  return (
    <section className="pantalla ayuda">
      <Encabezado titulo="Centro de ayuda" />
      <p className="body v-muted">Encontrá instrucciones para usar Vastio. Elegí un artículo de las funciones habilitadas para tu perfil.</p>
      <div role="search" aria-label="Buscar artículos de ayuda" className="ayuda__busqueda">
        <Input id="ayuda-busqueda" label="Buscar en la ayuda" type="search" icon="search"
          placeholder="Título, categoría o contenido" value={busqueda} onChange={(evento) => setBusqueda(evento.target.value)} />
        {busqueda.length > 0 && <Button variant="outline" onClick={limpiarBusqueda}>Limpiar búsqueda</Button>}
      </div>
      {buscando ? (
        <section className="ayuda__resultados" aria-label="Resultados de búsqueda">
          <p className="body-sm v-muted" role="status">{resultados.length === 1 ? '1 artículo encontrado' : `${resultados.length} artículos encontrados`}</p>
          <Card>
            {resultados.length > 0 ? (
              <ul className="ayuda__lista" aria-label="Artículos encontrados">
                {resultados.map((articulo) => (
                  <li key={articulo.id}>
                    <Link className="ayuda__enlace" to={rutaArticulo(articulo)}>{articulo.titulo}</Link>
                    <p className="body-sm v-muted">{CATEGORIAS.find((categoria) => categoria.id === articulo.categoria)!.titulo}</p>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState icon="search" title="No encontramos artículos">
                Probá con otras palabras o seleccioná Limpiar búsqueda.
              </EmptyState>
            )}
          </Card>
        </section>
      ) : (
        <>
          <div className="ayuda__categorias">
            {CATEGORIAS.map((categoria) => {
              const articulos = ARTICULOS.filter((articulo) => articulo.categoria === categoria.id && puedeLeer(articulo, roles));
              if (articulos.length === 0) return null;
              return (
                <Card key={categoria.id} className="ayuda__categoria">
                  <h2 className="h3" id={categoria.id} tabIndex={-1}>{categoria.titulo}</h2>
                  <ul className="ayuda__lista" aria-labelledby={categoria.id}>
                    {articulos.map((articulo) => (
                      <li key={articulo.id}>
                        <Link className="ayuda__enlace ayuda__articulo" to={rutaArticulo(articulo)}>
                          <span>{articulo.titulo}</span>
                          <Icon name="chevron-right" size={18} />
                        </Link>
                      </li>
                    ))}
                  </ul>
                </Card>
              );
            })}
          </div>
          <Card><Link className="ayuda__enlace" to={rutaArticulo(GLOSARIO)}>{GLOSARIO.titulo}</Link></Card>
        </>
      )}
      <div><Link className="ayuda__enlace" to={rutaDeInicio(roles)}>Volver a la aplicación</Link></div>
    </section>
  );
}
