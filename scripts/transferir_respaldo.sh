#!/bin/sh
# Uso interno: respaldo_local.sh mantiene flock durante preparar/enviar.
# No ejecutar directamente en paralelo con el respaldo.
set -eu
umask 077
etapa=transferencia_configuracion
hijo=
terminar() {
    codigo=$?
    trap - 0
    trap '' INT TERM
    if [ -n "$hijo" ]; then
        kill -TERM "$hijo" 2>/dev/null || :
        wait "$hijo" 2>/dev/null || :
    fi
    if [ "$codigo" -ne 0 ]; then printf 'ERROR transferencia etapa=%s codigo=%s\n' "$etapa" "$codigo" >&2; fi
    exit "$codigo"
}
trap terminar 0
trap 'exit 130' INT
trap 'exit 143' TERM
ejecutar() {
    "$@" &
    hijo=$!
    if wait "$hijo"; then resultado=0; else resultado=$?; fi
    hijo=
    return "$resultado"
}
directorio=$1
modo=$2
paquetes=$directorio/transferencias
[ ! -L "$paquetes" ] || exit 2
mkdir -p "$paquetes"

if [ "$modo" = preparar ]; then
    archivo=$3
    nombre=$(basename "$archivo" .dump)
    case "$nombre" in vastio-*[!a-zA-Z0-9_-]*|''|*[!a-zA-Z0-9_-]*) exit 2 ;; vastio-*) ;; *) exit 2 ;; esac
    [ -s "$archivo" ] && [ ! -L "$archivo" ]
    etapa=empaquetar
    temporal=$(mktemp -d "$paquetes/$nombre-XXXXXX.partial")
    # Miembros explicitos: no incluye .partial, locks ni otros dumps de la carpeta.
    # El legajo se congela dentro del tar; los reintentos reutilizan esos mismos bytes.
    ejecutar tar --exclude='*.partial' --exclude='*.tmp' --exclude='.respaldo.lock' --exclude='.en-curso' \
        -cf "$temporal/datos.tar" -C "$directorio" "$nombre.dump" legajo
    etapa=hashes
    (cd "$temporal" && sha256sum datos.tar > SHA256SUMS)
    printf 'Paquete completo M-10\n' > "$temporal/COMPLETO"
    etapa=publicar_paquete
    [ ! -e "$paquetes/$nombre.listo" ]
    ejecutar mv "$temporal" "$paquetes/$nombre.listo"
    printf 'OK transferencia paquete_preparado\n'
    exit 0
fi
[ "$modo" = enviar ] || exit 2
command -v rclone >/dev/null || exit 2
[ -f "${RCLONE_CONFIG:-}" ] || { echo 'Falta archivo externo de configuracion rclone' >&2; exit 2; }
# Solo remoto:ruta simple. No admitir conexiones inline, URI ni parametros/secretos.
destino=${RCLONE_DESTINO:-}
case "$destino" in ''|*[!a-zA-Z0-9_:/-]*|*:*:*|*..*) echo 'Destino rclone invalido' >&2; exit 2 ;; esac
remoto=${destino%%:*}
case "$remoto" in ''|*[!a-zA-Z0-9_-]*) exit 2 ;; esac
case "$destino" in "$remoto":?*) ;; *) exit 2 ;; esac
destino=${destino%/}/$RESPALDO_ENTORNO/$PGDATABASE
destino_huella=$(printf '%s' "$destino" | sha256sum | cut -d ' ' -f 1)

procesar_paquete() {
    # Dentro de un if POSIX desactiva errexit: comprobar cada paso explicitamente.
    etapa=validar_paquete
    [ ! -L "$paquete" ] || return 2
    if [ -f "$paquete/.enviado" ]; then
        confirmacion=$(cat "$paquete/.enviado") || return "$?"
        [ "$confirmacion" != "$destino_huella" ] || return 0
    fi
    [ -f "$paquete/COMPLETO" ] || return 2
    etapa=verificar_local
    (cd "$paquete" && sha256sum -c SHA256SUMS > /dev/null 2>&1) || return "$?"
    # La salida cruda de rclone no llega a logs: puede contener rutas/errores del proveedor.
    # No usar sync/move/purge: nunca se eliminan archivos locales ni remotos.
    etapa=rclone_copy
    ejecutar rclone copy "$paquete" "$destino/$nombre" --config "$RCLONE_CONFIG" \
        --include /datos.tar --include /SHA256SUMS --checksum \
        --retries 1 --low-level-retries 1 --contimeout 15s --timeout 1m --max-duration 30m > /dev/null 2>&1 || return "$?"
    etapa=rclone_check
    ejecutar rclone check "$paquete" "$destino/$nombre" --config "$RCLONE_CONFIG" \
        --include /datos.tar --include /SHA256SUMS --download \
        --retries 1 --low-level-retries 1 --contimeout 15s --timeout 1m --max-duration 30m > /dev/null 2>&1 || return "$?"
    etapa=rclone_completo
    ejecutar rclone copyto "$paquete/COMPLETO" "$destino/$nombre/COMPLETO" --config "$RCLONE_CONFIG" \
        --checksum --immutable --retries 1 --low-level-retries 1 --contimeout 15s --timeout 1m --max-duration 30m > /dev/null 2>&1 || return "$?"
    # Ack local solo despues de copy + check + marcador remoto. Repetible tras un corte.
    etapa=confirmar_local
    printf '%s\n' "$destino_huella" > "$paquete/.enviado" || return "$?"
    printf 'OK transferencia paquete_verificado paquete=%s\n' "$nombre"
    return 0
}

codigo_final=0
for paquete in "$paquetes"/vastio-*.listo; do
    [ -d "$paquete" ] || continue
    nombre=$(basename "$paquete" .listo)
    # Solo identificadores seguros en logs, nunca rutas/configuracion del remoto.
    case "$nombre" in
        ''|*[!a-zA-Z0-9_-]*)
            printf 'ERROR transferencia paquete=nombre_invalido etapa=validar_paquete codigo=2\n' >&2
            [ "$codigo_final" -ne 0 ] || codigo_final=2
            continue ;;
    esac
    if procesar_paquete; then
        :
    else
        codigo_paquete=$?
        printf 'ERROR transferencia paquete=%s etapa=%s codigo=%s; pendiente_conservado\n' "$nombre" "$etapa" "$codigo_paquete" >&2
        [ "$codigo_final" -ne 0 ] || codigo_final=$codigo_paquete
    fi
done
etapa=resumen_paquetes
exit "$codigo_final"
