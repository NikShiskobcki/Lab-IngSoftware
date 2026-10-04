# Historias de usuario – Facturación (Parte 4)

## US-40 – Ejecución periódica del proceso de facturación
**Como** sistema, **quiero** ejecutar automáticamente cada 5 minutos un proceso de facturación, **para** generar los cargos correspondientes a los turnos ya atendidos sin intervención manual.

**Criterios de aceptación**
- El proceso corre en un servicio independiente del de validación de turnos (contenedor `facturacion` en el `docker-compose.yml`).
- Se ejecuta con una periodicidad de 5 minutos (`FACTURACION_INTERVALO_MS=300000`, parametrizable).
- Una ejecución no se superpone con otra: si la anterior sigue en curso, la siguiente se omite.
- Si una ejecución falla, el error se registra en el log y el servicio sigue funcionando para la siguiente.
- Cada ejecución registra en el log la hora de inicio, turnos encontrados, facturados, omitidos y errores.
- Si no hay turnos en estado `Atendido`, la ejecución termina sin efectos ni errores.
- Si la base de datos aún no está lista al arrancar, el servicio espera y reintenta.

## US-41 – Generación de ítems de factura por cliente y mes
**Como** sistema, **quiero** recorrer todos los turnos en estado `Atendido` y generar un ítem de factura asociado al cliente y al mes correspondiente, **para** consolidar los cargos por período.

**Criterios de aceptación**
- El cliente se identifica de forma única por su email (normalizado a minúsculas y sin espacios); si no existe, se crea.
- El ítem queda asociado al mes de la fecha del turno (no al de ejecución del proceso).
- Si el cliente no tiene factura para ese mes, el sistema la crea; si ya tiene, el ítem se agrega a la misma (sin duplicar facturas).
- El ítem referencia el turno, e incluye descripción y monto (costo de consulta del personal).
- El total de la factura se recalcula como la suma de sus ítems.
- Un mismo turno genera como máximo un ítem (restricción única sobre `items_factura.id_turno`).
- Las entidades `Cliente`, `Factura` e `Item de factura` están reflejadas en el MER (`Parte4_TaPronto_MER.pdf`).

## US-42 – Marcado de turnos como Facturados
**Como** sistema, **quiero** cambiar el estado del turno a `Facturado` una vez que fue incluido en la factura del mes, **para** no facturarlo dos veces en corridas posteriores.

**Criterios de aceptación**
- Un turno `Facturado` nunca vuelve a ser tomado por el proceso de facturación.
- El cambio a `Facturado` ocurre solo después de agregar el ítem a la factura.
- La creación del ítem, el recálculo del total y el cambio de estado se hacen en una única transacción: si falla algo, no se aplica nada y el turno sigue `Atendido` para reintentarse.
- Un error en un turno no impide facturar los demás.
- Solo se facturan turnos `Atendido`; los demás estados (`Agendado`, `Rechazado/...`, etc.) no se tocan.
- Los datos pueden verificarse en la base de datos (`bash ver-facturas-db.sh`).
