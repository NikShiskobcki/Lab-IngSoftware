@echo off
REM ==============================================================================
REM Script Windows para visualizar eventos generados y consumidos por MQTT
REM ==============================================================================
echo ==================================================================
echo  Monitoreando eventos de reserva de turnos en tiempo real...
echo  Presiona Ctrl+C para salir.
echo ==================================================================
docker compose logs -f --tail=20 turno-publisher turno-subscriber
pause
