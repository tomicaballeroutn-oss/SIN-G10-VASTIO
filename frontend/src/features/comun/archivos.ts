/** Ofrece guardar un archivo bajado con la sesión (no se puede con un enlace común: el pedido lleva el token). */
export function guardarArchivo(contenido: Blob, nombre: string) {
  const url = URL.createObjectURL(contenido);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  // Se libera después: algunos navegadores todavía están leyendo la URL cuando vuelve el click.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
