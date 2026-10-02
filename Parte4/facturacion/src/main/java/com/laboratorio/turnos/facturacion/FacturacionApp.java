package com.laboratorio.turnos.facturacion;

import com.laboratorio.turnos.facturacion.db.DatabaseManager;
import com.laboratorio.turnos.facturacion.repository.FacturacionRepository;
import com.laboratorio.turnos.facturacion.service.FacturacionScheduler;
import com.laboratorio.turnos.facturacion.service.FacturacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

// servicio asincrono de facturacion de turnos atendidos (us-40, us-41, us-42)
public class FacturacionApp {

    private static final Logger logger = LoggerFactory.getLogger(FacturacionApp.class);

    private static final long INTERVALO_POR_DEFECTO_MS = 5 * 60 * 1000L;

    public static void main(String[] args) throws InterruptedException {
        String dbUrl = getEnv("DB_URL", "jdbc:mariadb://mariadb:3306/reservas");
        String dbUser = getEnv("DB_USER", "reservas_app");
        String dbPassword = getEnv("DB_PASSWORD", "admin");
        long intervaloMs = getEnvLong("FACTURACION_INTERVALO_MS", INTERVALO_POR_DEFECTO_MS);
        long delayInicialMs = getEnvLong("FACTURACION_DELAY_INICIAL_MS", 15_000L);

        logger.info("=== servicio de facturacion === db={}, intervalo={} ms", dbUrl, intervaloMs);

        DatabaseManager databaseManager = new DatabaseManager(dbUrl, dbUser, dbPassword);
        databaseManager.waitAndInitialize(60, 3000);

        FacturacionService service = new FacturacionService(databaseManager, new FacturacionRepository());
        FacturacionScheduler scheduler = new FacturacionScheduler(service, intervaloMs, delayInicialMs);

        CountDownLatch detener = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("deteniendo servicio de facturacion...");
            scheduler.close();
            detener.countDown();
        }));

        scheduler.start();
        detener.await();
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private static long getEnvLong(String key, long defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // cae al valor por defecto
        }
        logger.warn("valor invalido para {}='{}', se usa {}", key, value, defaultValue);
        return defaultValue;
    }
}
