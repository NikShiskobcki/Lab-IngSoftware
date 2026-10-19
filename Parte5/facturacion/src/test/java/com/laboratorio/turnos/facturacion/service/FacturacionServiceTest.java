package com.laboratorio.turnos.facturacion.service;

import com.laboratorio.turnos.facturacion.model.ResumenCiclo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * US-53 - Ciclo de facturacion (cada 5 minutos): FacturacionService y FacturacionScheduler.
 * El facturador de un turno es un mock; la cola de eventos es la real (en memoria).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-53 | Ciclo del facturador (cada 5 minutos)")
class FacturacionServiceTest {

    @Mock
    private TurnoFacturador turnoFacturador;
    @Mock
    private FacturacionService serviceMock;

    private ColaFacturacion cola;
    private FacturacionService service;

    @BeforeEach
    void setUp() {
        cola = new ColaFacturacion();
        service = new FacturacionService(cola, turnoFacturador);
    }

    @Test
    @DisplayName("Sin eventos pendientes el ciclo no factura nada")
    void cicloVacio() {
        ResumenCiclo r = service.ejecutarCiclo();

        assertEquals(new ResumenCiclo(0, 0, 0, 0), r);
        verifyNoInteractions(turnoFacturador);
    }

    @Test
    @DisplayName("Cada turno Atendido recibido se factura una vez (Atendido -> Facturado)")
    void facturaCadaTurnoRecibido() {
        cola.agregar(1);
        cola.agregar(2);
        when(turnoFacturador.facturar(1)).thenReturn(true);
        when(turnoFacturador.facturar(2)).thenReturn(true);

        ResumenCiclo r = service.ejecutarCiclo();

        assertEquals(new ResumenCiclo(2, 2, 0, 0), r);
        verify(turnoFacturador).facturar(1);
        verify(turnoFacturador).facturar(2);
    }

    @Test
    @DisplayName("Los turnos que ya no estaban Atendido se cuentan como omitidos")
    void turnosOmitidos() {
        cola.agregar(1);
        when(turnoFacturador.facturar(1)).thenReturn(false);

        ResumenCiclo r = service.ejecutarCiclo();

        assertEquals(new ResumenCiclo(1, 0, 1, 0), r);
    }

    @Test
    @DisplayName("Un error en un turno no frena a los demas y ese turno se reencola para el proximo ciclo")
    void errorSeReencolaYContinua() {
        cola.agregar(1);
        cola.agregar(2);
        cola.agregar(3);
        when(turnoFacturador.facturar(1)).thenReturn(true);
        when(turnoFacturador.facturar(2)).thenThrow(new IllegalStateException("el personal no existe"));
        when(turnoFacturador.facturar(3)).thenReturn(false);

        ResumenCiclo r = service.ejecutarCiclo();

        assertEquals(new ResumenCiclo(3, 1, 1, 1), r);
        assertEquals(List.of(2), cola.retirarPendientes());
    }

    @Test
    @DisplayName("Tras un ciclo exitoso la cola queda vacia (no se factura dos veces)")
    void colaVaciaTrasElCiclo() {
        cola.agregar(1);
        when(turnoFacturador.facturar(1)).thenReturn(true);

        service.ejecutarCiclo();

        assertTrue(cola.retirarPendientes().isEmpty());
        service.ejecutarCiclo();
        verify(turnoFacturador, times(1)).facturar(1);
    }

    // ---------- scheduler ----------

    @Test
    @DisplayName("El scheduler dispara el ciclo de facturacion")
    void schedulerDisparaElCiclo() {
        new FacturacionScheduler(serviceMock).ejecutar();

        verify(serviceMock, times(1)).ejecutarCiclo();
    }

    @Test
    @DisplayName("Si el ciclo lanza una excepcion, el scheduler la absorbe para no detener las siguientes ejecuciones")
    void schedulerAbsorbeErrores() {
        when(serviceMock.ejecutarCiclo()).thenThrow(new RuntimeException("falla inesperada"));

        assertDoesNotThrow(() -> new FacturacionScheduler(serviceMock).ejecutar());
    }

    // ---------- frecuencia: cada 5 minutos ----------

    @Test
    @DisplayName("El scheduler usa la propiedad facturacion.intervalo-ms")
    void schedulerUsaLaPropiedadDeIntervalo() throws Exception {
        Method m = FacturacionScheduler.class.getMethod("ejecutar");
        Scheduled s = m.getAnnotation(Scheduled.class);

        assertNotNull(s, "ejecutar debe estar anotado con @Scheduled");
        assertTrue(s.fixedRateString().contains("facturacion.intervalo-ms"));
    }

    @Test
    @DisplayName("application.yml fija el intervalo por defecto en 300000 ms (5 minutos)")
    void intervaloPorDefectoCincoMinutos() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertNotNull(in, "no se encontro application.yml");
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("${FACTURACION_INTERVALO_MS:300000}"));
        }
    }
}
