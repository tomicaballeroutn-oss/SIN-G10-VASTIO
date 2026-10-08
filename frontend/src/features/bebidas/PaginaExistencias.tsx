import { useCallback, useEffect, useState } from 'react';
import { catalogos, type Motivo } from '../../api/catalogos';
import { ESTADO_STOCK, INTERVALO_STOCK_MS, stock, type RenglonStock, type UbicacionConsultable } from '../../api/stock';
import { useDatos } from '../../api/useDatos';
import { NOMBRE_DE_TIPO } from '../../api/ubicaciones';
import { Alert, Button, Card, EmptyState, Input, Select, StockLevel, cantidadLegible } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { Encabezado } from '../layout/paginas';
import { DialogoRecuento, DialogoRotura } from './DialogosAjuste';
import './existencias.css';

/** Motivos activos del ámbito Ajuste, para el recuento y la rotura. */
const cargarMotivos = () => catalogos.motivos().then((lista) => lista.filter((m) => m.ambito === 'AJUSTE' && m.activo));

const TODAS = 'todas';

function etiqueta(u: UbicacionConsultable): string {
  if (u.evento) return `${u.ubicacion.nombre} · ${u.evento.nombre}`;
  return `${u.ubicacion.nombre} (${NOMBRE_DE_TIPO[u.ubicacion.tipo].toLowerCase()})`;
}

/**
 * UI-27 · Consultar stock (y UI-38 · ajuste: recuento y rotura desde cada bebida de una ubicación). Dirección, Coordinación, Administración y Compras eligen cualquier ubicación o el total del
 * complejo, con el estado de cada bebida. La encargada de barra elige entre las barras con evento en la jornada y ve solo
 * su saldo, sin estado (operación a ciegas). Se actualiza sola cada minuto.
 */
export function PaginaExistencias() {
  const ubicaciones = useDatos(stock.ubicaciones);
  return (
    <section className="pantalla">
      <Encabezado titulo="Existencias" antetitulo="Bebidas" />
      <Cargando datos={ubicaciones}>
        {(lista) =>
          lista.length === 0 ? (
            <Card flush>
              <EmptyState icon="martini" title="Hoy no hay eventos en tus barras">
                Cuando haya un evento confirmado en tu salón, vas a ver acá el stock de su barra.
              </EmptyState>
            </Card>
          ) : (
            <Consulta ubicaciones={lista} />
          )
        }
      </Cargando>
    </section>
  );
}

function Consulta({ ubicaciones }: { ubicaciones: UbicacionConsultable[] }) {
  // Quien ve el depósito ve todo; la barra solo recibe barras (con su evento).
  const veTodo = ubicaciones.some((u) => u.ubicacion.tipo !== 'BARRA' || !u.evento);
  const [elegida, setElegida] = useState(String(ubicaciones[0].ubicacion.id));
  const [buscar, setBuscar] = useState('');
  const cargar = useCallback(() => stock.existencias(elegida === TODAS ? null : Number(elegida)), [elegida]);
  const datos = useDatos(cargar);
  const { recargar } = datos;
  const [ajuste, setAjuste] = useState<{ tipo: 'recuento' | 'rotura'; renglon: RenglonStock } | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  // El ajuste necesita una ubicación: no se ofrece en el total ni a la barra.
  const ubicacion = elegida === TODAS ? undefined : ubicaciones.find((u) => String(u.ubicacion.id) === elegida)?.ubicacion;
  const puedeAjustar = veTodo && ubicacion !== undefined;

  useEffect(() => {
    const intervalo = setInterval(recargar, INTERVALO_STOCK_MS);
    return () => clearInterval(intervalo);
  }, [recargar]);

  const opciones = [
    ...(veTodo ? [{ value: TODAS, label: 'Todas las ubicaciones (total)' }] : []),
    ...ubicaciones.map((u) => ({ value: String(u.ubicacion.id), label: etiqueta(u) })),
  ];

  function registrado(mensaje: string) {
    setAjuste(null);
    setAviso(mensaje);
    recargar();
  }

  return (
    <>
      {aviso && <Alert tone="success">{aviso}</Alert>}
      <div className="existencias__filtros">
        <Select label="Ubicación" size="lg" options={opciones} value={elegida} onChange={(e) => { setElegida(e.target.value); setAviso(null); }} />
        <Input label="Buscar bebida" icon="search" size="lg" value={buscar} onChange={(e) => setBuscar(e.target.value)} />
        <Button variant="outline" icon="refresh-cw" size="lg" loading={datos.cargando && datos.datos !== undefined} onClick={recargar}>Actualizar</Button>
      </div>
      <Cargando datos={datos}>
        {({ renglones }) => {
          const texto = buscar.trim().toLowerCase();
          const visibles = renglones.filter((r) => !texto || `${r.bebida.nombre} ${r.bebida.presentacion}`.toLowerCase().includes(texto));
          if (renglones.length === 0) {
            return (
              <Card flush>
                <EmptyState icon="package" title="No hay bebidas en esta ubicación">
                  Cuando entre mercadería, la vas a ver acá.
                </EmptyState>
              </Card>
            );
          }
          return (
            <ul className="existencias__lista" aria-label="Existencias por bebida">
              {visibles.map((r) => (
                <li key={r.bebida.id}>
                  <StockLevel
                    name={r.bebida.nombre}
                    presentacion={[r.bebida.presentacion, r.bebida.tipo, !r.bebida.activo && 'dada de baja'].filter(Boolean).join(' · ')}
                    cantidad={r.cantidad}
                    cantidadTexto={cantidadLegible(r.cantidad, r.bebida.unidadesPorBulto, r.bebida.unidad)}
                    status={r.estado ? ESTADO_STOCK[r.estado] : null}
                    actions={puedeAjustar ? (
                      <>
                        <Button variant="outline" size="sm" icon="clipboard-list" onClick={() => setAjuste({ tipo: 'recuento', renglon: r })}>Registrar recuento</Button>
                        <Button variant="text" size="sm" onClick={() => setAjuste({ tipo: 'rotura', renglon: r })}>Declarar rotura</Button>
                      </>
                    ) : undefined}
                  />
                </li>
              ))}
              {visibles.length === 0 && <li className="body-sm v-muted">Ninguna bebida coincide con «{buscar.trim()}».</li>}
            </ul>
          );
        }}
      </Cargando>
      {ajuste && ubicacion && <DialogoAjuste tipo={ajuste.tipo} renglon={ajuste.renglon} ubicacion={ubicacion} alCerrar={() => setAjuste(null)} alRegistrar={registrado} />}
    </>
  );
}

function DialogoAjuste({ tipo, renglon, ubicacion, alCerrar, alRegistrar }: {
  tipo: 'recuento' | 'rotura';
  renglon: RenglonStock;
  ubicacion: { id: number; nombre: string };
  alCerrar: () => void;
  alRegistrar: (mensaje: string) => void;
}) {
  const motivos = useDatos(cargarMotivos);
  return (
    <Cargando datos={motivos}>
      {(lista: Motivo[]) => {
        const props = { ubicacion, renglon, motivos: lista, alCerrar, alRegistrar };
        return tipo === 'recuento' ? <DialogoRecuento {...props} /> : <DialogoRotura {...props} />;
      }}
    </Cargando>
  );
}
