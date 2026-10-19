import { useState } from 'react';
import { entregas, type BebidaParaRetiro, type Devolucion, type Entrega } from '../../api/entregas';
import { useEnvio } from '../../api/useDatos';
import { Alert, Button, CantidadEnCajas, Card, Dialog, EmptyState, Table, cantidadLegible } from '../../ds';

interface Declarado {
  devuelto: number;
  remanente: number;
}

const legible = (b: Pick<BebidaParaRetiro, 'unidad' | 'unidadesPorBulto'>, botellas: number) =>
  cantidadLegible(botellas, b.unidadesPorBulto, b.unidad);

/**
 * UI-32 · Registrar devolución de bebida. Por cada bebida de la barra, lo que vuelve al depósito y lo que queda para el
 * evento siguiente, en cajas y botellas enteras: las abiertas cuentan como consumidas. Se declara a ciegas: la pantalla
 * no muestra lo entregado ni lo esperado hasta registrar (decisión 10). Si la barra se abastece de una transición, también
 * se declara lo que queda ahí, que vuelve al depósito madre.
 */
export function DevolucionBarra({ entrega, alCancelar, alRegistrar }: {
  entrega: Entrega;
  /** null si no se puede volver al retiro (evento realizado). */
  alCancelar: (() => void) | null;
  alRegistrar: (d: Devolucion) => void;
}) {
  const [declarado, setDeclarado] = useState<Record<number, Declarado>>({});
  const [transicion, setTransicion] = useState<Record<number, number>>({});
  const [revisando, setRevisando] = useState(false);
  const { guardando, error, enviar, limpiarError } = useEnvio();
  const de = (id: number): Declarado => declarado[id] ?? { devuelto: 0, remanente: 0 };
  const cambiar = (id: number, campo: keyof Declarado, n: number) =>
    setDeclarado((previo) => ({ ...previo, [id]: { ...de(id), [campo]: n } }));
  const conTransicion = entrega.origen.tipo === 'TRANSICION' && entrega.enTransicion.length > 0;

  async function registrar() {
    const registrada = await enviar(() => entregas.devolver(entrega.evento.id, entrega.barra.id, {
      renglones: entrega.aDevolver.map((b) => ({ bebidaId: b.id, ...de(b.id) })),
      transicion: conTransicion ? entrega.enTransicion.map((b) => ({ bebidaId: b.id, cantidad: transicion[b.id] ?? 0 })) : [],
    }));
    if (registrada) {
      setRevisando(false);
      alRegistrar(registrada);
    }
  }

  return (
    <section className="barra__retiro" aria-label="Devolución">
      <h2 className="h4">Devolución de {entrega.barra.nombre}</h2>
      <p className="body-sm v-muted">
        Contá solo las botellas cerradas: las abiertas cuentan como consumidas por este evento.
      </p>

      {entrega.aDevolver.length === 0 ? (
        <Card flush>
          <EmptyState icon="martini" title="No quedan bebidas en la barra">
            Registrá la devolución para cerrar la entrega.
          </EmptyState>
        </Card>
      ) : (
        <ul className="barra__renglones">
          {entrega.aDevolver.map((b) => (
            <li key={b.id} aria-label={`${b.nombre} ${b.presentacion}`}>
              <Card title={b.nombre} subtitle={b.presentacion}>
                <div className="barra__devolucion">
                  <CantidadEnCajas label="Vuelve al depósito" value={de(b.id).devuelto}
                    onChange={(n) => cambiar(b.id, 'devuelto', n)} porBulto={b.unidadesPorBulto} unidad={b.unidad} />
                  <CantidadEnCajas label="Queda en la barra (remanente)" value={de(b.id).remanente}
                    onChange={(n) => cambiar(b.id, 'remanente', n)} porBulto={b.unidadesPorBulto} unidad={b.unidad} />
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}

      {conTransicion && (
        <>
          <h3 className="h4">Queda en {entrega.origen.nombre}</h3>
          <p className="body-sm v-muted">Lo que quedó en la transición vuelve al depósito madre.</p>
          <ul className="barra__renglones">
            {entrega.enTransicion.map((b) => (
              <li key={b.id} aria-label={`${b.nombre} ${b.presentacion} en la transición`}>
                <Card title={b.nombre} subtitle={b.presentacion}>
                  <CantidadEnCajas label="Queda en la transición" value={transicion[b.id] ?? 0}
                    onChange={(n) => setTransicion((p) => ({ ...p, [b.id]: n }))} porBulto={b.unidadesPorBulto} unidad={b.unidad} />
                </Card>
              </li>
            ))}
          </ul>
        </>
      )}

      <div className="barra__pie">
        {alCancelar && <Button variant="outline" size="lg" onClick={alCancelar}>Seguir retirando</Button>}
        <Button size="lg" icon="check" onClick={() => { limpiarError(); setRevisando(true); }}>Revisar devolución</Button>
      </div>

      {revisando && (
        <Dialog
          open
          title="Revisá la devolución"
          onClose={() => setRevisando(false)}
          actions={
            <>
              <Button variant="outline" onClick={() => setRevisando(false)}>Volver a editar</Button>
              <Button icon="check" loading={guardando} onClick={() => void registrar()}>Registrar devolución</Button>
            </>
          }
        >
          <div className="barra__revision">
            {error && <Alert tone="danger">{error.message}</Alert>}
            <p>De {entrega.barra.nombre}, para {entrega.evento.nombre}:</p>
            <ul className="barra__lista">
              {entrega.aDevolver.map((b) => (
                <li key={b.id}>
                  <span>{b.nombre} {b.presentacion}</span>
                  <span className="numeral-sm">vuelve {legible(b, de(b.id).devuelto)} · queda {legible(b, de(b.id).remanente)}</span>
                </li>
              ))}
              {conTransicion && entrega.enTransicion.filter((b) => (transicion[b.id] ?? 0) > 0).map((b) => (
                <li key={`t-${b.id}`}>
                  <span>{b.nombre} {b.presentacion} (transición)</span>
                  <span className="numeral-sm">vuelve {legible(b, transicion[b.id])}</span>
                </li>
              ))}
            </ul>
            <p className="body-sm v-muted">Al registrar se cierra la entrega: no admite más retiros.</p>
          </div>
        </Dialog>
      )}
    </section>
  );
}

/** Recién después de registrar: el consumo real por bebida (entregado − vuelve − queda). */
export function ConsumoDeLaDevolucion({ devolucion }: { devolucion: Devolucion }) {
  return (
    <section className="barra__entregado" aria-label="Consumo real">
      <Alert tone="success" title="Devolución registrada">
        La entrega a {devolucion.entrega.barra.nombre} quedó cerrada. Este es el consumo real de la barra.
      </Alert>
      <Card title="Consumo real" subtitle="Entregado − vuelve al depósito − queda en la barra">
        <Table
          caption="Consumo real por bebida"
          dense
          rows={devolucion.consumo.map((c) => ({ ...c, id: c.bebidaId }))}
          empty="No hubo bebidas en la barra."
          columns={[
            { key: 'bebida', header: 'Bebida', render: (c) => `${c.nombre} ${c.presentacion}` },
            { key: 'entregado', header: 'Entregado', numeric: true, render: (c) => legible(c, c.entregado) },
            { key: 'devuelto', header: 'Vuelve', numeric: true, render: (c) => legible(c, c.devuelto) },
            { key: 'remanente', header: 'Queda', numeric: true, render: (c) => legible(c, c.remanente) },
            { key: 'consumo', header: 'Consumo', numeric: true, render: (c) => legible(c, c.consumo) },
          ]}
        />
      </Card>
      {devolucion.desdeTransicion.length > 0 && (
        <Card title="Volvió desde la transición">
          <ul className="barra__lista">
            {devolucion.desdeTransicion.map((e) => (
              <li key={e.bebidaId}>
                <span>{e.nombre} {e.presentacion}</span>
                <span className="numeral-sm">{legible(e, e.cantidad)}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </section>
  );
}
