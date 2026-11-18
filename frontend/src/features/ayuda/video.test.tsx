import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { ARTICULOS } from './articulos';
import { VideoTutorial } from './VideoTutorial';
import { urlInsercionYoutube, urlVideoValida } from './video';

afterEach(cleanup);

describe('fuentes de tutoriales', () => {
  it('conserva archivos HTTPS directos, incluidos puertos y parámetros', () => {
    expect(urlVideoValida('https://media.ejemplo.com:8443/tutorial.mp4?v=2')).toBe('https://media.ejemplo.com:8443/tutorial.mp4?v=2');
    expect(urlInsercionYoutube('https://media.ejemplo.com/tutorial.webm')).toBeUndefined();
  });
  it.each([
    'https://youtu.be/HaqrhVjUWCg?si=compartir',
    'https://youtube.com/watch?v=HaqrhVjUWCg&t=10',
    'https://www.youtube.com/watch?v=HaqrhVjUWCg',
    'https://m.youtube.com/watch?v=HaqrhVjUWCg',
    'https://www.youtube.com/shorts/HaqrhVjUWCg?feature=share',
  ])('normaliza %s y genera una inserción segura', (url) => {
    expect(urlVideoValida(url)).toBe('https://www.youtube.com/watch?v=HaqrhVjUWCg');
    expect(urlInsercionYoutube(url)).toBe('https://www.youtube-nocookie.com/embed/HaqrhVjUWCg');
  });

  it.each([
    'http://youtu.be/HaqrhVjUWCg', 'https://youtube.com.evil.test/watch?v=HaqrhVjUWCg',
    'https://evil.test/watch?v=HaqrhVjUWCg', 'https://youtube.com@evil.test/watch?v=HaqrhVjUWCg',
    'https://user@youtube.com/watch?v=HaqrhVjUWCg', 'https://youtube.com:444/watch?v=HaqrhVjUWCg',
    'https://youtu.be/HaqrhVjUWCg/otro', 'https://youtu.be/corto', 'https://youtube.com/watch',
    'https://youtube.com/watch?v=HaqrhVjUWCg&v=_IvVyLp1BkI',
    'https://youtube.com/shorts/HaqrhVjUWCg/otro', 'https://youtu.be/HaqrhVjUWCg%22',
    'https://youtube.com/video.mp4', 'https://youtube.com/embed/HaqrhVjUWCg',
    'javascript:alert(1)', 'data:text/html,video',
  ])('rechaza %s sin crear reproductores ni enlaces', (url) => {
    expect(urlVideoValida(url)).toBeUndefined();
    expect(urlInsercionYoutube(url)).toBeUndefined();
    const { container } = render(<VideoTutorial url={url} titulo="Agenda" />);
    expect(container).toBeEmptyDOMElement();
  });

  const asociaciones = [
    ['HaqrhVjUWCg', ['utilizar-agenda']], ['_IvVyLp1BkI', ['ficha-historial']],
    ['I7zpEWDAP-I', ['pre-reservas-senas']], ['kpOcrk667Ic', ['confirmacion-reprogramacion']],
    ['CnYJ7_p4fmg', ['existencias', 'ingresos-ajustes']],
    ['NVNaKzVUP3w', ['retiros', 'devoluciones']], ['qygweTi5ZvQ', ['cierre']],
    ['bWLLdKbdjXw', ['ocupacion', 'estadisticas-consumo']],
  ] as const;
  it.each(asociaciones)('configura %s en los artículos correspondientes', (id, articulos) => {
    for (const nombre of articulos) {
      const articulo = ARTICULOS.find((a) => a.id === nombre)!;
      expect(urlInsercionYoutube(articulo.videoUrl!)).toBe(`https://www.youtube-nocookie.com/embed/${id}`);
    }
    expect(ARTICULOS.filter((a) => a.videoUrl && urlInsercionYoutube(a.videoUrl)?.endsWith(id)).map((a) => a.id)).toEqual(articulos);
  });

  it('presenta un iframe accesible, sin autoplay, con alternativa y reemplazo de fuente', () => {
    const { container, rerender } = render(<VideoTutorial url="https://youtu.be/NVNaKzVUP3w" titulo="Devoluciones" relacionAspecto={1080 / 2162} />);
    const iframe = screen.getByTitle('Video tutorial de Devoluciones');
    expect(iframe).toHaveAttribute('src', 'https://www.youtube-nocookie.com/embed/NVNaKzVUP3w');
    expect(iframe).toHaveAttribute('loading', 'lazy');
    expect(iframe).toHaveAttribute('allowfullscreen');
    expect(iframe).toHaveAttribute('referrerpolicy', 'strict-origin-when-cross-origin');
    expect(screen.getByRole('link', { name: 'Abrir video en YouTube' })).toHaveAttribute('href', 'https://www.youtube.com/watch?v=NVNaKzVUP3w');
    expect(container.querySelector('video')).toBeNull();
    rerender(<VideoTutorial url="https://ejemplo.com/video.ogv" titulo="Agenda" />);
    expect(container.querySelector('iframe')).toBeNull();
    expect(container.querySelector('source')).toHaveAttribute('src', 'https://ejemplo.com/video.ogv');
    rerender(<VideoTutorial titulo="Sin tutorial" />);
    expect(container).toBeEmptyDOMElement();
  });
});
