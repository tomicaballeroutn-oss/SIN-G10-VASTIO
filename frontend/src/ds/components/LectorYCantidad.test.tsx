import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CantidadEnCajas } from './formularios';
import { LectorCodigo, type IniciarLector } from './lector';

function Cantidad({ inicial, porBulto, alCambiar }: { inicial: number; porBulto: number; alCambiar: (n: number) => void }) {
  const [valor, setValor] = useState(inicial);
  return (
    <CantidadEnCajas
      label="Cantidad"
      value={valor}
      porBulto={porBulto}
      unidad="Caja"
      onChange={(n) => {
        setValor(n);
        alCambiar(n);
      }}
    />
  );
}

describe('CantidadEnCajas', () => {
  it('carga cajas y botellas sueltas y devuelve botellas', async () => {
    const alCambiar = vi.fn();
    const persona = userEvent.setup();
    render(<Cantidad inicial={15} porBulto={6} alCambiar={alCambiar} />);

    expect(screen.getByLabelText('Cajas de 6')).toHaveValue('2');
    expect(screen.getByLabelText('Botellas sueltas')).toHaveValue('3');
    await persona.click(screen.getAllByRole('button', { name: 'Sumar 1' })[0]);
    expect(alCambiar).toHaveBeenLastCalledWith(21);
    await persona.click(screen.getAllByRole('button', { name: 'Sumar 1' })[1]);
    expect(alCambiar).toHaveBeenLastCalledWith(22);
  });

  it('con un bulto de una botella, solo cuenta botellas', () => {
    render(<Cantidad inicial={4} porBulto={1} alCambiar={() => undefined} />);

    expect(screen.queryByLabelText(/Cajas/)).not.toBeInTheDocument();
    expect(screen.getByLabelText('Botellas')).toHaveValue('4');
  });
});

describe('LectorCodigo (UI-30)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(navigator, 'mediaDevices');
  });

  function conCamara() {
    vi.stubGlobal('isSecureContext', true);
    Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia: vi.fn() }, configurable: true });
  }

  it('sin HTTPS lo dice y ofrece la carga manual', async () => {
    vi.stubGlobal('isSecureContext', false);
    const manual = vi.fn();
    const persona = userEvent.setup();
    render(<LectorCodigo open onClose={() => undefined} onRead={() => undefined} onManual={manual} />);

    expect(screen.getByText(/La cámara necesita una conexión segura/)).toBeInTheDocument();
    await persona.click(screen.getByRole('button', { name: 'Elegir a mano' }));
    expect(manual).toHaveBeenCalled();
  });

  it('avisa cada código una sola vez aunque la cámara lo siga leyendo, y apaga la cámara al cerrar', async () => {
    conCamara();
    const stop = vi.fn();
    let leer: (codigo: string) => void = () => undefined;
    const iniciar: IniciarLector = async (_video, alLeer) => {
      leer = alLeer;
      return { stop };
    };
    const onRead = vi.fn();
    const { rerender } = render(<LectorCodigo open onClose={() => undefined} onRead={onRead} onManual={() => undefined} iniciar={iniciar} />);
    await act(async () => undefined);

    act(() => {
      leer('7790000000019');
      leer('7790000000019');
      leer('17790000000016');
    });
    expect(onRead.mock.calls).toEqual([['7790000000019'], ['17790000000016']]);

    rerender(<LectorCodigo open={false} onClose={() => undefined} onRead={onRead} onManual={() => undefined} iniciar={iniciar} />);
    expect(stop).toHaveBeenCalled();
  });

  it('sin permiso de cámara lo explica y deja reintentar', async () => {
    conCamara();
    const iniciar = vi.fn<IniciarLector>()
      .mockRejectedValueOnce(new DOMException('denegado', 'NotAllowedError'))
      .mockResolvedValueOnce({ stop: () => undefined });
    const persona = userEvent.setup();
    render(<LectorCodigo open onClose={() => undefined} onRead={() => undefined} onManual={() => undefined} iniciar={iniciar} />);

    expect(await screen.findByText(/No tenemos permiso para usar la cámara/)).toBeInTheDocument();
    await persona.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(iniciar).toHaveBeenCalledTimes(2);
    expect(screen.queryByText(/No tenemos permiso/)).not.toBeInTheDocument();
  });
});
