import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Table } from './datos';

const columnas = [
  { key: 'bebida', header: 'Bebida' },
  { key: 'consumo', header: 'Consumido', numeric: true, principal: true },
];

describe('Table apilada', () => {
  it('rotula cada celda con su encabezado y marca la principal', () => {
    render(<Table caption="Consumo" apilada columns={columnas} rows={[{ id: 1, bebida: 'Fernet', consumo: '3 cajas' }]} />);
    expect(screen.getByRole('table')).toHaveClass('v-table--apilada');
    const celda = screen.getByRole('cell', { name: '3 cajas' });
    expect(celda).toHaveAttribute('data-label', 'Consumido');
    expect(celda).toHaveClass('v-table__principal');
  });

  it('sin apilar no agrega rótulos', () => {
    render(<Table caption="Consumo" columns={columnas} rows={[{ id: 1, bebida: 'Fernet', consumo: '3 cajas' }]} />);
    expect(screen.getByRole('table')).not.toHaveClass('v-table--apilada');
    expect(screen.getByRole('cell', { name: 'Fernet' })).not.toHaveAttribute('data-label');
  });
});
