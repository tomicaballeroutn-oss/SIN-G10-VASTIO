import { render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Alert } from './feedback';

describe('Alert centrar', () => {
  afterEach(() => { vi.restoreAllMocks(); });

  it('al aparecer lleva la pantalla hasta el aviso y lo centra', () => {
    const desplazar = vi.fn();
    Element.prototype.scrollIntoView = desplazar;
    render(<Alert tone="success" centrar>Servicios guardados.</Alert>);
    expect(desplazar).toHaveBeenCalledWith(expect.objectContaining({ block: 'center' }));
  });

  it('sin centrar no mueve la pantalla', () => {
    const desplazar = vi.fn();
    Element.prototype.scrollIntoView = desplazar;
    render(<Alert tone="success">Servicios guardados.</Alert>);
    expect(desplazar).not.toHaveBeenCalled();
  });
});
