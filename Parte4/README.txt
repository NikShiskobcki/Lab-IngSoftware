TaPronto - Parte 4

Sistema de reservas con validacion y facturacion asincronas mediante MQTT.

REQUISITOS
Docker Desktop en ejecucion, Bash, curl y jq.
Docker compila los servicios: no hace falta instalar Java o Maven para ejecutarlos.

En Windows, abrir Git Bash dentro de la carpeta Parte4.
Para comprobar las herramientas:
curl --version
jq --version
docker --version

En Ubuntu o WSL, instalar curl y jq:
sudo apt update
sudo apt install curl jq -y

Si se usa WSL, debe estar habilitada su integracion con Docker Desktop.

EJECUCION
Todos los comandos siguientes se ejecutan dentro de Parte4.

docker compose up -d --build
docker compose ps

Esperar que reservas-db y turno-validador aparezcan healthy.
Antes de enviar reservas, comprobar las suscripciones MQTT:
docker logs --tail 20 turno-validador
docker logs --tail 20 facturacion

API: http://localhost:8080
Adminer: http://localhost:8081

En Adminer:
Sistema: MySQL
Servidor: mariadb
Usuario: reservas_app
Contrasena: admin
Base de datos: reservas

FUNCIONAMIENTO
La API guarda la reserva como SOLICITADO y publica un evento en turnos/reservas.
El validador consume el evento y revisa las solicitudes cada minuto.
Una solicitud valida queda Agendado; una invalida u ocupada queda rechazada.
Cuando pasa la fecha y hora de un turno agendado, cambia a Atendido y se publica
un evento en turnos/atendidos.
Facturacion consume esos eventos y los procesa cada cinco minutos.
Crea o reutiliza el cliente por email y la factura correspondiente al cliente,
anio y mes del turno. Agrega un item, recalcula el total y cambia a Facturado.
El subscriber muestra los eventos de reservas en los logs.
Las reservas atendidas o facturadas no pueden modificarse ni eliminarse por la API.

EJEMPLO COMPLETO
bash demo-completo.sh

Crea un establecimiento, personal y una reserva mediante curl, muestra los
codigos HTTP y los datos persistidos. Para demostrar la atencion sin esperar
al dia del turno, cambia su fecha en la base una vez agendado.
Los servicios realizan los cambios de estado y la facturacion.
Los datos de la demostracion quedan guardados.

PRUEBAS
bash testing-validaciones.sh

Prueba una reserva valida, establecimiento inexistente, personal de otro
establecimiento, fecha pasada, horario ocupado y datos de entrada invalidos.
Muestra los codigos HTTP y consulta las reservas persistidas.

bash testing-facturacion.sh

Prueba dos turnos del mismo cliente en un mes y otro turno en un mes distinto.
Comprueba una factura con dos items y total 3000, otra factura con un item y
total 1500, y un unico cliente. Simula las fechas pasadas de los turnos.

Cada script crea sus propios datos y los deja guardados.
Las esperas dependen de los ciclos: validacion cada minuto y facturacion cada
cinco minutos. El ejemplo y la prueba de facturacion pueden demorar varios minutos.

POSTMAN
Flujo_Postman_Parte4.txt contiene los pasos y cuerpos JSON para la demostracion
manual. Los IDs deben reemplazarse por los devueltos por la API.
El ejemplo mediante curl se ejecuta con demo-completo.sh.

DATOS PERSISTIDOS
En Adminer se pueden consultar establecimientos, personal, reservas_turnos,
clientes, facturas e items_factura.

DETENER
Docker conserva los datos al ejecutar:
docker compose down

Para eliminar todos los datos de este proyecto y comenzar desde cero:
docker compose down -v
docker compose up -d --build

La base inicia sin datos de ejemplo; los scripts crean los datos necesarios.
