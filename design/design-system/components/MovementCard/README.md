Registro inmutable de un movimiento de mercadería (entrega a barra, devolución, retiro adicional o ingreso) imputado a un evento y a una persona.

**Vos pasás:** `tipo` (`entrega`, `devolucion`, `retiro-adicional`, `ingreso`), `evento`, `salon`, `desde` y `hasta` (ubicaciones), `items` (`[{nombre, cantidad, unidad?, unidadUno?}]`), `nota`, `actor` y `at`.

- Muestra siempre quién y cuándo, y aclara "No editable · se corrige con un ajuste": el movimiento no se modifica ni se borra (RNF-SEG-03).
- Una devolución con `nota` puede mostrar el consumo real, en lenguaje de atribución, no de reproche.
- Para cargar un movimiento nuevo usá `Stepper` por ítem y un `Button` `lg`; para ver muchos, usá `Table`.
