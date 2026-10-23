TaPronto - Parte 5
Ejecucion de pruebas por bloques

REQUISITOS

- Java 21.
- Maven disponible desde la terminal.
- Docker Desktop abierto para las pruebas de integracion y funcionales.
- Git Bash en Windows, o Bash en Linux/macOS.

Copiar los cuatro scripts dentro de Parte5, junto al docker-compose.yml.
Abrir Git Bash en esa carpeta. En Windows se puede abrir la carpeta en el
Explorador, hacer clic derecho y seleccionar Open Git Bash here.

Comprobar las herramientas:

java -version
mvn -version
docker version

ORDEN DE EJECUCION

Ejecutar un bloque por vez y esperar a que termine antes de iniciar el siguiente.
No lanzar estos scripts simultaneamente desde varias terminales o desde IntelliJ.

1. PRUEBAS UNITARIAS

bash tests-unitarios.sh

Ejecuta los tests de turno-validador, turnos-api y facturacion que no estan
dentro de los paquetes integracion o funcionales. No necesita levantar Compose.
Algunos tests del validador se llaman FunctionalTest, pero usan mocks y estan
en el paquete service: tambien se ejecutan en este bloque.

2. PRUEBAS DE INTEGRACION

Preparacion adicional en Windows:

El inicializador escribe el archivo /tmp/esquema-listo. Si la carpeta no
existe, los tests del validador fallan con NoSuchFileException.
Ejecutar una vez en PowerShell, desde la misma unidad donde esta el proyecto:

New-Item -ItemType Directory -Path "\tmp" -Force

En macOS y Linux normalmente /tmp ya existe y este paso no es necesario.
Esta preparacion es para los tests ejecutados en el equipo; los contenedores
Linux tienen su propio sistema de archivos.

Con Docker Desktop abierto:

bash tests-integracion.sh

Ejecuta los tests del paquete integracion de los tres modulos.
Testcontainers prepara los contenedores que necesitan estas pruebas.
La primera ejecucion puede tardar mas por la descarga de imagenes y dependencias.

3. PRUEBAS FUNCIONALES

Levantar el sistema:

docker compose up -d --build

Consultar los contenedores:

docker compose ps

Esperar a que la API responda antes de ejecutar las pruebas:

curl -i http://localhost:8080/establecimientos

Cuando devuelva HTTP 200:

bash tests-funcionales.sh

Ejecuta los tests del paquete funcionales de turnos-api, excepto el de reinicio.
Ese paquete contiene tanto pruebas con mocks como pruebas contra el sistema real.
Las pruebas reales pueden dejar datos guardados en la base.

4. PRUEBA DE REINICIO

Ejecutarla sola y al final, con el sistema levantado:

bash tests-reinicio.sh

Ejecuta RecuperacionFacturacionFunctionalTest (US-68).
El test prepara una reserva atendida, confirma la recepcion de su evento y
detiene y vuelve a levantar todos los servicios del Compose.
Conserva el volumen de la base de datos y comprueba si se recupera la facturacion.
Interrumpe temporalmente la API y Adminer: no usar el sistema durante esta prueba.

RESULTADOS

Hay fallos documentados: el mensaje MQTT mal formado de US-56 y la recuperacion
de facturacion de US-68 fallaron en las ejecuciones revisadas.
Tambien fallo reservaAgendadaOcultaElHorario de PersonalConsultaServiceIntegracionTest:
una reserva Agendada a las 10:00 no excluye las 10:00 de la disponibilidad.
Un resultado rojo debe revisarse: puede ser un defecto conocido, otro defecto
o un problema de preparacion del entorno.

Para guardar la salida de un bloque y verla al mismo tiempo:

bash tests-unitarios.sh 2>&1 | tee resultados-unitarios.txt

bash tests-integracion.sh 2>&1 | tee resultados-integracion.txt

Estos comandos EJECUTAN las pruebas. Usarlos en lugar del comando simple,
no despues, para evitar ejecutar el mismo bloque dos veces.

Como leer los numeros:

- Tests run: cantidad de tests contabilizados.
- Failures: comprobaciones que no se cumplieron.
- Errors: excepciones durante la ejecucion o preparacion.
- Skipped: tests omitidos; no cuentan como aprobados.

DETENER EL SISTEMA

docker compose down
