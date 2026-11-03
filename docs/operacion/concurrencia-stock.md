# Recuentos de stock: coordinación transaccional

## Defecto reproducido

`SELECT ... FOR UPDATE` sobre `stock_ubicacion` solo bloqueaba filas existentes. Si el primer recuento todavía no había
confirmado su nueva fila, otro recuento podía leer saldo ausente (cero), calcular otra diferencia absoluta y esperar
recién en el `UPSERT`. Al continuar, sumaba esa diferencia obsoleta.

La prueba `RecuentoConcurrenciaIT` retiene el commit del primer recuento y verifica con `pg_blocking_pids` la espera real
del segundo. Antes de corregir, fallaron ambos casos sin fila previa; pasaron los dos con fila existente.

## Alternativas evaluadas

| Alternativa | Correctitud e interacción | Alcance, costo y complejidad |
|---|---|---|
| Bloquear una ubicación existente | Funciona sin fila de stock; inventario inicial ya usa esa ancla. Recuentos y todos los movimientos deben coordinarse sobre ella. | Serializa bebidas distintas de una ubicación. Ubicaciones distintas son independientes. No requiere migración. |
| Asegurar una fila cero y luego bloquearla | Protege la combinación y se coordina con el UPSERT. Debe hacerse antes de leer, en sentencias distintas. | Más granular, pero introduce filas técnicas y exige resolver su ciclo de vida para conservar cero contado sin crear stock. También debe ordenar los bloqueos frente a inventario inicial y las FK. |
| Advisory lock transaccional por bebida y ubicación | Protege claves inexistentes si todos los escritores participan. Requiere una lectura posterior al bloqueo, no en la misma instantánea SQL. | Más granular; exige definir claves y coordinar el orden con el bloqueo de ubicación de inventario inicial y las FK, evitando ciclos. Sin migración, con mayor complejidad. |
| Bloquear la bebida | También protege combinaciones inexistentes si participan todos los escritores. | Serializa ubicaciones diferentes del mismo artículo y no aprovecha el ancla de inventario inicial. |
| Aislamiento SERIALIZABLE con reintentos | Puede detectar conflictos, pero requiere reintentar transacciones completas y definir su tratamiento HTTP. | Mayor alcance y complejidad; no se eligió para esta corrección localizada. |

## Solución elegida: una única ancla por ubicación

1. El recuento toma `FOR NO KEY UPDATE` sobre la fila de `ubicacion`.
2. En una **segunda sentencia**, lee el saldo de la bebida. El aislamiento actual es `READ COMMITTED`: después de esperar
   observa el commit anterior. Las pruebas comprueban el aislamiento efectivo de cada transacción.
3. Calcula `diferencia = contado - saldo` y, si corresponde, registra un `AJUSTE` mediante `MovimientoService`.
4. Todo movimiento toma esa misma ancla **antes** de insertar su asiento y actualizar stock. El bloqueo dura hasta el
   commit o rollback de la transacción del caso de uso. Tomarlo otra vez dentro de esa transacción no la bloquea.

Se reemplazó el bloqueo explícito de la fila de saldo; no se agregaron advisory locks ni filas técnicas. El UPSERT conserva
su actualización atómica y sus bloqueos internos habituales. Los movimientos históricos no se actualizan ni se borran.

Se usa `NO KEY UPDATE`, compatible con `KEY SHARE`, porque ingresos ya pueden haber insertado una cabecera con FK a la
ubicación. Usar `FOR UPDATE` podría provocar un ciclo de upgrades entre ingresos concurrentes. Hibernate ya utiliza
`FOR NO KEY UPDATE` para el bloqueo pesimista de la ubicación en inventario inicial.

## Interacción con operaciones existentes

- **Recuentos:** el segundo lee el saldo aplicado por el primero, con o sin fila previa. El orden de llegada HTTP no
  define el orden de adquisición. Si no hay diferencia, no se insertan movimientos ni filas de stock.
- **Ingresos y mermas:** participan desde `MovimientoService`; sus incrementos/decrementos se aplican antes o después del
  recuento, sin intercalarse entre su lectura y escritura.
- **Inventario inicial:** ya toma el bloqueo de ubicación antes de comprobar si la carga está cerrada. Comparte el ancla:
  si el recuento se confirma primero, la carga inicial espera y después se rechaza por `CARGA_INICIAL_CERRADA`.
- **Saldo negativo:** bajo esta serialización, `saldo + (contado - saldo) = contado`; un recuento HTTP válido (no negativo)
  termina con ese saldo no negativo. Una merma posterior sí puede volverlo negativo y conserva su notificación existente.

## Alcance y límites

- Es un bloqueo **por ubicación**, no por combinación ni global. Bebidas distintas de la misma ubicación esperan;
  distintas ubicaciones avanzan en paralelo. Se acepta esta menor concurrencia para compartir el protocolo ya existente
  de carga inicial y mantener una solución simple. Una carga grande retiene la ubicación hasta su commit.
- Los casos de uso actuales afectan una ubicación por transacción. `MovimientoService` ordena por id ascendente las
  ubicaciones de un movimiento. Futuros lotes con varias ubicaciones deberán adquirir **todo** su conjunto en ese orden
  desde el inicio; ordenar cada movimiento por separado no garantiza ausencia de deadlocks entre lotes.
- El protocolo presupone `READ COMMITTED`, como el entorno verificado. Cambiar el aislamiento requiere reevaluar la
  lectura posterior a la espera y el tratamiento de errores/reintentos; no se declara soporte para otra configuración.
- Las escrituras SQL externas que omitan este protocolo no quedan coordinadas. La aplicación actual actualiza stock
  exclusivamente a través de `MovimientoService`; no se introduce un trigger ni se modifica el contrato HTTP.

## Pruebas reales

Transacciones en conexiones independientes, sin transacción envolvente en el test. Latches retienen y liberan commits;
`pg_backend_pid` y `pg_blocking_pids` confirman el solapamiento y el bloqueo efectivo. Hay límites de espera, de sentencia
y de duración de prueba. La espera no se infiere de un `sleep` ni de la mera largada simultánea de dos hilos.

Se cubren ambos órdenes de cantidades con/sin fila, ausencia de diferencias, paralelismo entre ubicaciones, serialización
deliberada entre bebidas de una ubicación, ingresos, mermas, carga inicial y recuentos desde un saldo negativo.

Referencia: [bloqueos explícitos de PostgreSQL 17](https://www.postgresql.org/docs/17/explicit-locking.html) y
[aislamiento de transacciones](https://www.postgresql.org/docs/17/transaction-iso.html).
