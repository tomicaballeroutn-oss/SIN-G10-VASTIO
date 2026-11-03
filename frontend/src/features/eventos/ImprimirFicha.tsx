import { useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { hora } from '../../api/catalogos';
import { tieneServicio, type Ficha } from '../../api/eventos';
import { Button, Checkbox, IconButton, Dialog, ESTADO_POR_CODIGO, ESTADOS, type SalonId } from '../../ds';
import { useSesion } from '../sesion/contexto';
import { dinero, fechaCorta, fechaHora } from '../comun/formato';
import './imprimir-ficha.css';

/** Partes opcionales de la ficha impresa. Cliente, evento, contrato y cancelación van siempre. */
export interface OpcionesImpresion {
  servicios: boolean;
  contactos: boolean;
  sena: boolean;
  observaciones: boolean;
}

/** En papel, Santa Bárbara usa su variante positiva (M-04): la original es blanca. */
const LOGO_IMPRESO: Record<SalonId, string> = {
  avril: '/brand/salones/avril.png',
  club: '/brand/salones/club.png',
  'santa-barbara': '/brand/salones/santa-barbara-positivo.png',
};

const CLASE_BODY = 'ficha-imprimiendo';

/**
 * «Imprimir ficha»: elige qué partes llevar y abre el diálogo de impresión del navegador
 * (desde ahí también se guarda como PDF). Imprime solo lo que el backend ya mandó a este perfil.
 */
export function ImprimirFicha({ ficha }: { ficha: Ficha }) {
  const [eligiendo, setEligiendo] = useState(false);
  const [impresion, setImpresion] = useState<{ opciones: OpcionesImpresion; momento: string } | null>(null);

  useEffect(() => {
    if (!impresion) return undefined;
    const terminar = () => {
      document.body.classList.remove(CLASE_BODY);
      setImpresion(null);
    };
    document.body.classList.add(CLASE_BODY);
    window.addEventListener('afterprint', terminar);
    window.print();
    return () => {
      window.removeEventListener('afterprint', terminar);
      document.body.classList.remove(CLASE_BODY);
    };
  }, [impresion]);

  return (
    <>
      <IconButton icon="printer" label="Imprimir ficha" variant="outline" onClick={() => setEligiendo(true)} />
      {eligiendo && (
        <DialogoImprimir
          ficha={ficha}
          alCerrar={() => setEligiendo(false)}
          alImprimir={(opciones) => {
            setEligiendo(false);
            setImpresion({ opciones, momento: new Date().toISOString() });
          }}
        />
      )}
      {impresion && createPortal(<FichaImpresa ficha={ficha} {...impresion} />, document.body)}
    </>
  );
}

function DialogoImprimir({ ficha, alCerrar, alImprimir }: {
  ficha: Ficha;
  alCerrar: () => void;
  alImprimir: (opciones: OpcionesImpresion) => void;
}) {
  const [opciones, setOpciones] = useState<OpcionesImpresion>({ servicios: true, contactos: true, sena: false, observaciones: false });
  const cambiar = (clave: keyof OpcionesImpresion) => setOpciones((o) => ({ ...o, [clave]: !o[clave] }));

  return (
    <Dialog
      open
      title="Imprimir ficha"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="printer" onClick={() => alImprimir(opciones)}>Imprimir ficha</Button>
        </>
      }
    >
      <div className="imprimir-ficha__opciones">
        <p className="body">Los datos del cliente y del evento van siempre. Elegí qué más querés incluir.</p>
        <Checkbox label="Servicios contratados" checked={opciones.servicios} onChange={() => cambiar('servicios')} />
        <Checkbox label="Otros contactos" checked={opciones.contactos} onChange={() => cambiar('contactos')} />
        {ficha.sena && (
          <Checkbox label="Seña y pago" checked={opciones.sena} onChange={() => cambiar('sena')} />
        )}
        <Checkbox label="Observaciones internas" checked={opciones.observaciones} onChange={() => cambiar('observaciones')} />
      </div>
    </Dialog>
  );
}

function Fila({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div className="ficha-impresa__fila">
      <dt>{etiqueta}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function Bloque({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="ficha-impresa__bloque">
      <h2>{titulo}</h2>
      {children}
    </section>
  );
}

const SIN_DATO = '—';

/** La hoja: oculta en pantalla, es lo único que se ve al imprimir. */
export function FichaImpresa({ ficha, opciones, momento }: { ficha: Ficha; opciones: OpcionesImpresion; momento: string }) {
  const { usuario } = useSesion();
  const { cliente, sena } = ficha;
  const servicios = ficha.servicios.filter(tieneServicio);
  const estado = ESTADOS[ESTADO_POR_CODIGO[ficha.estado]].label;

  return (
    <article className="ficha-impresa">
      <header className="ficha-impresa__cabecera">
        <div>
          <p className="ficha-impresa__codigo">{ficha.codigo} · {estado}</p>
          <h1>{ficha.nombre}</h1>
          <p>
            {ficha.salon.nombre} · {fechaCorta(ficha.fecha, true)} · {ficha.turno.nombre} ({hora(ficha.turno.horaInicio)} a {hora(ficha.turno.horaFin)})
          </p>
        </div>
        <img className="ficha-impresa__logo" src={LOGO_IMPRESO[ficha.salon.codigo]} alt={ficha.salon.nombre} />
      </header>

      <div className="ficha-impresa__columnas">
        <Bloque titulo="Evento">
          <dl>
            <Fila etiqueta="Tipo">{ficha.tipo.nombre}</Fila>
            <Fila etiqueta="Hora de inicio">
              {hora(ficha.horaInicio ?? ficha.turno.horaInicio)}
            </Fila>
            <Fila etiqueta="Invitados">
              {ficha.cantidadInvitados == null
                ? 'Sin definir'
                : `${ficha.cantidadInvitados}${ficha.invitadosDefinitivos ? ' (definitivos)' : ' (previstos)'}`}
            </Fila>
            <Fila etiqueta="Vendedora">{ficha.vendedora.nombre}</Fila>
            <Fila etiqueta="Planner">{ficha.planner?.nombre ?? 'Sin asignar'}</Fila>
            {ficha.reprogramadoDesde && <Fila etiqueta="Fecha original">{ficha.reprogramadoDesde}</Fila>}
            {ficha.fechaFirmaContrato && <Fila etiqueta="Contrato firmado">{fechaCorta(ficha.fechaFirmaContrato, true)}</Fila>}
          </dl>
        </Bloque>
        <Bloque titulo="Cliente">
          <dl>
            <Fila etiqueta="Nombre">{cliente.nombre}</Fila>
            <Fila etiqueta="Documento">{cliente.documento ?? SIN_DATO}</Fila>
            <Fila etiqueta="Teléfono">{cliente.telefono ?? SIN_DATO}</Fila>
            <Fila etiqueta="Correo">{cliente.email ?? SIN_DATO}</Fila>
          </dl>
        </Bloque>
      </div>

      {opciones.contactos && ficha.contactos.length > 0 && (
        <Bloque titulo="Otros contactos">
          <dl>
            {ficha.contactos.map((c) => (
              <Fila key={c.id} etiqueta={c.vinculo ?? 'Contacto'}>
                {[c.nombre, c.telefono, c.email].filter(Boolean).join(' · ')}
              </Fila>
            ))}
          </dl>
        </Bloque>
      )}

      {ficha.cancelacion && (
        <Bloque titulo="Cancelación">
          <dl>
            <Fila etiqueta="Motivo">{ficha.cancelacion.motivo}</Fila>
            <Fila etiqueta="Detalle">{ficha.cancelacion.detalle ?? SIN_DATO}</Fila>
          </dl>
        </Bloque>
      )}

      {opciones.servicios && (
        <Bloque titulo="Servicios contratados">
          {servicios.length === 0 ? <p>Sin servicios cargados.</p> : servicios.map((s) => (
            <div key={s.categoriaId} className="ficha-impresa__servicio">
              <h3>{s.categoria}</h3>
              {s.renglones.length > 0 && (
                <ul>
                  {s.renglones.map((r, i) => <li key={i}>{r.cantidad != null ? `${r.cantidad} · ` : ''}{r.texto}</li>)}
                </ul>
              )}
              {s.descripcion && <p>{s.descripcion}</p>}
            </div>
          ))}
        </Bloque>
      )}

      {opciones.sena && sena && (
        <Bloque titulo="Seña">
          <dl>
            {sena.importe != null && <Fila etiqueta="Importe">{dinero(sena.importe, sena.moneda ?? 'ARS')}</Fila>}
            <Fila etiqueta="Fecha del pago">{fechaCorta(sena.fecha, true)}</Fila>
            <Fila etiqueta="Firmante">
              {[sena.firmanteNombre, sena.firmanteDni && `DNI ${sena.firmanteDni}`].filter(Boolean).join(' · ') || SIN_DATO}
            </Fila>
            <Fila etiqueta="Contacto del firmante">{sena.firmanteContacto ?? SIN_DATO}</Fila>
          </dl>
        </Bloque>
      )}

      {opciones.observaciones && (
        <Bloque titulo="Observaciones internas">
          <p className="ficha-impresa__observaciones">{ficha.observacionesInternas || 'Sin observaciones.'}</p>
        </Bloque>
      )}

      <footer className="ficha-impresa__pie">
        <span>Impreso por {usuario?.nombreCompleto ?? SIN_DATO} el {fechaHora(momento)}</span>
        <img src="/brand/vastio-logo.png" alt="Vastio" />
      </footer>
    </article>
  );
}
