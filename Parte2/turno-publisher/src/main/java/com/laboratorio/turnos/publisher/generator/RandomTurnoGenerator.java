package com.laboratorio.turnos.publisher.generator;

import com.laboratorio.turnos.publisher.model.TurnoDTO;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generador de datos aleatorios pero coherentes con el modelo de dominio
 * para simular clientes que solicitan turnos.
 */
public class RandomTurnoGenerator {

    private final Random random = new Random();
    private final AtomicInteger turnoSequence = new AtomicInteger(100);

    private static final List<String> NOMBRES_CLIENTES = List.of(
            "Agustina Rossi", "Bruno Silva", "Camila Pereira", "Diego Morales",
            "Elena Cabrera", "Facundo Méndez", "Gabriela Suárez", "Hernán Castro",
            "Inés Delgado", "Joaquín Navarro", "Lucía Benítez", "Martín Romero"
    );

    private static final List<String> DOMINIOS_EMAIL = List.of(
            "gmail.com", "outlook.com", "hotmail.com", "yahoo.com", "fing.edu.uy"
    );

    // IDs de personal disponibles en la base de datos:
    // 1, 2, 3, 8 son ACTIVO; 4 es INACTIVO (para probar la regla de negocio de rechazo)
    private static final List<Integer> IDS_PERSONAL = List.of(1, 2, 3, 8, 8, 1, 2, 4);

    // Horarios válidos de 30 minutos dentro de la jornada laboral
    private static final List<LocalTime> HORARIOS = List.of(
            LocalTime.of(8, 0),
            LocalTime.of(8, 30),
            LocalTime.of(9, 0),
            LocalTime.of(9, 30),
            LocalTime.of(10, 0),
            LocalTime.of(10, 30),
            LocalTime.of(11, 0),
            LocalTime.of(11, 30),
            LocalTime.of(14, 0),
            LocalTime.of(14, 30),
            LocalTime.of(15, 0),
            LocalTime.of(15, 30),
            LocalTime.of(16, 0),
            LocalTime.of(16, 30),
            LocalTime.of(17, 0),
            LocalTime.of(17, 30),
            LocalTime.of(18, 0)
    );

    /**
     * Genera un TurnoDTO con datos aleatorios simulando una reserva.
     */
    public TurnoDTO generateRandomTurno() {
        String nombre = NOMBRES_CLIENTES.get(random.nextInt(NOMBRES_CLIENTES.size()));
        String dominio = DOMINIOS_EMAIL.get(random.nextInt(DOMINIOS_EMAIL.size()));
        String email = nombre.toLowerCase().replace(" ", ".") + "@" + dominio;

        String telefono = "09" + (random.nextInt(9000000) + 1000000);
        int idPersonal = IDS_PERSONAL.get(random.nextInt(IDS_PERSONAL.size()));

        // Fecha entre 1 y 10 días a partir de hoy
        int diasEnElFuturo = random.nextInt(10) + 1;
        LocalDate fecha = LocalDate.now().plusDays(diasEnElFuturo);

        LocalTime hora = HORARIOS.get(random.nextInt(HORARIOS.size()));

        return new TurnoDTO(
                turnoSequence.getAndIncrement(),
                email,
                telefono,
                idPersonal,
                fecha,
                hora
        );
    }
}
