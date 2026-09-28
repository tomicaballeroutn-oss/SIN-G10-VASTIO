import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { PaginaDs } from './PaginaDs';

describe('página /_ds', () => {
  it('muestra una sección por cada grupo de componentes', () => {
    render(<PaginaDs />);

    for (const titulo of [
      'Icon', 'Button e IconButton', 'Input, Select, Checkbox, Switch y Stepper', 'Alert', 'Dialog y EmptyState', 'Badge',
      'StatusChip: estados del evento', 'SalonTag', 'Card, Actor y Stat', 'Table', 'Tabs', 'Timeline', 'Nav', 'AgendaGrid',
      'EventCard', 'StockLevel y MovementCard',
    ]) {
      expect(screen.getByRole('heading', { name: titulo })).toBeInTheDocument();
    }
  });

  it('muestra cada estado del evento con su palabra', () => {
    render(<PaginaDs />);
    const seccion = screen.getByRole('heading', { name: 'StatusChip: estados del evento' }).parentElement!;

    for (const estado of ['Disponible', 'Pre-reserva', 'Señado', 'Confirmado', 'Realizado', 'Cancelado', 'Bloqueado']) {
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

    expect(screen.getByRole('button', { name: /sábado 26 de septiembre\. Avril noche: Confirmado; Club de Campo noche: Confirmado; Santa Bárbara noche: Señado/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'lunes 28 de septiembre. Todo disponible' })).toBeInTheDocument();
  });
});
