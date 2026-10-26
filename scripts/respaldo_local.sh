#!/bin/sh
# Lo ejecuta el servicio respaldo con los clientes de postgres:17.
set -eu
umask 077

etapa=configuracion
hijo=
terminar() {
    codigo=$?
    trap - 0
    trap '' INT TERM
    if [ -n "$hijo" ]; then
        # wait es interrumpible: detener y recoger el hijo antes de salir.
        # Se usa TERM tambien para INT (los comandos asincronos pueden heredar INT ignorada).
        kill -TERM "$hijo" 2>/dev/null || :
        wait "$hijo" 2>/dev/null || :
    fi
    # Nunca desbloquear con flock -u ni borrar el archivo: un hijo superviviente
    # tras SIGKILL conserva su descriptor y debe seguir excluyendo otro escritor.
    exec 9>&-
    if [ "$codigo" -ne 0 ]; then
        printf 'ERROR respaldo etapa=%s codigo=%s\n' "$etapa" "$codigo" >&2
    fi
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

# Componentes simples: no permiten escapar del directorio del entorno.
validar_nombre() {
    case "$1" in
        ''|*[!a-zA-Z0-9_-]*) echo 'Entorno y base requieren letras, numeros, _ o -' >&2; exit 2 ;;
    esac
}
validar_nombre "${RESPALDO_ENTORNO:-}"
validar_nombre "${PGDATABASE:-}"
case "${1:-}" in ''|--once|--transfer-only) ;; *) echo 'Uso: respaldo_local.sh [--once|--transfer-only]' >&2; exit 2 ;; esac
[ "$#" -le 1 ] || exit 2
transferir=${RESPALDO_TRANSFERIR:-0}
case "$transferir" in 0|1) ;; *) exit 2 ;; esac
if [ "${1:-}" = --transfer-only ] && [ "$transferir" != 1 ]; then exit 2; fi
herramienta=$(dirname "$0")/transferir_respaldo.sh
command -v flock >/dev/null || { echo 'Se requiere flock (util-linux)' >&2; exit 2; }

raiz=${RESPALDO_DIRECTORIO:-/respaldos}
legajo=${RESPALDO_LEGAJO:-/legajo}
directorio=$raiz/$RESPALDO_ENTORNO/$PGDATABASE
# No seguir enlaces en los directorios que delimitan la retencion.
for ruta in "$raiz" "$raiz/$RESPALDO_ENTORNO" "$directorio" "$directorio/legajo" "$directorio/.respaldo.lock"; do
    [ ! -L "$ruta" ] || { echo 'Directorio de respaldo enlazado: revisar configuracion' >&2; exit 2; }
done
mkdir -p "$directorio"

while true; do
    etapa=bloqueo
    # Archivo estable; su existencia NO implica bloqueo. El kernel lo libera
    # al cerrar el ultimo descriptor, incluso tras KILL. No truncar ni eliminar.
    exec 9>>"$directorio/.respaldo.lock"
    if ! flock -n 9; then
        echo 'Otro respaldo activo para este entorno y base' >&2
        exit 1
    fi
    if [ "${1:-}" = --transfer-only ]; then
        etapa=transferencia
        ejecutar /bin/sh "$herramienta" "$directorio" enviar
        exit 0
    fi
    etapa=pg_dump
    # Nombre exclusivo, incluso tras reinicios dentro del mismo segundo.
    parcial=$(mktemp "$directorio/vastio-$(date -u +%Y%m%d-%H%M%S)-XXXXXX.partial")
    ejecutar pg_dump -Fc -f "$parcial"
    etapa=validar_archivo
    [ -s "$parcial" ]
    # Inspeccionar el archivo no equivale a probar una restauracion.
    ejecutar pg_restore --list "$parcial" > /dev/null
    etapa=legajo
    ejecutar mkdir -p "$directorio/legajo"
    ejecutar cp -ru "$legajo/." "$directorio/legajo/"
    etapa=publicar
    archivo=${parcial%.partial}.dump
    ejecutar mv "$parcial" "$archivo"
    printf 'OK respaldo publicado=%s\n' "$archivo"
    transferencia_codigo=0
    if [ "$transferir" = 1 ]; then
        etapa=preparar_transferencia
        if ejecutar /bin/sh "$herramienta" "$directorio" preparar "$archivo"; then
            :
        else
            transferencia_codigo=$?
            printf 'ERROR respaldo etapa=preparar_transferencia codigo=%s; copia_local_conservada\n' "$transferencia_codigo" >&2
        fi
        # El fallo del paquete nuevo no impide enviar pendientes independientes.
        etapa=transferencia
        if ejecutar /bin/sh "$herramienta" "$directorio" enviar; then
            :
        else
            envio_codigo=$?
            [ "$transferencia_codigo" -ne 0 ] || transferencia_codigo=$envio_codigo
            printf 'ERROR respaldo etapa=transferencia codigo=%s; copia_local_conservada\n' "$envio_codigo" >&2
        fi
    fi
    etapa=retencion
    # Solo dumps regulares de ESTA base y entorno; no recorre subdirectorios.
    if [ "$transferencia_codigo" -eq 0 ]; then
        ejecutar find "$directorio" -maxdepth 1 -type f -name 'vastio-*.dump' -mtime +14 -delete
    fi
    # Sin bloqueo ni descriptor heredable durante la espera diaria.
    exec 9>&-
    if [ "$transferencia_codigo" -eq 0 ]; then
        printf 'OK respaldo ciclo_completo entorno=%s base=%s\n' "$RESPALDO_ENTORNO" "$PGDATABASE"
    fi
    if [ "${1:-}" = --once ]; then
        if [ "$transferencia_codigo" -ne 0 ]; then etapa=transferencia; fi
        exit "$transferencia_codigo"
    fi
    etapa=espera
    ejecutar sleep 86400
done
