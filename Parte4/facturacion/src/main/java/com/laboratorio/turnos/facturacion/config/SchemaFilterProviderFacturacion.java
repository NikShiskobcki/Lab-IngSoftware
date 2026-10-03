package com.laboratorio.turnos.facturacion.config;

import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaFilter;
import org.hibernate.tool.schema.spi.SchemaFilterProvider;

import java.util.Set;

// personal y reservas_turnos pertenecen a otros servicios (las crea turno-subscriber).
// aca solo estan mapeadas para leerlas/actualizarlas: hibernate no debe crearlas ni modificarlas.
public class SchemaFilterProviderFacturacion implements SchemaFilterProvider {

    private static final Set<String> TABLAS_AJENAS = Set.of("personal", "reservas_turnos");

    private static final SchemaFilter FILTRO = new SchemaFilter() {
        @Override
        public boolean includeNamespace(Namespace namespace) {
            return true;
        }

        @Override
        public boolean includeTable(Table table) {
            return !TABLAS_AJENAS.contains(table.getName().toLowerCase());
        }

        @Override
        public boolean includeSequence(Sequence sequence) {
            return true;
        }
    };

    @Override
    public SchemaFilter getCreateFilter() {
        return FILTRO;
    }

    @Override
    public SchemaFilter getDropFilter() {
        return FILTRO;
    }

    @Override
    public SchemaFilter getMigrateFilter() {
        return FILTRO;
    }

    @Override
    public SchemaFilter getValidateFilter() {
        return FILTRO;
    }
}
