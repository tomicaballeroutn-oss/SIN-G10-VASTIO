import { urlInsercionYoutube, urlVideoValida } from './video';

export function VideoTutorial({ url, titulo, relacionAspecto = 16 / 9 }: { url?: string; titulo: string; relacionAspecto?: number }) {
  const fuente = urlVideoValida(url);
  if (!fuente) return null;
  const insercion = urlInsercionYoutube(fuente);
  const aspecto = relacionAspecto > 0 && Number.isFinite(relacionAspecto) ? relacionAspecto : 16 / 9;
  return (
    <section className="ayuda__seccion" aria-label="Video tutorial">
      <h2 className="h3">Video tutorial</h2>
      {insercion ? <iframe
        key={insercion}
        className="ayuda__youtube"
        style={{ aspectRatio: aspecto, maxWidth: `${75 * aspecto}vh` }}
        src={insercion}
        title={`Video tutorial de ${titulo}`}
        loading="lazy"
        allow="accelerometer; encrypted-media; gyroscope; picture-in-picture; fullscreen"
        allowFullScreen
        referrerPolicy="strict-origin-when-cross-origin"
      /> : <video key={fuente} className="ayuda__video" controls preload="none" aria-label={`Video tutorial de ${titulo}`}>
        <source src={fuente} />
        Tu navegador requiere abrir el video por separado.
      </video>}
      <a className="ayuda__enlace" href={fuente} target="_blank" rel="noreferrer">{insercion ? 'Abrir video en YouTube' : 'Abrir video en otra pestaña'}</a>
    </section>
  );
}
