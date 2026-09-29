import { Button, EmptyState } from '../../ds';

/** Provisoria hasta el layout con Nav (tarea 14). */
export function Inicio() {
  return (
    <main style={{ padding: 'var(--space-6)' }}>
      <EmptyState icon="sparkles" title="Vastio" action={<Button variant="outline" href="/_ds">Ver sistema de diseño</Button>}>
        Agenda de eventos y control de existencias de bebida.
      </EmptyState>
    </main>
  );
}
