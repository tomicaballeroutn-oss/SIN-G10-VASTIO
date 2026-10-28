# A1-02 — Evidencia de auditoría del PR #50

Base original: 954d579c52a903fa0c5ad958738f68b66c23d5dc. main inspeccionado: feb8ae3d3c5ae14d258f3d226caa1260a282919b.
La actualización de base es un merge de main; las correcciones de cuenta de #49/#53 no se incorporan como cambios de A1-02.

## Exclusividad y atomicidad

El protocolo de escritura existente se conserva:
- Asegurar la unidad mediante INSERT ... ON CONFLICT DO NOTHING.
- Tomar SELECT ... FOR UPDATE de unidad_comercializable antes de consultar evento y bloqueo activos.
- En bloqueos masivos, recorrer salón, fecha y turno en orden ascendente.
- Pre-reserva y reprogramación usan TomaDeUnidad. Reprogramación toma primero el evento y luego su única unidad de destino.
- Desbloqueo serializa sobre la cabecera de bloqueo. Antes de su commit, otra solicitud aún puede recibir un conflicto; después puede reintentarse.

BloqueoProtocoloIT añade 15 casos con transacciones independientes, barreras de commit y pg_blocking_pids:
- Dos bloqueos; ambos órdenes contra pre-reserva y reprogramación, con y sin unidad previa.
- Rangos parcialmente superpuestos y salones/turnos enviados en orden inverso.
- Dos desbloqueos: uno confirma y el otro recibe BLOQUEO_YA_DESBLOQUEADO.
- Nuevo bloqueo o pre-reserva durante un desbloqueo sin confirmar y después del commit.
- Rollback del ganador mientras una pre-reserva espera.

Los casos verifican que no coexistan evento y bloqueo activos ni dos bloqueos activos sobre la misma unidad.
No son una demostración universal de ausencia de deadlocks frente a futuros escritores o SQL externo.

BloqueoAuditoriaIT verifica rollback real de doce unidades ante conflictos de evento o bloqueo en las posiciones 0, 3 y 11 (primer día, día intermedio y último día); también un fallo posterior al alta. No quedan cabeceras, relaciones ni unidades parciales.
BloqueoValidacionIT amplía nulos, listas vacías, duplicados, longitud exacta de detalle, las tres operaciones para los perfiles permitidos, ausencia de autenticación y roles combinados.
Los usuarios de las nuevas pruebas completaron explícitamente el primer ingreso, para conservar su validez al integrar #49.

## Correcciones demostradas

1. El listado materializaba bloqueos activos ya pasados y sus unidades antes de descartarlos en Java. La consulta selecciona ahora las cabeceras que tienen alguna unidad desde hoy y trae todas las unidades de esas cabeceras. Así conserva el rango original cuando un bloqueo cruza el día actual. La prueba reproduce el exceso antes del cambio y comprueba la carga después.
2. La pantalla recibía responsable y fecha de registro, pero no los mostraba. La guía del sistema exige quién y cuándo para las acciones que comprometen unidades. Se usa Actor y fechaHora existentes, con aserciones y prueba de navegador.

No se cambian el protocolo de locks, el límite comercial, los permisos, el esquema ni las migraciones.

## Mediciones locales

PostgreSQL 17 en Docker Desktop, Java 25; muestras individuales, sin carga de producción ni SLA definido.
SQL es la cantidad de sentencias preparadas informada por Hibernate, no un EXPLAIN del coste del servidor.

| Alta de 180 días | Unidades | SQL | Duración antes de la corrección del listado |
|---|---:|---:|---:|
| Un salón, un turno | 180 | 551 | 1.202 ms |
| Un salón, dos turnos | 360 | 1.092 | 2.466 ms |
| Tres salones, dos turnos | 1.080 | 3.254 | 7.585 ms |

El alta sigue siendo lineal y usa el mismo protocolo. No se atribuyen al cambio del listado diferencias de tiempo observadas entre ejecuciones.
La duración medida incluye el commit y da una cota superior aproximada del tiempo de retención de las primeras unidades; no se midieron todos los locks individualmente.
El delta de heap de la muestra de 1.080 unidades fue aproximadamente 64 MiB; otras muestras reflejan GC, incluso deltas negativos. No equivale a memoria pico ni a un presupuesto de producción.

| Listado con un bloqueo pasado de 1.080 unidades y otro vigente de una unidad | Antes | Después |
|---|---:|---:|
| Unidades materializadas | 1.081 | 1 |
| Cabeceras materializadas | 2 | 1 |
| SQL preparado | 6 | 6 |
| Duración de una muestra | 48,05 ms | 16,87 ms |

La reducción de materialización es determinista para ese escenario. Los tiempos dependen del entorno y no constituyen un benchmark estadístico.
SQL todavía debe comprobar la vigencia de las cabeceras históricas; no se afirma coste constante sobre todo el historial.
No se agrega un límite de salones o turnos ni una migración sin evidencia.

## Reproducción

Desde backend:
~~~powershell
mvn -B "-Dit.test=BloqueoIT,BloqueoConcurrenciaIT,BloqueoAuditoriaIT,BloqueoProtocoloIT,BloqueoValidacionIT,PreReservaIT,ReprogramacionIT,AgendaIT,AutenticacionIT" verify
mvn -B verify
~~~
La prueba externa de resguardo requiere VASTIO_TEST_PYTHON y Docker; el CI la configura.

Desde frontend:
~~~powershell
npm test -- --maxWorkers=1
npm run lint
npm run build
$env:E2E_URL='http://127.0.0.1:5180'
$env:E2E_BROWSER_CHANNEL='msedge'
npm run e2e -- bloqueos.spec.ts --workers=1
~~~
El ensayo Playwright de bloqueos utiliza API simulada y verifica conflicto 409, datos conservados, registro/Actor, agenda, cancelación de confirmación y desbloqueo en escritorio y teléfono. No sustituye la prueba de concurrencia PostgreSQL ni la aceptación de un despliegue completo.
