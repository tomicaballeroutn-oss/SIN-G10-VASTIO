import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { ESTADO_ORDEN, type OrdenDelEvento, type Orden } from '../../api/ordenes';
import { Button, cantidadLegible } from '../../ds';
import { fechaCorta, fechaHora } from '../comun/formato';

const CLASE_BODY = 'orden-imprimiendo';

/** En papel, Santa Bárbara usa su variante positiva (M-04), como la ficha impresa. */
const LOGO_SALON: Record<string, string> = {
  avril: '/brand/salones/avril.png',
  club: '/brand/salones/club.png',
  'santa-barbara': '/brand/salones/santa-barbara-positivo.png',
};

function momento(): string {
  return new Intl.DateTimeFormat('es-AR', { timeZone: 'America/Argentina/Cordoba', dateStyle: 'long', timeStyle: 'short' }).format(new Date());
}

/**
 * «Vista para imprimir» de la orden de preparación: una hoja propia para el depósito (no la pantalla impresa), con lo
 * que hay que retirar en cajas y botellas, una casilla por bebida y lugar para firmar.
 */
export function ImprimirOrden({ consulta, origen }: { consulta: OrdenDelEvento & { orden: Orden }; origen: string }) {
  const [impresion, setImpresion] = useState<string | null>(null);

  useEffect(() => {
    if (!impresion) return undefined;
    const terminar = () => {
      document.body.classList.remove(CLASE_BODY);
      setImpresion(null);
    };
    document.body.classList.add(CLASE_BODY);
    window.addEventListener('afterprint', terminar);
    // Que cargue el logo del salón antes de abrir el diálogo de impresión.
    const logo = document.querySelector<HTMLImageElement>('.orden-impresa img');
    const listo = !logo || logo.complete ? Promise.resolve() : new Promise<void>((ok) => {
      logo.addEventListener('load', () => ok(), { once: true });
      logo.addEventListener('error', () => ok(), { once: true });
      setTimeout(ok, 3000);
    });
    let vigente = true;
    void listo.then(() => { if (vigente) window.print(); });
    return () => {
      vigente = false;
      window.removeEventListener('afterprint', terminar);
      document.body.classList.remove(CLASE_BODY);
    };
  }, [impresion]);

  return (
    <>
      <Button variant="outline" icon="printer" onClick={() => setImpresion(momento())}>Vista para imprimir</Button>
      {impresion && createPortal(<OrdenImpresa consulta={consulta} origen={origen} generado={impresion} />, document.body)}
    </>
  );
}

export function OrdenImpresa({ consulta, origen, generado }: { consulta: OrdenDelEvento & { orden: Orden }; origen: string; generado: string }) {
  const { evento, orden } = consulta;
  const totalBotellas = orden.renglones.reduce((suma, r) => suma + r.aRetirar, 0);
  return (
    <article className="orden-impresa" aria-label="Orden de preparación para imprimir">
      <header className="orden-impresa__cabecera">
        <div>
          <p className="orden-impresa__sobre">Orden de preparación · {ESTADO_ORDEN[orden.estado].label}</p>
          <h1>{evento.nombre}</h1>
          <p className="orden-impresa__dato">
            {evento.codigo} · {evento.salon.nombre} · {fechaCorta(evento.fecha, true)}
            {evento.asistentes != null && <> · {evento.asistentes} asistentes previstos</>}
          </p>
        </div>
        {LOGO_SALON[evento.salon.codigo] && <img className="orden-impresa__logo" src={LOGO_SALON[evento.salon.codigo]} alt={evento.salon.nombre} />}
      </header>

      <p className="orden-impresa__nota">{origen} Margen: {orden.margenPorcentaje} %. Sale del depósito principal.</p>

      {orden.renglones.length === 0 ? (
        <p className="orden-impresa__vacia">La orden no tiene bebidas.</p>
      ) : (
        <table className="orden-impresa__tabla">
          <thead>
            <tr>
              <th scope="col">Bebida</th>
              <th scope="col" className="is-num">A retirar</th>
              <th scope="col" className="is-num">Botellas</th>
              <th scope="col" className="is-check">Preparado</th>
            </tr>
          </thead>
          <tbody>
            {orden.renglones.map((r) => (
              <tr key={r.bebidaId}>
                <td><strong>{r.nombre}</strong> <span className="orden-impresa__presentacion">{r.presentacion}{r.activa ? '' : ' · dada de baja'}</span></td>
                <td className="is-num">{cantidadLegible(r.aRetirar, r.unidadesPorBulto, r.unidad)}</td>
                <td className="is-num">{r.aRetirar}</td>
                <td className="is-check"><span className="orden-impresa__casilla" aria-hidden /></td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <th scope="row">{orden.renglones.length} {orden.renglones.length === 1 ? 'bebida' : 'bebidas'}</th>
              <td />
              <td className="is-num">{totalBotellas}</td>
              <td />
            </tr>
          </tfoot>
        </table>
      )}

      <footer className="orden-impresa__pie">
        <p>
          {orden.confirmacion
            ? <>Confirmada por {orden.confirmacion.usuario?.nombre ?? 'Sistema'} · {fechaHora(orden.confirmacion.fechaHora)}</>
            : <>Todavía no está confirmada: las cantidades pueden cambiar.</>}
        </p>
        <div className="orden-impresa__firmas">
          <span>Preparó</span>
          <span>Recibió</span>
        </div>
        <p className="orden-impresa__generado">Vastio · generada el {generado}</p>
      </footer>
    </article>
  );
}
