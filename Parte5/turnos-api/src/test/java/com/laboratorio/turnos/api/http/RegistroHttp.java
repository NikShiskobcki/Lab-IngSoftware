package com.laboratorio.turnos.api.http;

import org.springframework.test.web.servlet.ResultHandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imprime una linea por cada request ejecutado por MockMvc:
 * HTTP_LOG|id caso|metodo|uri|codigo esperado|codigo recibido
 * El id y el codigo esperado se toman del @DisplayName ("CP-HTTP-01 | ... -> 201 ...").
 * Lo consume tests-http.sh para mostrar la tabla de codigos.
 */
final class RegistroHttp {

    private static final Pattern ESPERADO = Pattern.compile("-> (\\d{3})");

    private RegistroHttp() {}

    static ResultHandler registrar(String displayName) {
        String id = displayName.split(" \\| ")[0].trim();
        Matcher m = ESPERADO.matcher(displayName);
        String esperado = m.find() ? m.group(1) : "?";
        return result -> System.out.println("HTTP_LOG|" + id + "|"
                + result.getRequest().getMethod() + "|"
                + result.getRequest().getRequestURI() + "|"
                + esperado + "|"
                + result.getResponse().getStatus());
    }
}
