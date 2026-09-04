# Guía de Entregables Teóricos y de Gestión: Parte 2
**Taller de Ingeniería de Software | UTEC 2026**

Este documento recopila la información necesaria para los entregables documentales de la Parte 2:
1. **Metodología Ágil y Configuración de Jira (Escenarios, Historias y Épicas)**.
2. **Evaluación técnica de GitHub Actions con Maven**.
3. **Diagrama y Especificación del Modelo Entidad-Relación (MER)**.

---

## 1. Metodología Ágil y Configuración de Jira

### Decisión Metodológica: Scrum con Prácticas de Kanban (Scrumban)
Para el desarrollo de este proyecto se adopta un marco de trabajo ágil basado en **Scrum** complementado con el flujo visual y límites de trabajo en curso (WIP) de **Kanban**:

- **Razones que lo justifican**:
  1. **Iteraciones de 2 semanas (Sprints)**: El curso estructura las entregas en ciclos de 2 semanas con entregables claros y revisiones periódicas (Sprint Planning, Daily breve y Sprint Review/Retrospective).
  2. **Definición Clara del Alcance**: Permite priorizar Épicas e Historias en el Backlog antes de comprometerse al Sprint Goal de cada entrega.
  3. **Visualización y Control de Flujo (Board Kanban)**: Se utiliza el tablero para limitar cuellos de botella en la fase de pruebas y revisión de código.

### Configuración del Tablero en Jira
El tablero de Jira debe configurarse con las cuatro columnas obligatorias:
1. **Todo**: Historias y tareas priorizadas para la iteración actual.
2. **InProgress**: Ítems en desarrollo activo por un miembro del equipo.
3. **ToTest**: Ítems cuya funcionalidad fue desarrollada y están pendientes de verificación funcional, pruebas de integración o revisión entre pares (*Peer Review*).
4. **Done**: Ítems que cumplen la *Definition of Done* (código compilado con Maven, testeado, integrado en Docker y documentado).

---

## 2. Épicas e Historias de Usuario para Jira

### Épica 1: Gestión de la Plataforma y Establecimientos (`EPIC-1`)
*Alcance*: Administración y configuración de los datos base de los locales que ofrecen servicios.

#### Historia 1.1: Registro y administración de Establecimientos
- **Como**: Administrador de la plataforma.
- **Quiero**: Registrar y consultar los datos de un establecimiento (nombre, dirección, teléfono, correo, horarios de apertura y cierre).
- **Para**: Configurar el marco operativo donde se atenderá a los clientes.
- **Criterios de Aceptación (Gherkin)**:
  - **Escenario 1**: Creación exitosa de un establecimiento con horarios válidos.
    - *Given* que el administrador ingresa un horario de apertura anterior al horario de cierre.
    - *When* se envía la solicitud de alta del establecimiento.
    - *Then* el establecimiento queda registrado en la base de datos con identificador único.

---

### Épica 2: Gestión de Personal y Especialidades (`EPIC-2`)
*Alcance*: Administración del personal profesional que presta servicios en los establecimientos.

#### Historia 2.1: Alta y Control de Estado del Personal
- **Como**: Encargado del establecimiento.
- **Quiero**: Registrar al personal asignando su especialidad, costo de consulta y estado (Activo/Inactivo).
- **Para**: Disponer de los recursos humanos habilitados para la asignación de turnos.
- **Criterios de Aceptación (Gherkin)**:
  - **Escenario 1**: No permitir turnos a profesionales inactivos.
    - *Given* un profesional registrado con estado `INACTIVO`.
    - *When* un cliente intenta solicitar un turno para dicho profesional.
    - *Then* el sistema rechaza la solicitud indicando que el personal no está habilitado.

---

### Épica 3: Mensajería Asíncrona y Reserva de Turnos (`EPIC-3`)
*Alcance*: Comunicación desacoplada mediante broker MQTT para la solicitud y persistencia de reservas.

#### Historia 3.1: Publicación de Solicitudes de Turno vía MQTT
- **Como**: Módulo cliente / API de reservas.
- **Quiero**: Publicar un evento JSON estandarizado en el tópico `turnos/reservas` de Mosquitto.
- **Para**: Desacoplar la solicitud de reserva del proceso de almacenamiento.
- **Criterios de Aceptación (Gherkin)**:
  - **Escenario 1**: Publicación periódica periódica con formato compatible.
    - *Given* un generador de reservas simulado.
    - *When* transcurren 10 segundos.
    - *Then* se publica un mensaje JSON con los campos `status`, `fechaHora` y `turno` conteniendo solicitante, profesional, fecha y hora.

#### Historia 3.2: Consumo, Validación de Reglas de Negocio y Persistencia
- **Como**: Servicio de procesamiento de turnos (`turno-subscriber`).
- **Quiero**: Escuchar el tópico MQTT, verificar las reglas de negocio y guardar la reserva en MariaDB.
- **Para**: Asegurar que solo se confirmen reservas válidas sin solapamientos ni fuera de agenda.
- **Criterios de Aceptación (Gherkin)**:
  - **Escenario 1**: Prevención de turnos duplicados.
    - *Given* que el profesional ya tiene un turno asignado para una fecha y hora específica.
    - *When* llega una nueva solicitud para el mismo profesional en ese horario.
    - *Then* el sistema rechaza la reserva y no genera registros duplicados en MariaDB.
  - **Escenario 2**: Validación de horario dentro de la agenda del establecimiento.
    - *Given* un establecimiento con horario de 08:00 a 19:00.
    - *When* se solicita un turno para las 19:30 o antes de las 08:00.
    - *Then* el sistema rechaza la solicitud por estar fuera de horario de atención.

---

## 3. Evaluación de GitHub Actions con Maven

### Análisis de Viabilidad
De acuerdo a la consigna, se analizó la incorporación de **GitHub Actions** junto con **Maven**:

- **Decisión del equipo**: **POSITIVA** (Se recomienda y adopta la integración continua mediante GitHub Actions).
- **Justificación**:
  1. **Automatización del ciclo de vida**: Cada *push* o *pull request* ejecuta `mvn clean test` y `mvn package` asegurando que no se rompan las dependencias ni la compilación.
  2. **Detección temprana de incompatibilidades**: Garantiza que el código compile de manera uniforme en entornos limpios de Linux (Ubuntu Latest), evitando problemas locales de sistema operativo entre los integrantes del grupo (por ejemplo, diferencias entre Windows y Linux).
  3. **Generación automática de artefactos**: Permite compilar y empaquetar los JARs y construir las imágenes Docker de manera automática en el repositorio central.

#### Flujo sugerido (`.github/workflows/maven-ci.yml`):
```yaml
name: Java CI with Maven

on:
  push:
    branches: [ "main", "develop" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: maven
    - name: Build turno-publisher
      run: mvn -B package --file Parte2/turno-publisher/pom.xml
    - name: Build turno-subscriber
      run: mvn -B package --file Parte2/turno-subscriber/pom.xml
```

---

## 4. Diagrama del Modelo Entidad-Relación (MER)

### Entidades y Atributos:

1. **`ESTABLECIMIENTOS`**:
   - `id` (PK, INT, AUTO_INCREMENT)
   - `nombre_comercial` (VARCHAR 150)
   - `direccion` (VARCHAR 255)
   - `telefono` (VARCHAR 50)
   - `correo_electronico` (VARCHAR 100)
   - `horario_apertura` (TIME)
   - `horario_cierre` (TIME)

2. **`PERSONAL`**:
   - `id` (PK, INT, AUTO_INCREMENT)
   - `id_establecimiento` (FK hacia `ESTABLECIMIENTOS.id`)
   - `nombre` (VARCHAR 150)
   - `especialidad` (VARCHAR 100)
   - `costo_consulta` (DECIMAL 10,2)
   - `duracion_estandar_minutos` (INT, Default 30)
   - `estado` (ENUM: 'ACTIVO', 'INACTIVO')

3. **`RESERVAS_TURNOS`**:
   - `id` (PK, INT, AUTO_INCREMENT)
   - `id_personal` (FK hacia `PERSONAL.id`)
   - `email_solicitante` (VARCHAR 100)
   - `telefono_solicitante` (VARCHAR 50)
   - `fecha_turno` (DATE)
   - `hora_turno` (TIME)
   - `duracion_minutos` (INT, Default 30)
   - `fecha_registro` (DATETIME)
   - `estado` (VARCHAR 30, Default 'CONFIRMADO')
   - **Restricción de unicidad**: `UNIQUE KEY (id_personal, fecha_turno, hora_turno)`

### Relaciones:
- Un **Establecimiento** tiene de 1 a N integrantes de **Personal** (1 a N).
- Un integrante de **Personal** pertenece obligatoriamente a 1 **Establecimiento**.
- Un integrante de **Personal** puede atender de 0 a N **Reservas de Turnos** (1 a N).
- Una **Reserva de Turno** está asociada obligatoriamente a 1 integrante de **Personal**.
