import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AgendaGrid, AgendaLeyenda, MiniCalendar } from './agenda';
import { Card, Tabs } from './datos';
import { IconButton } from './Button';
import { Logo } from './Logo';
import { Nav } from './Nav';

// Piezas v2 «Fichero» del sistema de diseño: riel, fichas con solapa y muesca, solapas folder y MiniCalendar.

const ITEMS = [
  { id: 'inicio', label: 'Inicio', icon: 'house' },
  { id: 'agenda', label: 'Agenda', icon: 'calendar', group: 'Agenda' },
  { id: 'eventos', label: 'Eventos', icon: 'clipboard-list', group: 'Agenda', badge: 2 },
  { id: 'barra', label: 'Barra', icon: 'martini', group: 'Bebidas' },
  { id: 'usuarios', label: 'Usuarios y perfiles', icon: 'users', group: 'Gestión' },
];

describe('Nav layout="rail"', () => {
  it('conserva el texto de cada ítem para los lectores de pantalla aunque el riel esté cerrado', () => {
    render(<Nav layout="rail" label="Navegación principal" items={ITEMS} value="agenda" />);
    const nav = screen.getByRole('navigation', { name: 'Navegación principal' });

    for (const it of ITEMS) expect(within(nav).getByRole('button', { name: new RegExp(it.label) })).toBeInTheDocument();
    expect(within(nav).getByRole('button', { name: /Agenda/ })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('button', { name: /Eventos/ })).not.toHaveAttribute('aria-current');
  });

  it('agrupa los ítems con el nombre del grupo, y sin título para el grupo sin nombre', () => {
    const { container } = render(<Nav layout="rail" items={ITEMS} value="inicio" />);
    const titulos = [...container.querySelectorAll('.v-nav__title')].map((e) => e.textContent);

    expect(titulos).toEqual(['Agenda', 'Bebidas', 'Gestión']);
    expect(container.querySelectorAll('.v-nav__group')).toHaveLength(4);
  });

  it('marca la novedad con un punto sobre el ícono y el contador', () => {
    const { container } = render(<Nav layout="rail" items={ITEMS} value="inicio" />);
    expect(container.querySelectorAll('.v-nav__dot')).toHaveLength(1);
    expect(screen.getByRole('button', { name: /Eventos/ })).toHaveTextContent('2');
  });

  it('avisa qué ítem se eligió', async () => {
    const elegir = vi.fn();
    render(<Nav layout="rail" items={ITEMS} value="inicio" onSelect={elegir} />);
    await userEvent.click(screen.getByRole('button', { name: /Barra/ }));
    expect(elegir).toHaveBeenCalledWith('barra');
  });

  it('`open` lo deja abierto', () => {
    const { container } = render(<Nav layout="rail" items={ITEMS} open />);
    expect(container.querySelector('.v-rail')).toHaveClass('is-open');
  });
});

describe('Card v2', () => {
  it('muestra la solapa y la muesca con su botón', async () => {
    const abrir = vi.fn();
    const { container, rerender } = render(<Card tab="Sáb 26 sep" title="Bruno y Martina">Contenido</Card>);
    expect(screen.getByText('Sáb 26 sep')).toHaveClass('v-card__tab', 'overline');
    expect(container.querySelector('.v-card')).toHaveClass('v-card--tabbed');

    rerender(<Card title="Gala anual" notch={<IconButton icon="arrow-right" label="Abrir evento" onClick={abrir} />}>Contenido</Card>);
    expect(container.querySelector('.v-card')).toHaveClass('v-card--notched');
    await userEvent.click(screen.getByRole('button', { name: 'Abrir evento' }));
    expect(abrir).toHaveBeenCalled();
  });

  it('aplica el tono; surface es el predeterminado y no agrega clase', () => {
    const { container, rerender } = render(<Card>Contenido</Card>);
    expect(container.querySelector('.v-card')!.className).toBe('v-card');

    rerender(<Card tone="deep">Contenido</Card>);
    expect(container.querySelector('.v-card')).toHaveClass('v-card--deep');
  });
});

describe('Tabs variant="folder"', () => {
  it('se comporta como pestañas: una activa y se cambia con las flechas', async () => {
    const cambiar = vi.fn();
    render(
      <Tabs variant="folder" label="Próximos días" value="hoy" onChange={cambiar}
        items={[{ id: 'hoy', label: 'Hoy' }, { id: 'manana', label: 'Mañana' }]} />,
    );
    const lista = screen.getByRole('tablist', { name: 'Próximos días' });
    expect(lista).toHaveClass('v-tabs--folder');
    expect(screen.getByRole('tab', { name: 'Hoy' })).toHaveAttribute('aria-selected', 'true');

    screen.getByRole('tab', { name: 'Hoy' }).focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(cambiar).toHaveBeenCalledWith('manana');
  });
});

describe('MiniCalendar', () => {
  it('anuncia cada día completo con su ocupación', () => {
    render(<MiniCalendar year={2026} month={8} today="2026-09-28" days={{ '2026-09-26': 'reservado', '2026-09-27': 'prereserva' }} />);

    expect(screen.getByRole('button', { name: '26 de septiembre: con reserva' })).toHaveClass('is-reservado');
    expect(screen.getByRole('button', { name: '27 de septiembre: con pre-reserva' })).toHaveClass('is-prereserva');
    expect(screen.getByRole('button', { name: '28 de septiembre: libre' })).toHaveAttribute('aria-current', 'date');
    expect(screen.getAllByRole('button', { name: /de septiembre/ })).toHaveLength(30);
  });

  it('empieza la semana el lunes', () => {
    // El 1 de septiembre de 2026 es martes: un hueco antes del primer día.
    const { container } = render(<MiniCalendar year={2026} month={8} legend={false} />);
    const grilla = container.querySelector('.v-minical__grid')!;
    const primeraCelda = grilla.children[7];
    expect(primeraCelda.tagName).toBe('SPAN');
    expect(grilla.children[8]).toHaveTextContent('1');
  });

  it('elige un día y cambia de mes', async () => {
    const elegir = vi.fn();
    const anterior = vi.fn();
    render(<MiniCalendar year={2026} month={8} onSelectDay={elegir} onPrev={anterior} onNext={() => {}} />);

    await userEvent.click(screen.getByRole('button', { name: '10 de septiembre: libre' }));
    expect(elegir).toHaveBeenCalledWith('2026-09-10');
    await userEvent.click(screen.getByRole('button', { name: 'Mes anterior' }));
    expect(anterior).toHaveBeenCalled();
  });
});

describe('AgendaGrid y AgendaLeyenda', () => {
  it('dice arriba qué salón es cada columna de puntos, con los nombres de Parámetros', () => {
    render(<AgendaGrid year={2026} month={8} salones={[{ codigo: 'club', nombre: 'El Club' }]} />);
    const columnas = screen.getAllByRole('listitem').map((li) => li.textContent);
    expect(columnas).toEqual(['Avril', 'El Club', 'Santa Bárbara']);
  });

  it('el machete se abre y se cierra, y muestra cada estado que puede verse en la agenda', async () => {
    render(<AgendaLeyenda />);
    const resumen = screen.getByText('Qué significa cada punto');
    const machete = resumen.closest('details')!;
    expect(machete).not.toHaveAttribute('open');

    await userEvent.click(resumen);
    expect(machete).toHaveAttribute('open');
    for (const estado of ['Disponible', 'Pre-reserva', 'Señado', 'Contratado', 'Confirmado', 'En curso', 'Realizado', 'Cerrado', 'Bloqueado']) {
      expect(within(machete).getByText(estado)).toBeInTheDocument();
    }
  });
});

describe('Logo reverse', () => {
  it('usa solo la versión clara, en los dos temas', () => {
    render(<Logo variant="marca" reverse />);
    const imagenes = screen.getAllByRole('img', { name: 'Vastio' });
    expect(imagenes).toHaveLength(1);
    expect(imagenes[0]).toHaveAttribute('src', '/brand/vastio-mark-reverse.png');
  });
});
