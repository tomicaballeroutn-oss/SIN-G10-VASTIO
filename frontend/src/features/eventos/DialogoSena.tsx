import { useRef, useState } from 'react';
import { hoyEnCordoba } from '../../api/agenda';
import { fichas, type Ficha, type Moneda } from '../../api/eventos';
import { useEnvio } from '../../api/useDatos';
import { Alert, Button, Dialog, Icon, Input, Select } from '../../ds';
import { formatearImporte, leerImporte } from '../comun/formato';

/**
 * UI-11 · Registrar seña: la pre-reserva pasa a Señado y se vuelve una reserva firme.
 * Importe mayor a 0 en pesos o dólares (M-02, sin conversión; pesos de entrada), fecha del pago no posterior a hoy y
 * DNI del firmante; nombre y contacto opcionales. Antes de señar hacen falta la cantidad de invitados, el menú y la bodega
 * (A3-01): son los datos con los que se calcula el precio y, por lo tanto, la seña. Si falta alguno, el botón no va sólido
 * y, al tocarlo, avisa qué completar.
 */
export function DialogoSena({ ficha, alCerrar, alRegistrar }: {
  ficha: Ficha;
  alCerrar: () => void;
  alRegistrar: (ficha: Ficha) => void;
}) {
  const hoy = hoyEnCordoba();
  const [importe, setImporte] = useState('');
  const [moneda, setMoneda] = useState<Moneda>('ARS');
  const [fechaPago, setFechaPago] = useState(hoy);
  const [firmanteNombre, setFirmanteNombre] = useState('');
  const [firmanteDni, setFirmanteDni] = useState('');
  const [firmanteContacto, setFirmanteContacto] = useState('');
  const [importeIlegible, setImporteIlegible] = useState(false);
  const { guardando, error, enviar } = useEnvio();
  const requisitos = ficha.requisitosSena ?? [];
  const listo = requisitos.every((r) => r.cumplido);
  const pendientes = requisitos.filter((r) => !r.cumplido);
  const [intento, setIntento] = useState(false);
  const lista = useRef<HTMLUListElement>(null);

  async function confirmar() {
    if (!listo) {
      setIntento(true);
      lista.current?.focus();
      return;
    }
    const valor = leerImporte(importe);
    setImporteIlegible(importe.trim() !== '' && valor === null);
    if (importe.trim() !== '' && valor === null) return;
    const nueva = await enviar(() =>
      fichas.registrarSena(ficha.id, { importe: valor, moneda, fechaPago, firmanteNombre, firmanteDni, firmanteContacto }),
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
          <Button icon="banknote" loading={guardando}
            pendiente={!listo || !importe.trim() || !fechaPago || !firmanteDni.trim()}
            onClick={() => void confirmar()}>Confirmar seña</Button>
        </>
      }
    >
      <div className="sena">
        {error && !error.errores.length && error.codigo !== 'FECHA_PAGO_FUTURA' && <Alert tone="danger">{error.message}</Alert>}
        {requisitos.length > 0 && (
          <div className="sena__requisitos">
            {intento && !listo && (
              <Alert tone="warning" title="Todavía no se puede registrar la seña">
                Falta: {pendientes.map((r) => r.titulo.toLowerCase()).join(', ')}.
              </Alert>
            )}
            <ul ref={lista} tabIndex={-1} className="confirmar__requisitos" aria-label="Requisitos para registrar la seña">
              {requisitos.map((r) => (
                <li key={r.codigo} className={r.cumplido ? 'confirmar__requisito' : 'confirmar__requisito is-pendiente'}>
                  <Icon name={r.cumplido ? 'circle-check' : 'circle-alert'} size={20} />
                  <span className="body">{r.titulo}</span>
                  <span className="body-sm">
                    <span className="v-sr">{r.cumplido ? 'Listo: ' : 'Pendiente: '}</span>
                    {r.detalle}
                  </span>
                </li>
              ))}
            </ul>
            {!listo && (
              <p className="body-sm v-muted">La seña se calcula sobre estos datos. Completá lo que falta para poder registrarla.</p>
            )}
          </div>
        )}
        <div className="sena__par">
          <Select
            label="Moneda"
            options={[{ value: 'ARS', label: 'Pesos (ARS)' }, { value: 'USD', label: 'Dólares (USD)' }]}
            value={moneda}
            onChange={(e) => setMoneda(e.target.value as Moneda)}
            error={error?.errorDe('moneda')}
          />
          <Input
            label="Importe de la seña"
            icon="banknote"
            inputMode="decimal"
            placeholder="0"
            suffix={moneda === 'USD' ? 'dólares' : 'pesos'}
            value={importe}
            onChange={(e) => setImporte(formatearImporte(e.target.value))}
            error={importeIlegible ? 'Escribí el importe con números, p. ej. 150.000.' : error?.errorDe('importe')}
            required
          />
        </div>
        <div className="sena__par">
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
          <Input label="Nombre y apellido" optional value={firmanteNombre} onChange={(e) => setFirmanteNombre(e.target.value)} error={error?.errorDe('firmanteNombre')} />
          <div className="sena__par">
            <Input
              label="DNI"
              inputMode="numeric"
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
