import { useCallback, useMemo, useState } from 'react';
import { bebidas, type Bebida } from '../../api/bebidas';
import { catalogos, type TipoEvento } from '../../api/catalogos';
import { proporciones, type Proporcion } from '../../api/ordenes';
import { useDatos, useEnvio } from '../../api/useDatos';
import { Alert, Button, Card, Combobox, EmptyState, IconButton, Input, Select, Table } from '../../ds';
import { Cargando } from '../comun/Cargando';

const NUMERO = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 4 });

const cargar = () => Promise.all([catalogos.tiposEvento(), bebidas.listar()]).then(([tipos, catalogo]) => ({ tipos, catalogo }));

/** «0,05» o «0.05» → 0.05; null si no es un número con hasta cuatro decimales. */
function decimal(texto: string): number | null {
  const normal = texto.trim().replace(',', '.');
  return /^\d{1,2}(\.\d{1,4})?$/.test(normal) ? Number(normal) : null;
}

/**
 * Proporciones de consumo (docs/sprint-4.md, decisión 14): por tipo de evento, las botellas por asistente de cada bebida
 * con que arranca la orden sugerida mientras no hay suficientes eventos cerrados del tipo.
 */
export function SeccionProporciones() {
  const datos = useDatos(cargar);
  return (
    <Cargando datos={datos}>
      {({ tipos, catalogo }) => <Proporciones tipos={tipos.filter((t) => t.activo)} catalogo={catalogo.filter((b) => b.activo)} />}
    </Cargando>
  );
}

function Proporciones({ tipos, catalogo }: { tipos: TipoEvento[]; catalogo: Bebida[] }) {
  const [tipoId, setTipoId] = useState(tipos[0]?.id ?? 0);
  const cargarDelTipo = useCallback(() => (tipoId ? proporciones.delTipo(tipoId) : Promise.resolve([])), [tipoId]);
  const lista = useDatos(cargarDelTipo);
  const [buscar, setBuscar] = useState('');
  const [bebida, setBebida] = useState<Bebida | null>(null);
  const [valor, setValor] = useState('');
  const [revisar, setRevisar] = useState(false);
  const { guardando, error, enviar } = useEnvio();

  const opciones = useMemo(() => {
    const texto = buscar.trim().toLowerCase();
    return catalogo.filter((b) => `${b.nombre} ${b.presentacion}`.toLowerCase().includes(texto)).slice(0, 8)
      .map((b) => ({ value: String(b.id), label: `${b.nombre} ${b.presentacion}` }));
  }, [buscar, catalogo]);

  async function guardar() {
    const numero = decimal(valor);
    setRevisar(true);
    if (!bebida || numero === null || numero <= 0) return;
    const guardada = await enviar(() => proporciones.guardar(tipoId, bebida.id, numero));
    if (guardada) {
      lista.recargar();
      setBebida(null);
      setBuscar('');
      setValor('');
      setRevisar(false);
    }
  }

  async function quitar(p: Proporcion) {
    const hecho = await enviar(() => proporciones.quitar(p.id));
    if (hecho !== undefined) lista.recargar();
  }

  if (tipos.length === 0) {
    return <Card><EmptyState icon="clipboard-list" title="Sin tipos de evento activos">Agregá uno en «Tipos de evento».</EmptyState></Card>;
  }
  const numero = decimal(valor);
  return (
    <Card
      title="Proporciones de consumo"
      subtitle="Botellas por asistente de cada bebida. La orden sugerida las usa mientras haya menos de 3 eventos cerrados del tipo."
    >
      <div className="proporciones">
        <Select
          label="Tipo de evento"
          options={tipos.map((t) => ({ value: String(t.id), label: t.nombre }))}
          value={String(tipoId)}
          onChange={(e) => setTipoId(Number(e.target.value))}
        />
        {error && <Alert tone="danger">{error.message}</Alert>}
        <Cargando datos={lista}>
          {(filas) => (
            <Table<Proporcion>
              caption="Proporciones del tipo de evento"
              rows={filas}
              empty="Sin proporciones cargadas para este tipo de evento."
              columns={[
                { key: 'bebida', header: 'Bebida', render: (p) => `${p.nombre} ${p.presentacion}${p.activa ? '' : ' · Dada de baja'}` },
                { key: 'botellas', header: 'Botellas por asistente', numeric: true, render: (p) => NUMERO.format(p.botellasPorAsistente) },
                {
                  key: 'quitar', header: '', width: '56px',
                  render: (p) => <IconButton icon="trash-2" label={`Quitar ${p.nombre} ${p.presentacion}`} onClick={() => void quitar(p)} />,
                },
              ]}
            />
          )}
        </Cargando>
        <div className="proporciones__alta">
          <Combobox
            label="Bebida"
            value={bebida ? `${bebida.nombre} ${bebida.presentacion}` : buscar}
            onInputChange={(t) => {
              setBebida(null);
              setBuscar(t);
            }}
            options={opciones}
            onSelect={(o) => setBebida(catalogo.find((b) => String(b.id) === o.value) ?? null)}
            emptyText="Ninguna bebida activa con ese nombre."
            error={revisar && !bebida ? 'Elegí una bebida de la lista.' : undefined}
          />
          <Input
            label="Botellas por asistente"
            inputMode="decimal"
            value={valor}
            placeholder="0,05"
            onChange={(e) => setValor(e.target.value)}
            hint="Si la bebida ya tiene proporción, se reemplaza."
            error={revisar && (numero === null || numero <= 0) ? 'Escribí un número mayor a 0, con hasta cuatro decimales.' : undefined}
          />
          <Button icon="plus" loading={guardando} onClick={() => void guardar()}>Guardar proporción</Button>
        </div>
      </div>
    </Card>
  );
}
