import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import { backendFalso, json, montar, sesionDe } from '../../test/backendFalso';
import { ARTICULOS, CATEGORIAS, GLOSARIO } from './articulos';
import { CONTENIDO } from './contenido';
import { puedeLeer, rutaArticulo } from './modelo';
import { VideoTutorial } from './VideoTutorial';
import { urlVideoValida } from './video';

beforeEach(() => {
  _reiniciarCliente();
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  Element.prototype.scrollIntoView = vi.fn();
});

describe('centro de ayuda integrado', () => {
  it('explica que la devolución registra el consumo y actualiza las existencias', async () => {
    backendFalso(sesionDe(['BARRA']), () => json(200, []));
    montar('/ayuda/barra/devoluciones');
    await screen.findByText(/Al confirmar la devolución, Vastio calcula y registra el consumo real de cada bebida\./);
    expect(screen.getByText('Al confirmar la devolución, se registran los movimientos de consumo y se actualizan las existencias.')).toBeInTheDocument();
    expect(screen.getByText('El cierre posterior del evento no vuelve a descontar esas bebidas del stock.')).toBeInTheDocument();
    expect(within(screen.getByRole('article')).getAllByRole('listitem')).toHaveLength(5);
  });

  it('explica que el cierre consolida el consumo previamente registrado', async () => {
    backendFalso(sesionDe(['BARRA']), () => json(200, []));
    montar('/ayuda/barra/cierre');
    await screen.findByText('El consumo de cada barra ya fue calculado y registrado al confirmar su devolución.');
    expect(screen.getByText('Durante el cierre, Vastio consolida esos consumos y guarda el resultado definitivo en consumo_evento.')).toBeInTheDocument();
    expect(screen.getByText('El cierre no genera nuevamente movimientos CONSUMO ni descuenta otra vez las bebidas del stock.')).toBeInTheDocument();
    expect(screen.getByText('Los ajustes de stock posteriores no modifican el consumo consolidado del evento.')).toBeInTheDocument();
    expect(within(screen.getByRole('article')).getAllByRole('listitem')).toHaveLength(5);
  });

  it('permite abrir los 19 artículos y el glosario por URL y volver a su categoría', async () => {
    backendFalso(sesionDe(['DIRECCION']), () => json(200, []));
    const router = montar('/ayuda');
    const usuario = userEvent.setup();
    await screen.findByRole('heading', { name: 'Centro de ayuda', level: 1 });
    expect(CATEGORIAS).toHaveLength(6);
    expect(ARTICULOS).toHaveLength(19);
    for (const articulo of [...ARTICULOS, GLOSARIO]) {
      await usuario.click(screen.getByRole('link', { name: articulo.titulo }));
      expect(router.state.location.pathname).toBe(rutaArticulo(articulo));
      expect(await screen.findByRole('heading', { level: 1, name: articulo.titulo })).toHaveFocus();
      expect(document.querySelector('video, iframe')).toBeNull();
      await usuario.click(screen.getByRole('link', { name: /^Volver a / }));
      expect(router.state.location.pathname).toBe('/ayuda');
      expect(router.state.location.hash).toBe(`#${articulo.categoria}`);
    }
  }, 20_000);

  it('abre un enlace directo y rechaza combinaciones de categoría o artículo inexistentes', async () => {
    backendFalso(sesionDe(['DIRECCION']), () => json(200, []));
    const router = montar('/ayuda/agenda/filtros-estados');
    await screen.findByRole('heading', { level: 1, name: 'Filtros y estados de la agenda' });
    await router.navigate('/ayuda/barra/filtros-estados');
    await screen.findByText('Este artículo no está disponible');
    await router.navigate('/ayuda/agenda/desconocido');
    expect(await screen.findByText('Este artículo no está disponible')).toBeInTheDocument();
  });

  it('exige sesión y conserva la dirección del artículo para el ingreso', async () => {
    backendFalso(null);
    const router = montar('/ayuda/barra/devoluciones');
    await screen.findByRole('button', { name: 'Ingresar' });
    expect(router.state.location.pathname).toBe('/login');
    expect(new URLSearchParams(router.state.location.search).get('volver')).toBe('/ayuda/barra/devoluciones');
    expect(screen.queryByText('Registrar la devolución')).not.toBeInTheDocument();
  });

  it('respeta el cambio obligatorio de contraseña', async () => {
    const sesion = sesionDe(['COCINA']);
    sesion.usuario.debeCambiarContrasena = true;
    backendFalso(sesion, () => json(200, []));
    const router = montar('/ayuda');
    await screen.findByRole('form', { name: 'Cambiar contraseña' });
    expect(router.state.location.pathname).toBe('/mi-cuenta');
    expect(screen.queryByRole('heading', { name: 'Centro de ayuda' })).not.toBeInTheDocument();
  });

  it.each<Rol>(['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'VENDEDORA', 'PLANNER', 'COMPRAS', 'BARRA', 'COCINA'])(
    'ofrece acceso desde la cuenta de escritorio y Más para %s', async (rol) => {
      backendFalso(sesionDe([rol]), () => json(200, []));
      montar('/mas');
      const usuario = userEvent.setup();
      await screen.findByRole('heading', { name: 'Más', level: 1 });
      const escritorio = document.querySelector('.app__lateral') as HTMLElement;
      expect(within(escritorio).getByRole('button', { name: 'Centro de ayuda' })).toBeInTheDocument();
      const mas = screen.getByRole('heading', { name: 'Más', level: 1 }).closest('section')!;
      await usuario.click(within(mas).getByRole('button', { name: 'Centro de ayuda' }));
      await screen.findByRole('heading', { name: 'Centro de ayuda', level: 1 });
    },
  );

  it('aplica la unión de roles y protege también los enlaces directos', async () => {
    backendFalso(sesionDe(['BARRA', 'COCINA']), () => json(200, []));
    const router = montar('/ayuda');
    await screen.findByRole('heading', { name: 'Centro de ayuda', level: 1 });
    expect(screen.getByRole('link', { name: 'Devoluciones y remanentes' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Vista de cocina' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Órdenes y preparación de bebidas' })).not.toBeInTheDocument();
    await router.navigate('/ayuda/bebidas/ordenes-preparacion');
    await screen.findByText('Este artículo no está disponible');
    expect(screen.queryByText('Gestionar una orden')).not.toBeInTheDocument();
  });

  it.each([
    { rol: 'VENDEDORA' as Rol, visible: 'Reprogramar un evento', oculta: 'Confirmar un evento' },
    { rol: 'PLANNER' as Rol, visible: 'Confirmar un evento', oculta: 'Reprogramar un evento' },
  ])('filtra las operaciones del artículo compartido para $rol', async ({ rol, visible, oculta }) => {
    backendFalso(sesionDe([rol]), () => json(200, []));
    montar('/ayuda/agenda/confirmacion-reprogramacion');
    await screen.findByRole('heading', { name: visible });
    expect(screen.queryByRole('heading', { name: oculta })).not.toBeInTheDocument();
  });

  it('ofrece consulta y preparación a Administración, y reserva la gestión de órdenes', async () => {
    backendFalso(sesionDe(['ADMINISTRACION']), () => json(200, []));
    montar('/ayuda/bebidas/ordenes-preparacion');
    await screen.findByRole('heading', { name: 'Consultar una orden' });
    expect(screen.getByRole('heading', { name: 'Registrar una preparación' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Gestionar una orden' })).not.toBeInTheDocument();
  });
});

describe('permisos y contenido', () => {
  const compartidos = ['ingresar', 'mi-cuenta', 'notificaciones'];
  const agenda = ['utilizar-agenda', 'filtros-estados', 'ficha-historial'];
  const operacion = ['retiros', 'devoluciones', 'cierre'];
  const stock = ['existencias', 'ingresos-ajustes', 'ordenes-preparacion', 'estadisticas-consumo'];
  const comercial = ['pre-reservas-senas', 'confirmacion-reprogramacion'];
  const esperados: Record<Rol, string[]> = {
    DIRECCION: ARTICULOS.map((articulo) => articulo.id),
    COORDINACION: ARTICULOS.map((articulo) => articulo.id),
    ADMINISTRACION: [...compartidos, ...agenda, ...stock, 'parametros'],
    COMPRAS: [...compartidos, ...agenda, ...stock, ...operacion, 'cocina'],
    VENDEDORA: [...compartidos, ...agenda, ...comercial],
    PLANNER: [...compartidos, ...agenda, 'confirmacion-reprogramacion', 'cocina'],
    BARRA: [...compartidos, 'existencias', ...operacion],
    COCINA: [...compartidos, 'cocina'],
  };
  it.each(Object.keys(esperados) as Rol[])('conserva la matriz de acceso de %s', (rol) => {
    expect(ARTICULOS.filter((articulo) => puedeLeer(articulo, [rol])).map((articulo) => articulo.id).sort()).toEqual([...esperados[rol]].sort());
    expect(puedeLeer(GLOSARIO, [rol])).toBe(true);
  });

  it('tiene contenido para cada artículo y enlaces únicos sin integración oculta', () => {
    expect(new Set(ARTICULOS.map(rutaArticulo)).size).toBe(19);
    for (const articulo of ARTICULOS) {
      expect(CONTENIDO[articulo.id].secciones.length).toBeGreaterThan(0);
    }
    expect(JSON.stringify(CONTENIDO)).not.toMatch(/google calendar/i);
  });
});

describe('video tutorial opcional', () => {
  it.each([undefined, '', ' ', 'incorrecta', 'javascript:alert(1)', 'http://ejemplo.com/video.mp4', 'https://ejemplo.com/pagina', 'https://usuario:clave@ejemplo.com/video.mp4'])(
    'omite el reproductor para %s', (url) => {
      expect(urlVideoValida(url)).toBeUndefined();
      const { container } = render(<VideoTutorial url={url} titulo="Agenda" />);
      expect(container).toBeEmptyDOMElement();
    },
  );
  it('reproduce una URL HTTPS configurada y permite reemplazarla', () => {
    cleanup();
    const { container, rerender } = render(<VideoTutorial url="https://ejemplo.com/agenda.mp4?version=1" titulo="Agenda" />);
    expect(container.querySelector('video')).toHaveAttribute('controls');
    expect(container.querySelector('source')).toHaveAttribute('src', 'https://ejemplo.com/agenda.mp4?version=1');
    rerender(<VideoTutorial url="https://ejemplo.com/agenda.webm" titulo="Agenda" />);
    expect(container.querySelector('source')).toHaveAttribute('src', 'https://ejemplo.com/agenda.webm');
    rerender(<VideoTutorial titulo="Agenda" />);
    expect(container).toBeEmptyDOMElement();
  });
});
