import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { fechaLarga, hoyEnCordoba, sumarMeses } from '../../api/agenda';
import { hora } from '../../api/catalogos';
import { cocina, type Cocina, type TarjetaCocina } from '../../api/cocina';
import { ProblemaApi } from '../../api/problema';
import { useDatos } from '../../api/useDatos';
import { AgendaGrid, Alert, Button, Card, SalonTag, type AgendaEvent } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { Encabezado } from '../layout/paginas';
import '../agenda/agenda.css';
import './cocina.css';

const MES_VALIDO = /^\d{4}-(0[1-9]|1[0-2])$/;

/**
 * A7-02 · Consultar vista de cocina (UI-23): el calendario de la agenda, en solo lectura y sin estados, y al elegir un
 * día una tarjeta por evento con lo que hay que preparar. Solo eventos Confirmados y En curso. Mes, día y evento quedan
 * en la dirección: un aviso de Cocina lleva a `/cocina?evento=ID`, que abre su día y resalta su tarjeta.
 */
export function PaginaCocina() {
  const [busqueda, setBusqueda] = useSearchParams();
  const pedido = busqueda.get('mes') ?? '';
  const mes = MES_VALIDO.test(pedido) ? pedido : hoyEnCordoba().slice(0, 7);
  const diaPedido = busqueda.get('dia');
  const eventoPedido = Number(busqueda.get('evento')) || null;
  const [aviso, setAviso] = useState<{ evento: number; mensaje: string } | null>(null);
  const [intento, setIntento] = useState(0);

  const cargar = useCallback(() => cocina.mes(mes), [mes]);
  const datos = useDatos(cargar);

  function cambiar(valores: Record<string, string | null>) {
    const siguiente = new URLSearchParams(busqueda);
    for (const [clave, valor] of Object.entries(valores)) {
      if (valor) siguiente.set(clave, valor);
      else siguiente.delete(clave);
    }
    setBusqueda(siguiente, { replace: true });
  }

  // Desde un aviso: se busca el evento para abrir su mes y su día. Si ya no está en la vista, se dice por qué.
  const resuelto = useRef<number | null>(null);
  useEffect(() => {
    if (!eventoPedido) {
      resuelto.current = null;
      return;
    }
    if (resuelto.current === eventoPedido) return;
    resuelto.current = null;
    let vigente = true;
    cocina.evento(eventoPedido).then(
      (t) => {
        if (!vigente) return;
        resuelto.current = eventoPedido;
        setAviso(null);
        const siguiente = new URLSearchParams(busqueda);
        siguiente.set('mes', t.fecha.slice(0, 7));
        siguiente.set('dia', t.fecha);
        setBusqueda(siguiente, { replace: true });
      },
      (error: unknown) => {
        if (vigente) setAviso({ evento: eventoPedido, mensaje: error instanceof ProblemaApi ? error.message : 'No pudimos abrir ese evento. Volvé a intentarlo.' });
      },
    );
    return () => { vigente = false; };
  }, [eventoPedido, busqueda, setBusqueda, intento]);

  const [anio, numeroMes] = mes.split('-').map(Number);

  return (
    <section className="pantalla">
      <Encabezado titulo="Vista de cocina" antetitulo="Agenda" />
      {aviso?.evento === eventoPedido && <Alert tone="warning" title="No pudimos abrir ese evento"
        action={<Button variant="outline" size="sm" onClick={() => setIntento((n) => n + 1)}>Reintentar</Button>}>
        {aviso.mensaje}
      </Alert>}
      <Cargando datos={datos}>
        {(c) => {
          const dia = diaPedido?.startsWith(mes) ? diaPedido : c.hoy.startsWith(mes) ? c.hoy : null;
          return (
            <>
              {c.hasta && <p className="body-sm v-muted">Ves los eventos confirmados desde hoy hasta el {fechaLarga(c.hasta)}.</p>}
              <div className="agenda">
                <Card className="agenda__mes">
                  <AgendaGrid
                    year={anio}
                    month={numeroMes - 1}
                    events={[...c.eventos, ...c.jornadasAnteriores].map((e): AgendaEvent => ({ date: e.fecha, salon: e.salon.codigo, turno: e.turno, status: 'confirmado' }))}
                    soloOcupacion
                    today={c.hoy}
                    selected={dia ?? undefined}
                    onSelectDay={(iso) => cambiar({ dia: iso, evento: null })}
                    onPrev={() => cambiar({ mes: sumarMeses(mes, -1), dia: null, evento: null })}
                    onNext={() => cambiar({ mes: sumarMeses(mes, 1), dia: null, evento: null })}
                    salones={c.salones}
                  />
                  <p className="caption v-muted">Cada punto lleno es un evento para cocinar.</p>
                </Card>
                <div className="agenda__lateral">
                  {c.jornadasAnteriores.length > 0 && <div className="cocina__dia">
                    <h2 className="h3">Jornadas anteriores</h2>
                    {c.jornadasAnteriores.map((e) => <div key={e.id}>
                      <p className="overline">{fechaLarga(e.fecha)}</p>
                      <TarjetaEvento evento={e} destacada={e.id === eventoPedido} />
                    </div>)}
                  </div>}
                  <DiaDeCocina cocina={c} dia={dia} destacado={eventoPedido} />
                </div>
              </div>
            </>
          );
        }}
      </Cargando>
    </section>
  );
}

/** Los eventos del día elegido, uno por tarjeta, en orden de hora. */
function DiaDeCocina({ cocina: c, dia, destacado }: { cocina: Cocina; dia: string | null; destacado: number | null }) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (dia) ref.current?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' });
  }, [dia, destacado]);

  if (!dia) {
    return (
      <Card>
        <p className="body-sm v-muted">Tocá un día para ver los eventos que hay que preparar.</p>
      </Card>
    );
  }

  const delDia = c.eventos.filter((e) => e.fecha === dia && !c.jornadasAnteriores.some((anterior) => anterior.id === e.id)).sort((a, b) => a.hora.localeCompare(b.hora));
  return (
    <div ref={ref} className="cocina__dia">
      <div className="pantalla__titulos">
        <p className="overline v-muted">Día elegido</p>
        <h2 className="h3">{fechaLarga(dia)}</h2>
      </div>
      {delDia.length ? (
        delDia.map((e) => <TarjetaEvento key={e.id} evento={e} destacada={e.id === destacado} />)
      ) : c.jornadasAnteriores.some((e) => e.fecha === dia) ? null : (
        <Card>
          <p className="body-sm v-muted">{sinEventos(c, dia)}</p>
        </Card>
      )}
    </div>
  );
}

function sinEventos(c: Cocina, dia: string): string {
  if (c.hasta && dia > c.hasta) return `Ves hasta el ${fechaLarga(c.hasta)}: los eventos de ese día aparecen cuando falten menos días.`;
  if (c.hasta && dia < c.hoy) return 'Los días que ya pasaron no se muestran.';
  return 'No hay eventos para cocinar este día.';
}

/** UI-23: nombre, hora, invitados, salón y lo que se sirve. Sin estado, importes ni observaciones internas. */
function TarjetaEvento({ evento: e, destacada }: { evento: TarjetaCocina; destacada: boolean }) {
  const ref = useRef<HTMLElement>(null);
  useEffect(() => {
    if (destacada) ref.current?.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
  }, [destacada]);

  return (
    <article ref={ref} className={destacada ? 'cocina__tarjeta cocina__tarjeta--destacada' : 'cocina__tarjeta'} aria-current={destacada || undefined}>
      <Card eyebrow={e.tipo} title={e.nombre} actions={<span className="numeral cocina__hora">{hora(e.hora)}</span>}>
        <div className="cocina__cuerpo">
          <p className="cocina__datos body">
            <SalonTag salon={e.salon.codigo} label={e.salon.nombre} />
            <span>{e.turnoNombre}</span>
            {e.invitados != null && <span className="numeral-sm">{e.invitados} {e.invitados === 1 ? 'invitado' : 'invitados'}</span>}
          </p>
          {e.servicios.length ? (
            <dl className="cocina__servicios">
              {e.servicios.map((s) => (
                <div key={s.categoria} className="cocina__servicio">
                  <dt className="label">{s.categoria}</dt>
                  {s.renglones.map((r) => (
                    <dd key={`${r.texto}-${r.cantidad}`} className="body">
                      {r.cantidad != null && <span className="numeral-sm cocina__cantidad">{r.cantidad}</span>}
                      {r.texto}
                    </dd>
                  ))}
                  {s.detalle && <dd className="body-sm v-muted cocina__detalle">{s.detalle}</dd>}
                </div>
              ))}
            </dl>
          ) : (
            <p className="body-sm v-muted">Todavía no hay servicios de cocina cargados.</p>
          )}
        </div>
      </Card>
    </article>
  );
}
