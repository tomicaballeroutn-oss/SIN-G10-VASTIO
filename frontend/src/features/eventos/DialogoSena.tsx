import { useState } from 'react';
import { hoyEnCordoba } from '../../api/agenda';
import { fichas, type Ficha } from '../../api/eventos';
import { useEnvio } from '../../api/useDatos';
import { Alert, Button, Dialog, Input } from '../../ds';
import { fechaCorta, leerImporte } from '../comun/formato';

/**
 * UI-11 · Registrar seña: la pre-reserva pasa a Señado y se vuelve una reserva firme.
 * Importe mayor a 0, fecha del pago no posterior a hoy y DNI del firmante; nombre y contacto opcionales.
 */
export function DialogoSena({ ficha, alCerrar, alRegistrar }: {
  ficha: Ficha;
  alCerrar: () => void;
  alRegistrar: (ficha: Ficha) => void;
}) {
  const hoy = hoyEnCordoba();
  const [importe, setImporte] = useState('');
  const [fechaPago, setFechaPago] = useState(hoy);
  const [firmanteNombre, setFirmanteNombre] = useState('');
  const [firmanteDni, setFirmanteDni] = useState('');
  const [firmanteContacto, setFirmanteContacto] = useState('');
  const [importeIlegible, setImporteIlegible] = useState(false);
  const { guardando, error, enviar } = useEnvio();

  async function confirmar() {
    const valor = leerImporte(importe);
    setImporteIlegible(importe.trim() !== '' && valor === null);
    if (importe.trim() !== '' && valor === null) return;
    const nueva = await enviar(() =>
      fichas.registrarSena(ficha.id, { importe: valor, fechaPago, firmanteNombre, firmanteDni, firmanteContacto }),
    );
    if (nueva) alRegistrar(nueva);
  }

  return (
    <Dialog
      open
      title="Registrar seña"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="banknote" loading={guardando} onClick={() => void confirmar()}>Confirmar seña</Button>
        </>
      }
    >
      <div className="sena">
        <p className="body-sm v-muted">
          {ficha.nombre} · {ficha.salon.nombre} · {fechaCorta(ficha.fecha, true)} · {ficha.turno.nombre}
        </p>
        {error && !error.errores.length && error.codigo !== 'FECHA_PAGO_FUTURA' && <Alert tone="danger">{error.message}</Alert>}
        <div className="sena__par">
          <Input
            label="Importe de la seña"
            icon="banknote"
            inputMode="decimal"
            placeholder="0"
            suffix="pesos"
            value={importe}
            onChange={(e) => setImporte(e.target.value)}
            error={importeIlegible ? 'Escribí el importe con números, p. ej. 150.000.' : error?.errorDe('importe')}
            required
          />
          <Input
            label="Fecha del pago"
            type="date"
            value={fechaPago}
            onChange={(e) => setFechaPago(e.target.value)}
            error={error?.errorDe('fechaPago') ?? (error?.codigo === 'FECHA_PAGO_FUTURA' ? error.message : undefined)}
            required
          />
        </div>
        <fieldset className="sena__firmante">
          <legend className="label">Datos del firmante</legend>
          <p className="body-sm v-muted">Quien firma el contrato. El DNI es obligatorio.</p>
          <Input label="Nombre y apellido" optional value={firmanteNombre} onChange={(e) => setFirmanteNombre(e.target.value)} error={error?.errorDe('firmanteNombre')} />
          <div className="sena__par">
            <Input
              label="DNI"
              inputMode="numeric"
              hint="Sin puntos."
              value={firmanteDni}
              onChange={(e) => setFirmanteDni(e.target.value.replace(/\D/g, ''))}
              error={error?.errorDe('firmanteDni')}
              required
            />
            <Input
              label="Contacto"
              optional
              placeholder="Teléfono o email"
              value={firmanteContacto}
              onChange={(e) => setFirmanteContacto(e.target.value)}
              error={error?.errorDe('firmanteContacto')}
            />
          </div>
        </fieldset>
      </div>
    </Dialog>
  );
}
