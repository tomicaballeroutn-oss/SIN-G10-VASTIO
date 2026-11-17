import { urlVideoValida } from './video';

export function VideoTutorial({ url, titulo }: { url?: string; titulo: string }) {
  const fuente = urlVideoValida(url);
  if (!fuente) return null;
  return (
    <section className="ayuda__seccion" aria-label="Video tutorial">
      <h2 className="h3">Video tutorial</h2>
      <video key={fuente} className="ayuda__video" controls preload="none" aria-label={`Video tutorial de ${titulo}`}>
        <source src={fuente} />
        Tu navegador requiere abrir el video por separado.
      </video>
      <a className="ayuda__enlace" href={fuente} target="_blank" rel="noreferrer">Abrir video en otra pestaña</a>
    </section>
  );
}
