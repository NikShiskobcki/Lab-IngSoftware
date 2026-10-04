# TaPronto – Parte 3

Sistema de gestión de establecimientos, personal y reservas de turnos.

## Tecnologías

- Java 21
- Spring Boot
- MariaDB
- Mosquitto MQTT
- Docker Compose

## Requisitos

Se necesita tener instalado:

- Docker Desktop
- Bash
- curl
- jq

## Instalación en Ubuntu o WSL

Para instalar curl y jq:

sudo apt update
sudo apt install curl jq -y

## Uso desde Git Bash en Windows

Abrir la carpeta Parte3 en el Explorador de archivos, hacer clic derecho dentro de ella y seleccionar Open Git Bash here.

Windows normalmente ya incluye curl. Para instalar jq, ejecutar desde PowerShell:

winget install jqlang.jq

Después de instalarlo, cerrar y volver a abrir Git Bash.

Para comprobar las instalaciones:

curl --version
jq --version
docker --version

Docker Desktop debe estar abierto antes de ejecutar el proyecto.

## Ejecución

Desde WSL o Git Bash, ubicarse dentro de la carpeta Parte3 y ejecutar:

docker compose up -d --build

Para verificar los contenedores:

docker compose ps

Para comprobar que la API responde:

curl http://localhost:8080/personal

La API estará disponible en:

http://localhost:8080

La documentación Swagger puede consultarse en:

http://localhost:8080/swagger-ui.html

## Ejemplo completo

El script demo-completo.sh demuestra el uso integrado de las API mediante curl.

El ejemplo crea un establecimiento, crea un personal asociado, genera una reserva, espera su procesamiento mediante MQTT, consulta la reserva desde la API y muestra los datos persistidos en MariaDB.

Para ejecutarlo:

bash demo-completo.sh

Flujo demostrado:

curl → API de Establecimientos → API de Personal → API de Reservas → Mosquitto → Subscriber → MariaDB

Los datos creados por este ejemplo no se eliminan, para permitir comprobar que quedaron persistidos en la base de datos.

## Scripts de prueba

Para probar las API individualmente:

bash testing-establecimientos.sh
bash testing-personal.sh
bash testing-reservas.sh

Para visualizar las reservas guardadas en la base de datos:

bash ver-turnos-db.sh

## Detener el sistema

docker compose down

Para eliminar también los datos almacenados:

docker compose down -v

## Parte 4 – Servicio de facturación (US-40, US-41, US-42)

Flujo completo:

curl → API de Reservas → Mosquitto → Subscriber → MariaDB → **turno-validador** (cada 1 min) → **facturacion** (cada 5 min) → MariaDB

### Servicio `facturacion`

Contenedor independiente del validador (carpeta `facturacion/`, Java 21 + Spring Boot + JPA, igual que `turnos-api`). Cada 5 minutos:

1. Busca los turnos en estado `Atendido`.
2. Por cada turno, en **una única transacción**:
   - obtiene (o crea) el cliente, identificado por su **email único** (sin distinguir mayúsculas);
   - obtiene (o crea) la factura de ese cliente para el **mes del turno**;
   - agrega un ítem con el costo de la consulta y recalcula el total de la factura;
   - cambia el estado del turno a `Facturado`.
3. Si algo falla, se revierte todo el turno (queda `Atendido`) y se reintenta en el ciclo siguiente. Un error en un turno no frena a los demás.
4. Cada ejecución registra en el log la hora de inicio, turnos encontrados, facturados, omitidos y errores.

Configuración por variable de entorno (ver `docker-compose.yml`):

| Variable | Por defecto | Descripción |
|---|---|---|
| `FACTURACION_INTERVALO_MS` | `300000` | Intervalo entre ejecuciones (5 min) |
| `FACTURACION_DELAY_INICIAL_MS` | `15000` | Espera antes de la primera ejecución |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | ver compose | Conexión a MariaDB |

### Tablas nuevas

Se declaran como entidades JPA (`model/Cliente`, `Factura`, `ItemFactura`) y Hibernate las crea al arrancar (`ddl-auto: update`). `personal` y `reservas_turnos` las sigue creando `turno-subscriber`: `facturacion` las mapea solo para leerlas (`model/Personal`, `model/Reserva`) y Hibernate no las modifica.  `items_factura.id_turno` referencia a `reservas_turnos` sin FK física (queda el `UNIQUE`). Si el ciclo corre antes de que existan las tablas base, falla, se registra en el log y se reintenta en el siguiente.

- `clientes` (`email` UNIQUE)
- `facturas` (una por cliente y mes: UNIQUE `id_cliente, anio, mes`; `total`, `estado`)
- `items_factura` (`id_turno` UNIQUE: un turno no puede facturarse dos veces)

### Scripts de la Parte 4

```
bash demo-facturacion.sh       # ejemplo completo con curl (tarda varios minutos: espera al validador y a la facturación)
bash testing-facturacion.sh    # prueba con verificaciones automáticas de la facturación
bash ver-facturas-db.sh        # muestra clientes, facturas, ítems y turnos por estado
docker compose logs -f facturacion
```

Para ver el servicio actuar más rápido durante una prueba, bajar `FACTURACION_INTERVALO_MS` en el compose (por ejemplo `30000`) y reiniciar con `docker compose up -d facturacion`.

### Nota sobre el estado `Atendido`

El paso `Agendado → Atendido` (turno cuya fecha ya pasó) no forma parte del servicio de facturación. Los scripts de prueba lo simulan actualizando el estado directamente en la base de datos.
