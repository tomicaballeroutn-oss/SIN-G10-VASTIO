import { useState } from 'react';
import { bebidas } from '../../api/bebidas';
import type { RenglonStock } from '../../api/stock';
import { useDatos } from '../../api/useDatos';
import { Button, Combobox, Dialog } from '../../ds';
import { Cargando } from '../comun/Cargando';

/** El catálogo permite encontrar una bebida aunque la consulta de una barra omita su saldo cero. */
export function SeleccionBebidaRecuento({ ubicacion, renglones, alCerrar, alElegir }: {
  ubicacion: { id: number; nombre: string };
  renglones: RenglonStock[];
  alCerrar: () => void;
  alElegir: (renglon: RenglonStock) => void;
}) {
  const catalogo = useDatos(bebidas.listar);
  const [buscar, setBuscar] = useState('');
  const texto = buscar.trim().toLowerCase();
  return (
    <Dialog open title="Elegí la bebida del recuento" onClose={alCerrar} actions={<Button variant="outline" onClick={alCerrar}>Cancelar</Button>}>
      <p>Recuento en {ubicacion.nombre}. Buscá una bebida activa del catálogo.</p>
      <Cargando datos={catalogo}>
        {(lista) => (
          <Combobox
            label="Buscar bebida para el recuento"
            icon="search"
            value={buscar}
            onInputChange={setBuscar}
            options={texto ? lista.filter((b) => b.activo && (
              `${b.nombre} ${b.presentacion}`.toLowerCase().includes(texto) || b.codigos.some((c) => c.codigo.includes(texto))
            )).slice(0, 8).map((b) => ({ value: String(b.id), label: b.nombre, detail: b.presentacion })) : []}
            emptyText="No hay bebidas activas con ese nombre o código."
            onSelect={(opcion) => {
              const bebida = lista.find((b) => b.activo && String(b.id) === opcion.value);
              if (!bebida) return;
              // Solo es un valor de presentación. El servicio calcula la diferencia al registrar; no se crea stock al elegir.
              alElegir(renglones.find((r) => r.bebida.id === bebida.id) ?? {
                bebida: {
                  id: bebida.id, nombre: bebida.nombre, presentacion: bebida.presentacion,
                  tipo: bebida.tipo.nombre, unidad: bebida.unidad.nombre, unidadesPorBulto: bebida.unidadesPorBulto, activo: bebida.activo,
                },
                cantidad: 0,
              });
            }}
          />
        )}
      </Cargando>
    </Dialog>
  );
}
