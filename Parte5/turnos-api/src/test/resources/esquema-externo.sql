CREATE TABLE IF NOT EXISTS establecimientos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre_comercial VARCHAR(150) NOT NULL,
    direccion VARCHAR(255) NOT NULL,
    telefono VARCHAR(50) NOT NULL,
    correo_electronico VARCHAR(100) NOT NULL,
    horario_apertura TIME NOT NULL,
    horario_cierre TIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS personal (
    id INT AUTO_INCREMENT PRIMARY KEY,
    id_establecimiento INT NOT NULL,
   nombre VARCHAR(150) NOT NULL,
    especialidad VARCHAR(100) NOT NULL,
    costo_consulta DECIMAL(10,2) NOT NULL,
    duracion_estandar_minutos INT NOT NULL DEFAULT 30,
    estado ENUM('ACTIVO', 'INACTIVO') NOT NULL DEFAULT 'ACTIVO',
    INDEX idx_personal_est (id_establecimiento),
    CONSTRAINT fk_personal_establecimiento
    FOREIGN KEY (id_establecimiento) REFERENCES establecimientos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reservas_turnos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    id_personal INT NOT NULL,
    id_establecimiento INT NULL,
    email_solicitante VARCHAR(100) NOT NULL,
    telefono_solicitante VARCHAR(50) NOT NULL,
    fecha_turno DATE NOT NULL,
    hora_turno TIME NOT NULL,
    duracion_minutos INT NOT NULL DEFAULT 30,
    fecha_registro DATETIME NOT NULL,
    estado VARCHAR(50) NOT NULL DEFAULT 'SOLICITADO',
    INDEX idx_reserva_personal (id_personal),
    CONSTRAINT fk_reserva_personal
    FOREIGN KEY (id_personal) REFERENCES personal(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;