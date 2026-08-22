# Parte 1
Se pide:
1. Proponer la Visión del Producto (versión 1), utilizando la plantilla de visión de Moore.
2. Desarrollar un Prototipo v1:
- a. Instalar Docker
- b. Usar docker y docker compose para ejecutar:
    -  Broker MQTT (Eclipse Mosquitto)
- c. Simulación de publish / subscribe con comandos (ej. curl o comandos de mosquitto).
- d. Usar un cliente de MQTT para monitorear mensajes (Ej. MQTT Explorer)
3. Crear proyecto en Github
4. Entregar documento de Visión, docker-compose.yml, shell script con los comandos para
publicar y consumir mensajes.

Nota: Los mensajes a publicar en Mosquitto de las solicitudes de turno tendrán formato JSON. La
representación del formato será la que figura en el Anexo.

### Anexo
Formato JSON a publicar en Mosquitto:
{
"status":"turno_creado",
"fechaHora":"2026-09-01T10:15:00",
"turno":{
"id":35,
"email_cliente":”a@a.com”,
“telefono_cliente:11111111,
"idPersonal":8,
"fecha":"2026-09-15",
"hora":"14:30"
}
}
