import { useState } from 'react';
import { fichas, type Ficha, type ServicioFicha } from '../../api/eventos';
import { useEnvio } from '../../api/useDatos';
import { Actor, Alert, Badge, Button, Card, Input } from '../../ds';
import { fechaHora } from '../comun/formato';

/**
 * UI-13 · Servicios contratados: un texto libre por categoría configurable. Quien puede modificar el evento los
 * edita y los guarda juntos; el resto los ve. Las categorías dadas de baja con algo cargado se ven sin editar.
 */
export function PestanaServicios({ ficha, alGuardar }: { ficha: Ficha; alGuardar: (ficha: Ficha) => void }) {
  const editable = ficha.acciones.modificar;
  const [textos, setTextos] = useState<Record<number, string>>(() =>
    Object.fromEntries(ficha.servicios.map((s) => [s.categoriaId, s.descripcion ?? ''])),
  );
  const { guardando, error, enviar } = useEnvio();
  const [guardado, setGuardado] = useState(false);

  const cambiados = ficha.servicios.filter((s) => s.activa && (textos[s.categoriaId] ?? '').trim() !== (s.descripcion ?? ''));

  async function guardar() {
    setGuardado(false);
    const nueva = await enviar(() =>
      fichas.registrarServicios(ficha.id, ficha.version, cambiados.map((s) => ({ categoriaId: s.categoriaId, descripcion: textos[s.categoriaId] }))),
    );
    if (nueva) {
      setGuardado(true);
      alGuardar(nueva);
    }
  }

  /** Errores de validación por campo: `servicios[i].descripcion`, con i en el orden de lo enviado. */
  function errorDe(s: ServicioFicha): string | undefined {
    const i = cambiados.findIndex((c) => c.categoriaId === s.categoriaId);
    return i >= 0 ? error?.errorDe(`servicios[${i}].descripcion`) : undefined;
  }

  return (
    <Card title="Servicios contratados" subtitle={editable ? 'Describí lo acordado con el cliente en cada categoría.' : undefined}>
      <div className="servicios">
        {guardado && !cambiados.length && <Alert tone="success">Servicios guardados.</Alert>}
        {error && !error.errores.length && <Alert tone="danger">{error.message}</Alert>}
        {ficha.servicios.map((s) => (
          <div key={s.categoriaId} className="servicios__categoria">
            {editable && s.activa ? (
              <Input
                label={s.categoria}
                multiline
                rows={2}
                placeholder="Describí la propuesta para esta categoría"
                hint={s.requeridaParaConfirmar ? 'Hace falta para confirmar el evento.' : undefined}
                value={textos[s.categoriaId] ?? ''}
                onChange={(e) => setTextos((previos) => ({ ...previos, [s.categoriaId]: e.target.value }))}
                error={errorDe(s)}
              />
            ) : (
              <>
                <div className="servicios__titulo">
                  <h4 className="h4">{s.categoria}</h4>
                  {s.requeridaParaConfirmar && <Badge tone="info">Hace falta para confirmar</Badge>}
                  {!s.activa && <Badge>Categoría dada de baja</Badge>}
                </div>
                <p className="body servicios__texto">{s.descripcion ?? <span className="v-muted">Sin cargar.</span>}</p>
              </>
            )}
            {s.usuario && s.fechaModificacion && (
              <Actor name={s.usuario.nombre} action="lo cargó" at={fechaHora(s.fechaModificacion)} size="sm" />
            )}
          </div>
        ))}
        {editable && (
          <div className="servicios__acciones">
            <Button icon="check" loading={guardando} disabled={!cambiados.length} onClick={() => void guardar()}>
              Guardar servicios
            </Button>
          </div>
        )}
      </div>
    </Card>
  );
}
