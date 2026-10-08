import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import type { Rol } from '../../api/cliente';
import {
  avisarSinLeer, notificaciones, type Notificacion, type PaginaNotificaciones, type TipoNotificacion,
} from '../../api/notificaciones';
import { ProblemaApi } from '../../api/problema';
import { Alert, Badge, Button, EmptyState, Icon, Tabs, type IconName } from '../../ds';
import { fechaHora } from '../comun/formato';
import { useSesion } from '../sesion/contexto';
import './notificaciones.css';

const ICONO: Record<TipoNotificacion, IconName> = {
  EVENTO_NUEVO: 'calendar-plus',
  SENA: 'banknote',
  CONTRATO: 'file-check',
  CONFIRMACION: 'circle-check',
  MODIFICACION: 'pencil',
  REPROGRAMACION: 'calendar-clock',
  CANCELACION: 'calendar-x',
  PLANNER_ASIGNADA: 'user-plus',
  ALERTA_STOCK: 'triangle-alert',
};

/** Perfiles que abren la ficha del evento. Cocina y Barra leen el aviso pero no tienen ficha (Sprint 3: vista de cocina). */
const ABREN_FICHA: Rol[] = ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'VENDEDORA', 'PLANNER', 'COMPRAS'];

type Filtro = 'sin-leer' | 'todas';

/**
 * UI-22 · Lista de notificaciones (en el panel de la campana y en la página): más recientes primero, filtro
 * «Sin leer» / «Todas», de a 20. Abrir un aviso lo marca como leído y lleva a la ficha del evento; «Marcar todas como
 * leídas» deja el contador en 0. Cada cambio del contador se avisa a la campana.
 *
 * @param alIrAlEvento antes de navegar a la ficha (p. ej. cerrar el panel).
 */
export function ListaNotificaciones({ alIrAlEvento }: { alIrAlEvento?: () => void }) {
  const navegar = useNavigate();
  const { usuario } = useSesion();
  const abreFicha = !!usuario?.roles.some((r) => ABREN_FICHA.includes(r));
  const [filtro, setFiltro] = useState<Filtro>('sin-leer');
  const [lista, setLista] = useState<Notificacion[]>([]);
  const [pagina, setPagina] = useState(0);
  const [hayMas, setHayMas] = useState(false);
  const [sinLeer, setSinLeer] = useState(0);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<ProblemaApi | null>(null);

  const fijarSinLeer = useCallback((cantidad: number) => {
    setSinLeer(cantidad);
    avisarSinLeer(cantidad);
  }, []);

  /** Aplica una página recibida: la primera reemplaza la lista, las siguientes se agregan. */
  const aplicar = useCallback((r: PaginaNotificaciones) => {
    setLista((previa) => (r.pagina === 0 ? r.notificaciones : [...previa, ...r.notificaciones]));
    setPagina(r.pagina);
    setHayMas(r.hayMas);
    fijarSinLeer(r.sinLeer);
    setCargando(false);
  }, [fijarSinLeer]);

  const fallar = useCallback((e: unknown) => {
    setError(e instanceof ProblemaApi ? e : ProblemaApi.sinConexion());
    setCargando(false);
  }, []);

  // Primera página de cada filtro; `recarga` la vuelve a pedir (p. ej. después de marcar todas).
  const [recarga, setRecarga] = useState(0);
  useEffect(() => {
    let vigente = true;
    notificaciones.listar(filtro === 'sin-leer', 0).then(
      (r) => vigente && aplicar(r),
      (e: unknown) => vigente && fallar(e),
    );
    return () => {
      vigente = false;
    };
  }, [filtro, recarga, aplicar, fallar]);

  function pedirPrimera(f: Filtro) {
    setCargando(true);
    setError(null);
    setFiltro(f);
    setRecarga((n) => n + 1);
  }

  function verMas() {
    setCargando(true);
    notificaciones.listar(filtro === 'sin-leer', pagina + 1).then(aplicar, fallar);
  }

  async function abrir(n: Notificacion) {
    if (!n.leida) {
      try {
        await notificaciones.marcarLeida(n.id);
        setLista((previa) => previa.map((x) => (x.id === n.id ? { ...x, leida: true } : x)));
        fijarSinLeer(Math.max(0, sinLeer - 1));
      } catch {
        // si falla, el aviso sigue sin leer: no impide ir a la ficha
      }
    }
    if (n.tipo === 'ALERTA_STOCK') {
      // Sin evento: el saldo negativo se mira en Existencias (docs/sprint-3.md, decisión 18).
      alIrAlEvento?.();
      navegar('/existencias');
    } else if (abreFicha && n.eventoId) {
      alIrAlEvento?.();
      navegar(`/eventos/${n.eventoId}`);
    }
  }

  async function marcarTodas() {
    try {
      await notificaciones.marcarTodasLeidas();
      pedirPrimera(filtro);
    } catch (e) {
      fallar(e);
    }
  }

  return (
    <div className="avisos">
      <div className="avisos__cabeza">
        <Tabs
          variant="segmented"
          label="Filtro"
          items={[{ id: 'sin-leer', label: 'Sin leer' }, { id: 'todas', label: 'Todas' }]}
          value={filtro}
          onChange={(f) => pedirPrimera(f as Filtro)}
        />
        {sinLeer > 0 && <Badge tone="accent">{sinLeer === 1 ? '1 nueva' : `${sinLeer} nuevas`}</Badge>}
      </div>
      {error && <Alert tone="danger">{error.message}</Alert>}
      {!cargando && !error && lista.length === 0 && (
        <EmptyState icon="bell" title={filtro === 'sin-leer' ? 'No tenés avisos sin leer' : 'Todavía no tenés avisos'}>
          Acá aparecen los cambios de los eventos que te involucran.
        </EmptyState>
      )}
      {lista.length > 0 && (
        <ul className="avisos__lista">
          {lista.map((n) => (
            <li key={n.id}>
              <button type="button" className={n.leida ? 'avisos__aviso' : 'avisos__aviso is-nuevo'} onClick={() => void abrir(n)}>
                <Icon name={ICONO[n.tipo]} size={18} />
                <span className="avisos__texto">
                  <span className="body-sm">
                    {!n.leida && <span className="v-sr">Sin leer: </span>}
                    {n.mensaje}
                  </span>
                  <span className="caption v-muted">{fechaHora(n.fechaHora)}</span>
                </span>
                {!n.leida && <span className="avisos__punto" aria-hidden />}
              </button>
            </li>
          ))}
        </ul>
      )}
      {cargando && <p className="body-sm v-muted" role="status">Cargando…</p>}
      <div className="avisos__pie">
        {hayMas && !cargando && (
          <Button variant="outline" size="sm" onClick={verMas}>Ver más</Button>
        )}
        <Button variant="text" icon="check-check" disabled={sinLeer === 0} onClick={() => void marcarTodas()}>
          Marcar todas como leídas
        </Button>
      </div>
    </div>
  );
}
