#!/bin/bash
# script de pruebas para el modulo de reservas

echo "=== 1. crear reserva exitosa (post) ==="
curl -i -X POST http://localhost:8080/reservas \
  -H "Content-Type: application/json" \
  -d '{
    "idPersonal": 1,
    "emailCliente": "cliente@correo.com",
    "telefonoCliente": "099123456",
    "fecha": "2026-10-25",
    "hora": "10:00"
  }'
echo -e "\n"

sleep 1

echo "=== 2. consultar todas las reservas (get) ==="
curl -i http://localhost:8080/reservas
echo -e "\n"

echo "=== 3. consultar reserva individual por id (get) ==="
curl -i http://localhost:8080/reservas/1
echo -e "\n"

echo "=== 4. filtrar reservas por idPersonal (get) ==="
curl -i "http://localhost:8080/reservas?idPersonal=1"
echo -e "\n"

echo "=== 5. error 400 por datos invalidos (post) ==="
curl -i -X POST http://localhost:8080/reservas \
  -H "Content-Type: application/json" \
  -d '{
    "idPersonal": 1,
    "emailCliente": "correo-invalido",
    "telefonoCliente": "",
    "fecha": "2026-10-25",
    "hora": "10:00"
  }'
echo -e "\n"

echo "=== 6. error en subscriber por personal inactivo (post) ==="
curl -i -X POST http://localhost:8080/reservas \
  -H "Content-Type: application/json" \
  -d '{
    "idPersonal": 4,
    "emailCliente": "cliente@correo.com",
    "telefonoCliente": "099123456",
    "fecha": "2026-10-25",
    "hora": "10:00"
  }'
echo -e "\n"

echo "=== 7. error en subscriber por horario fuera de atencion (post) ==="
curl -i -X POST http://localhost:8080/reservas \
  -H "Content-Type: application/json" \
  -d '{
    "idPersonal": 1,
    "emailCliente": "cliente@correo.com",
    "telefonoCliente": "099123456",
    "fecha": "2026-10-25",
    "hora": "03:00"
  }'
echo -e "\n"

echo "=== 8. consultar id inexistente devuelve 404 (get) ==="
curl -i http://localhost:8080/reservas/9999
echo -e "\n"

echo "=== 9. eliminar reserva existente devuelve 204 (delete) ==="
curl -i -X DELETE http://localhost:8080/reservas/1
echo -e "\n"

echo "=== 10. verificar que la reserva eliminada ya no existe (get) ==="
curl -i http://localhost:8080/reservas/1
echo -e "\n"

echo "=== 11. eliminar reserva inexistente devuelve 404 (delete) ==="
curl -i -X DELETE http://localhost:8080/reservas/9999
echo -e "\n"
