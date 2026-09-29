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
