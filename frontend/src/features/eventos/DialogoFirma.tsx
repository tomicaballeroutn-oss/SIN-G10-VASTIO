import { useState } from 'react';
import { hoyEnCordoba } from '../../api/agenda';
import { CONTRATO_MAXIMO_ARCHIVOS, CONTRATO_MAXIMO_BYTES, CONTRATO_TIPOS, fichas, type Ficha } from '../../api/eventos';
import { useEnvio } from '../../api/useDatos';
import { Alert, Button, Dialog, Input, SelectorArchivos } from '../../ds';
import { fechaCorta } from '../comun/formato';

/** Errores del sistema que van debajo de la fecha o de los archivos, no arriba del formulario. */
const DE_LA_FECHA = ['FECHA_FIRMA_FUTURA', 'FECHA_FIRMA_ANTERIOR_A_SENA'];
const DE_LOS_ARCHIVOS = ['FALTA_CONTRATO', 'DEMASIADOS_ARCHIVOS', 'ARCHIVO_VACIO', 'ARCHIVO_GRANDE', 'FORMATO_NO_ADMITIDO', 'HTTP_413'];

/** Lo que se puede revisar antes de mandar: cantidad, tamaño y tipo declarado. El sistema verifica el contenido. */
function revisarArchivos(archivos: File[]): string | undefined {
  if (archivos.length === 0) return 'Adjuntá el contrato digitalizado.';
  if (archivos.length > CONTRATO_MAXIMO_ARCHIVOS) {
    return `Adjuntá hasta ${CONTRATO_MAXIMO_ARCHIVOS} archivos. Si el contrato tiene más hojas, juntalas en un PDF.`;
  }
  const grande = archivos.find((a) => a.size > CONTRATO_MAXIMO_BYTES);
  if (grande) return `El archivo ${grande.name} pesa más de 10 MB. Sacá la foto con menos resolución o comprimí el PDF.`;
  const otro = archivos.find((a) => a.type && !CONTRATO_TIPOS.split(',').includes(a.type));
  if (otro) return `El archivo ${otro.name} no es PDF, JPG ni PNG. Adjuntá el contrato en alguno de esos formatos.`;
  return undefined;
}

/**
 * UI-12 · Registrar firma de contrato: el evento señado pasa a Contratado con la fecha de firma y el contrato
 * digitalizado (una o más fotos o un PDF). La registran Coordinación y Dirección.
 */
export function DialogoFirma({ ficha, alCerrar, alRegistrar }: {
  ficha: Ficha;
  alCerrar: () => void;
  alRegistrar: (ficha: Ficha) => void;
}) {
  const [fechaFirma, setFechaFirma] = useState(hoyEnCordoba());
  const [archivos, setArchivos] = useState<File[]>([]);
  const [problemaArchivos, setProblemaArchivos] = useState<string>();
  const { guardando, error, enviar } = useEnvio();

  async function confirmar() {
    const problema = revisarArchivos(archivos);
    setProblemaArchivos(problema);
    if (problema) return;
    const nueva = await enviar(() => fichas.registrarFirma(ficha.id, fechaFirma, archivos));
    if (nueva) alRegistrar(nueva);
  }

  const errorFecha = error?.errorDe('fechaFirma') ?? (error && DE_LA_FECHA.includes(error.codigo) ? error.message : undefined);
  const errorArchivos = problemaArchivos ?? (error && DE_LOS_ARCHIVOS.includes(error.codigo) ? error.message : undefined);
  const errorGeneral = error && !error.errores.length && !errorFecha && !errorArchivos ? error.message : undefined;

  return (
    <Dialog
      open
      title="Registrar firma de contrato"
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon="file-check" loading={guardando} onClick={() => void confirmar()}>Registrar firma</Button>
        </>
      }
    >
      <div className="firma">
        <p className="body-sm v-muted">
          {ficha.nombre} · {ficha.salon.nombre} · {fechaCorta(ficha.fecha, true)} · {ficha.turno.nombre}
        </p>
        {errorGeneral && <Alert tone="danger">{errorGeneral}</Alert>}
        <Input
          label="Fecha de firma"
          type="date"
          value={fechaFirma}
          onChange={(e) => setFechaFirma(e.target.value)}
          hint={ficha.sena ? `No puede ser anterior a la seña (${fechaCorta(ficha.sena.fecha, true)}) ni posterior a hoy.` : undefined}
          error={errorFecha}
          required
        />
        <SelectorArchivos
          label="Contrato digitalizado"
          prompt="Sacá una foto o elegí un archivo de la galería"
          hint="PDF, JPG o PNG, hasta 10 MB cada uno. Si son varias hojas, agregalas todas."
          accept={CONTRATO_TIPOS}
          multiple
          files={archivos}
          onChange={(nuevos) => {
            setArchivos(nuevos);
            setProblemaArchivos(undefined);
          }}
          error={errorArchivos}
        />
      </div>
    </Dialog>
  );
}
