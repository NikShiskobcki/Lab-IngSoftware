package com.laboratorio.turnos.facturacion.service;

import com.laboratorio.turnos.facturacion.model.Cliente;
import com.laboratorio.turnos.facturacion.model.Factura;
import com.laboratorio.turnos.facturacion.model.ItemFactura;
import com.laboratorio.turnos.facturacion.model.Personal;
import com.laboratorio.turnos.facturacion.model.Reserva;
import com.laboratorio.turnos.facturacion.repository.ClienteRepository;
import com.laboratorio.turnos.facturacion.repository.FacturaRepository;
import com.laboratorio.turnos.facturacion.repository.ItemFacturaRepository;
import com.laboratorio.turnos.facturacion.repository.PersonalRepository;
import com.laboratorio.turnos.facturacion.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * US-53 - Transicion Atendido -> Facturado (TurnoFacturador), de forma aislada.
 * Repositorios mockeados: sin base de datos, sin broker MQTT.
 * Nota: la atomicidad real (rollback) la da @Transactional y se comprueba en integracion;
 * aqui se verifica que el estado solo cambia cuando todo lo anterior se completo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("US-53 | Transicion Atendido -> Facturado (TurnoFacturador)")
class TurnoFacturadorTest {

    private static final int ID_TURNO = 5;
    private static final int ID_PERSONAL = 3;
    private static final LocalDate FECHA = LocalDate.of(2026, 3, 15);

    @Mock private ReservaRepository reservas;
    @Mock private PersonalRepository personal;
    @Mock private ClienteRepository clientes;
    @Mock private FacturaRepository facturas;
    @Mock private ItemFacturaRepository items;

    @Mock private Reserva turno;
    @Mock private Personal profesional;

    private TurnoFacturador facturador;

    @BeforeEach
    void setUp() {
        facturador = new TurnoFacturador(reservas, personal, clientes, facturas, items);

        when(turno.getId()).thenReturn(ID_TURNO);
        when(turno.getIdPersonal()).thenReturn(ID_PERSONAL);
        when(turno.getEmailSolicitante()).thenReturn("  Cliente@Correo.COM ");
        when(turno.getTelefonoSolicitante()).thenReturn("099123456");
        when(turno.getFechaTurno()).thenReturn(FECHA);
        when(turno.getHoraTurno()).thenReturn(LocalTime.of(10, 0));

        when(profesional.getNombre()).thenReturn("Dra. Ana Perez");
        when(profesional.getEspecialidad()).thenReturn("Pediatria");
        when(profesional.getCostoConsulta()).thenReturn(new BigDecimal("1500"));

        when(reservas.bloquearPorIdYEstado(ID_TURNO, "Atendido")).thenReturn(Optional.of(turno));
        when(personal.findById(ID_PERSONAL)).thenReturn(Optional.of(profesional));

        // escenario por defecto: cliente y factura nuevos
        when(clientes.findByEmail(any())).thenReturn(Optional.empty());
        when(clientes.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        when(facturas.buscarYBloquear(any(), any(), any())).thenReturn(Optional.empty());
        when(facturas.save(any(Factura.class))).thenAnswer(i -> i.getArgument(0));
        when(items.save(any(ItemFactura.class))).thenAnswer(i -> i.getArgument(0));
        when(items.sumarMontos(any())).thenReturn(new BigDecimal("1500"));
    }

    // ---------- camino feliz ----------

    @Test
    @DisplayName("Atendido -> Facturado: crea cliente, factura e item y marca el turno Facturado")
    void atendidoAFacturado() {
        boolean resultado = facturador.facturar(ID_TURNO);

        assertTrue(resultado);
        verify(turno).setEstado("Facturado");
        verify(clientes).save(any(Cliente.class));
        verify(facturas).save(any(Factura.class));
        verify(items).save(any(ItemFactura.class));
    }

    @Test
    @DisplayName("El item lleva el costo de consulta del profesional y una descripcion del turno")
    void itemConMontoYDescripcion() {
        facturador.facturar(ID_TURNO);

        ArgumentCaptor<ItemFactura> captor = ArgumentCaptor.forClass(ItemFactura.class);
        verify(items).save(captor.capture());
        ItemFactura item = captor.getValue();

        assertEquals(new BigDecimal("1500"), item.getMonto());
        assertEquals("Turno #5 - Dra. Ana Perez (Pediatria) - 2026-03-15 10:00", item.getDescripcion());
        assertSame(turno, item.getTurno());
    }

    @Test
    @DisplayName("El total de la factura se recalcula como la suma de sus items")
    void totalEsLaSumaDeItems() {
        Cliente cliente = new Cliente("cliente@correo.com", "099", java.time.LocalDateTime.now());
        Factura existente = new Factura(cliente, 2026, 3, java.time.LocalDateTime.now());
        when(clientes.findByEmail("cliente@correo.com")).thenReturn(Optional.of(cliente));
        when(facturas.buscarYBloquear(any(), eq(2026), eq(3))).thenReturn(Optional.of(existente));
        when(items.sumarMontos(any())).thenReturn(new BigDecimal("3000"));

        facturador.facturar(ID_TURNO);

        assertEquals(new BigDecimal("3000"), existente.getTotal());
    }

    @Test
    @DisplayName("El estado cambia a Facturado recien despues de agregar el item a la factura")
    void estadoCambiaDespuesDeAgregarElItem() {
        facturador.facturar(ID_TURNO);

        InOrder orden = inOrder(items, turno);
        orden.verify(items).save(any(ItemFactura.class));
        orden.verify(turno).setEstado("Facturado");
    }

    // ---------- cliente por email ----------

    @Test
    @DisplayName("El cliente se identifica por email sin mayusculas ni espacios")
    void emailNormalizado() {
        facturador.facturar(ID_TURNO);

        verify(clientes).findByEmail("cliente@correo.com");
        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clientes).save(captor.capture());
        assertEquals("cliente@correo.com", captor.getValue().getEmail());
        assertEquals("099123456", captor.getValue().getTelefono());
    }

    @Test
    @DisplayName("Si el cliente ya existe se reutiliza (no se crea otro)")
    void clienteExistenteSeReutiliza() {
        Cliente existente = new Cliente("cliente@correo.com", "099", java.time.LocalDateTime.now());
        when(clientes.findByEmail("cliente@correo.com")).thenReturn(Optional.of(existente));

        facturador.facturar(ID_TURNO);

        verify(clientes, never()).save(any(Cliente.class));
        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        verify(facturas).save(captor.capture());
        assertSame(existente, captor.getValue().getCliente());
    }

    // ---------- factura mensual ----------

    @Test
    @DisplayName("La factura nueva es del anio y mes del turno (no del momento de ejecucion), ABIERTA")
    void facturaDelPeriodoDelTurno() {
        facturador.facturar(ID_TURNO);

        verify(facturas).buscarYBloquear(any(), eq(2026), eq(3));
        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        verify(facturas).save(captor.capture());
        assertEquals(2026, captor.getValue().getAnio());
        assertEquals(3, captor.getValue().getMes());
        assertEquals("ABIERTA", captor.getValue().getEstado());
    }

    @Test
    @DisplayName("Turno de diciembre de otro anio -> factura 2025/12")
    void periodoCambiaDeAnio() {
        when(turno.getFechaTurno()).thenReturn(LocalDate.of(2025, 12, 31));

        facturador.facturar(ID_TURNO);

        verify(facturas).buscarYBloquear(any(), eq(2025), eq(12));
    }

    @Test
    @DisplayName("Si ya existe la factura del cliente para ese mes se reutiliza (no se crea otra)")
    void facturaExistenteSeReutiliza() {
        Cliente cliente = new Cliente("cliente@correo.com", "099", java.time.LocalDateTime.now());
        Factura existente = new Factura(cliente, 2026, 3, java.time.LocalDateTime.now());
        when(clientes.findByEmail("cliente@correo.com")).thenReturn(Optional.of(cliente));
        when(facturas.buscarYBloquear(any(), eq(2026), eq(3))).thenReturn(Optional.of(existente));

        facturador.facturar(ID_TURNO);

        verify(facturas, never()).save(any(Factura.class));
        ArgumentCaptor<ItemFactura> captor = ArgumentCaptor.forClass(ItemFactura.class);
        verify(items).save(captor.capture());
        assertSame(existente, captor.getValue().getFactura());
    }

    // ---------- casos en los que NO se factura ----------

    @Test
    @DisplayName("Si el turno ya no esta en estado Atendido no se factura (devuelve false) y no se toca nada")
    void turnoYaNoEstaAtendido() {
        when(reservas.bloquearPorIdYEstado(ID_TURNO, "Atendido")).thenReturn(Optional.empty());

        boolean resultado = facturador.facturar(ID_TURNO);

        assertFalse(resultado);
        verifyNoInteractions(personal, clientes, facturas, items);
        verify(turno, never()).setEstado(any());
    }

    @Test
    @DisplayName("Si el profesional no existe falla con IllegalStateException y el turno NO pasa a Facturado")
    void profesionalInexistente() {
        when(personal.findById(ID_PERSONAL)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> facturador.facturar(ID_TURNO));

        verify(turno, never()).setEstado(any());
        verify(items, never()).save(any(ItemFactura.class));
    }

    @Test
    @DisplayName("Si falla el guardado del item, el turno NO pasa a Facturado (se reintenta en el proximo ciclo)")
    void falloAlGuardarElItem() {
        when(items.save(any(ItemFactura.class))).thenThrow(new RuntimeException("violacion de unicidad"));

        assertThrows(RuntimeException.class, () -> facturador.facturar(ID_TURNO));

        verify(turno, never()).setEstado(any());
    }
}
