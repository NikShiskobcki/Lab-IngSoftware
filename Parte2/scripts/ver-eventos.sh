#!/bin/bash
# ==============================================================================
# Script para visualizar los eventos de reservas generados y consumidos por MQTT
# ==============================================================================

echo "=================================================================="
echo " Monitoreando eventos de reserva de turnos en tiempo real...      "
echo " Presiona Ctrl+C para detener el monitoreo.                       "
echo "=================================================================="

# Muestra los logs en tiempo real combinando publisher y subscriber
docker compose logs -f --tail=20 turno-publisher turno-subscriber
