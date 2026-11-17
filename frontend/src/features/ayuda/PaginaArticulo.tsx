import { useEffect, useRef } from 'react';
import { Link, useParams } from 'react-router';
import { Card, EmptyState } from '../../ds';
import { useSesion } from '../sesion/contexto';
import { ARTICULOS, CATEGORIAS, GLOSARIO } from './articulos';
import { CONTENIDO, TERMINOS } from './contenido';
import { puedeLeer } from './modelo';
import { VideoTutorial } from './VideoTutorial';
import './ayuda.css';

export function PaginaArticulo() {
  const { categoria, articulo: id } = useParams();
  const { usuario } = useSesion();
  const roles = usuario?.roles ?? [];
  const articulo = [...ARTICULOS, GLOSARIO].find((item) => item.categoria === categoria && item.id === id);
  const titulo = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    titulo.current?.focus();
    window.scrollTo({ top: 0 });
  }, [categoria, id]);

  if (!articulo || !puedeLeer(articulo, roles)) {
    return (
      <section className="pantalla ayuda">
        <h1 ref={titulo} tabIndex={-1}>Centro de ayuda</h1>
        <Card flush><EmptyState icon="lock" title="Este artículo no está disponible">
          Volvé al centro de ayuda para consultar los artículos habilitados para tu perfil.
        </EmptyState></Card>
        <Link className="ayuda__enlace" to="/ayuda">Volver al centro de ayuda</Link>
      </section>
    );
  }

  const esGlosario = articulo.id === GLOSARIO.id;
  const contenido = CONTENIDO[articulo.id];
  const categoriaActual = CATEGORIAS.find((item) => item.id === categoria)!;
  return (
    <article className="pantalla ayuda ayuda--articulo">
      <nav className="ayuda__navegacion" aria-label="Navegación de ayuda">
        <Link className="ayuda__enlace" to="/ayuda">Centro de ayuda</Link>
        <Link className="ayuda__enlace" to={`/ayuda#${categoriaActual.id}`}>{categoriaActual.titulo}</Link>
      </nav>
      <header className="pantalla__titulos">
        <h1 ref={titulo} tabIndex={-1}>{articulo.titulo}</h1>
        <p className="body v-muted">{esGlosario ? 'Consultá el significado de los términos que utiliza Vastio.' : contenido.descripcion}</p>
      </header>
      <Card>
        {esGlosario ? (
          <dl className="ayuda__glosario">
            {TERMINOS.map(({ termino, definicion }) => <div key={termino}><dt className="label">{termino}</dt><dd>{definicion}</dd></div>)}
          </dl>
        ) : contenido.secciones.filter((seccion) => !seccion.roles || seccion.roles.some((rol) => roles.includes(rol))).map((seccion) => (
          <section className="ayuda__seccion" key={seccion.titulo}>
            <h2 className="h3">{seccion.titulo}</h2>
            {seccion.pasos && <ol className="ayuda__pasos">{seccion.pasos.map((paso) => <li key={paso}>{paso}</li>)}</ol>}
            {seccion.notas?.map((nota) => <p key={nota}>{nota}</p>)}
          </section>
        ))}
        <VideoTutorial url={articulo.videoUrl} titulo={articulo.titulo} />
      </Card>
      <div><Link className="ayuda__enlace" to={`/ayuda#${categoriaActual.id}`}>Volver a {categoriaActual.titulo}</Link></div>
    </article>
  );
}
