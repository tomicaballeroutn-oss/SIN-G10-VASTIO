import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { PaginaDs } from './PaginaDs';

describe('página /_ds', () => {
  it('muestra una sección por cada grupo de componentes', () => {
    render(<PaginaDs />);

    for (const titulo of [
      'Icon', 'Button e IconButton', 'Input, Select, Combobox, SelectorArchivos, Checkbox, Switch y Stepper', 'Alert', 'Dialog y EmptyState', 'Badge',
      'StatusChip: estados del evento', 'SalonTag', 'Card, Actor y Stat', 'Table', 'Tabs', 'Timeline', 'Nav', 'AgendaGrid',
      'EventCard', 'StockLevel y MovementCard',
    ]) {
      expect(screen.getByRole('heading', { name: titulo })).toBeInTheDocument();
    }
  });

  it('muestra los nueve estados del evento con su palabra, más bloqueado y disponible', () => {
    render(<PaginaDs />);
    const seccion = screen.getByRole('heading', { name: 'StatusChip: estados del evento' }).parentElement!;

    for (const estado of [
      'Pre-reserva', 'Señado', 'Contratado', 'Confirmado', 'En curso', 'Realizado', 'Cerrado', 'Liberada', 'Cancelado', 'Bloqueado', 'Disponible',
    ]) {
      expect(within(seccion).getByText(estado)).toBeInTheDocument();
    }
  });

  it('el diálogo se abre, se anuncia y cierra con Esc', async () => {
    const usuario = userEvent.setup();
    render(<PaginaDs />);

    await usuario.click(screen.getByRole('button', { name: 'Abrir diálogo' }));
    expect(screen.getByRole('dialog', { name: 'Cancelar evento' })).toBeInTheDocument();

    await usuario.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('el campo con error lo anuncia y queda vinculado al input', () => {
    render(<PaginaDs />);

    const dni = screen.getByLabelText('DNI del firmante');
    expect(dni).toHaveAttribute('aria-invalid', 'true');
    expect(dni).toHaveAccessibleDescription('Escribí el DNI sin puntos.');
  });

  it('la agenda describe cada día con sus unidades ocupadas', () => {
    render(<PaginaDs />);

    expect(screen.getByRole('button', { name: /sábado 26 de septiembre\. Avril noche: Confirmado; Club de Campo noche: Confirmado; Santa Bárbara noche: Contratado/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'martes 29 de septiembre. Todo disponible' })).toBeInTheDocument();
  });
});

describe('rellenos de la agenda', () => {
  it('cada estado tiene su relleno para los tres salones', () => {
    const { container } = render(<PaginaDs />);
    const tabla = container.querySelector('.ds-rellenos')!;

    for (const estado of ['prereserva', 'senado', 'contratado', 'confirmado', 'en-curso', 'realizado', 'cerrado', 'liberada', 'cancelado']) {
      expect(tabla.querySelectorAll(`.v-pip--${estado}`)).toHaveLength(3);
    }
  });

  it('un evento en curso se anuncia en el día de la agenda', () => {
    render(<PaginaDs />);

    expect(screen.getByRole('button', { name: 'lunes 28 de septiembre. Santa Bárbara mediodía: En curso' })).toBeInTheDocument();
  });
});
