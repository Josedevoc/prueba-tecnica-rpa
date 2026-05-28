# Generador de Archivo Plano - RPA

Automatizacion en Java que lee un archivo Excel (`DetalleTecnico.xlsx`) con la estructura de 294 campos de creditos bancarios y genera un archivo plano `.txt` de ancho fijo (2094 chars/registro), aplicando todas las reglas de negocio, tipos de campo, longitudes, listas de referencia y 22 validaciones cruzadas definidas en el Excel.

## Requisitos

- Java 17+ (probado con JDK 25 Temurin)
- Maven embebido en IntelliJ IDEA (ver seccion Ejecucion) o Maven 3.8+ en PATH
- Archivo `DetalleTecnico.xlsx` en el directorio raiz del proyecto

## Ejecucion

> **Nota:** Maven no esta en PATH del sistema. Usar la ruta completa del Maven embebido en IntelliJ o ejecutar directamente desde el IDE.

```bash
# Compilar
& "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.3.5\plugins\maven\lib\maven3\bin\mvn.cmd" compile

# Ejecutar con el fat JAR (genera salida.txt con 100 registros por defecto)
java -jar target/prueba_tecnica-1.0-SNAPSHOT-jar-with-dependencies.jar

# Ejecutar con parametros personalizados
java -jar target/prueba_tecnica-1.0-SNAPSHOT-jar-with-dependencies.jar DetalleTecnico.xlsx 200 salida.txt
#                                                                        [excel]              [n]  [output]

# Rebuild del fat JAR
& "...mvn.cmd" package -DskipTests
```

O desde IntelliJ: clic derecho en `Main.java` → **Run 'Main'**.

### Parametros

| Parametro | Defecto              | Descripcion                                      |
|-----------|----------------------|--------------------------------------------------|
| `args[0]` | `DetalleTecnico.xlsx`| Ruta del archivo Excel con la estructura         |
| `args[1]` | `100`                | Cantidad de registros a generar                  |
| `args[2]` | `salida.txt`         | Nombre del archivo de salida                     |

### Archivos generados al ejecutar

| Archivo                  | Descripcion                                                  |
|--------------------------|--------------------------------------------------------------|
| `salida.txt`             | Archivo plano de migracion (2094 chars por registro)         |
| `execution.log`          | Log completo con timestamps de cada paso de ejecucion        |
| `execution-report.html`  | Reporte HTML de evidencia con estadisticas y campos clave    |

## Tests

```bash
# Ejecutar tests
& "...mvn.cmd" verify

# Solo tests sin reporte
& "...mvn.cmd" test
```

Se ejecutan **16 escenarios BDD** con Cucumber (keywords en ingles, contenido en espanol). Al finalizar, `mvn verify` genera automaticamente el reporte HTML profesional en:

```
target/cucumber-html-reports/overview-features.html
```

### Escenarios de prueba

| # | Escenario | CP | Que valida |
|---|-----------|-----|------------|
| 1 | Verificar carga del esquema | CP-001 | 294 campos cargados + 15 listas de referencia presentes |
| 2 | Generar archivo con 100 registros validos | CP-002 | Longitud fija 2094 chars + campos obligatorios con datos |
| 3 | Validar reglas cruzadas de tipo de tasa | CP-007 | TIPOTASA=F → TASDINAM=X y TIPCUOTA=F; TIPOTASA=D → TIPCUOTA=V |
| 4 | Validar saldos calculados | CP-008 | VALSLDACT = CAPVIGACT + CAPVENCI |
| 5 | Validar formato de fechas | CP-005 | Todos los campos FEC* tienen formato yyyyMMdd valido |
| 6 | Validar mapeo CODLINCRE a CLASECAR | CP-011 | CLASECAR-IN es C, O, H o M segun la hoja Lineas |
| 7 | Validar reglas de credito rotativo | CP-012 | CODLINCRE=014 → VALCAPPAG/NUMCUOPAG/NUMCUOAPA=0, FECFINPACT=99991231 |
| 8 | Validar TIPIDECLI-IN contra lista de referencia | CP-013 | Solo valores validos de la lista TIP ID CLIENTE (C,E,I,J,L,P,R,T) |
| 9 | Validar CIUCLI-IN con codigos DANE | CP-014 | Codigo DANE valido de la hoja CodigoDANE |
| 10 | Validar coherencia VALSLDACT <= VALPRESTA | CP-015 | Saldo actual nunca supera el valor del prestamo |
| 11 | Validar FACTLMOR siempre 365 | CP-016 | FACTLMOR-IN = 365 en todos los registros |
| 12 | Validar calculo de capital pagado | CP-009 | VALCAPPAG = max(0, VALPRESTA - VALSLDACT) |
| 13 | Validar regla NITEMPCON condicional | CP-010 | CODLINCRE != 131 → NITEMPCON = ceros |
| 14 | Generar archivo parametrizado (5 registros) | CP-017 | Cantidad exacta de lineas en el archivo |
| 15 | Generar archivo parametrizado (50 registros) | CP-018 | Cantidad exacta de lineas en el archivo |
| 16 | Generar archivo parametrizado (200 registros) | CP-019 | Cantidad exacta de lineas en el archivo |

### Reportes de tests generados

| Archivo                                          | Formato   | Descripcion                              |
|--------------------------------------------------|-----------|------------------------------------------|
| `target/cucumber-html-reports/overview-features.html` | HTML | Dashboard con graficas y tabla de escenarios |
| `target/cucumber-html-reports/report-feature_*.html`  | HTML | Detalle paso a paso de cada escenario    |
| `target/cucumber-report.html`                    | HTML      | Reporte basico de Cucumber               |
| `target/cucumber-report.json`                    | JSON      | Resultados en formato estandar           |
| `target/cucumber-report-junit.xml`               | XML       | Resultados compatibles con CI/CD         |
| `target/surefire-reports/*.txt`                  | TXT       | Resumen: Tests run: 16, Failures: 0      |

## Arquitectura

```
src/main/java/com/pruebatecnica/rpa/
├── Main.java                      # Punto de entrada + logging + genera execution-report.html
├── model/
│   └── FieldDefinition.java       # Modelo de campo (nombre, tipo, longitud, regla, etc.)
├── loader/
│   └── SchemaLoader.java          # Lee el Excel y carga 294 campos + 15 listas de referencia
├── engine/
│   └── MigrationEngine.java       # Orquesta: carga -> genera -> post-procesa -> escribe
├── generator/
│   ├── FieldGenerator.java        # Genera datos validos por campo segun tipo y reglas
│   └── PostProcessor.java         # Aplica 22 reglas cruzadas entre campos del registro
├── writer/
│   ├── RecordBuilder.java         # Ensambla el registro de ancho fijo (padding)
│   └── FlatFileWriter.java        # Escribe el archivo .txt en UTF-8
└── report/
    └── ExecutionReport.java       # Genera execution-report.html con evidencia de ejecucion

src/main/resources/
└── log4j2.xml                     # Configuracion de logging (consola + archivo execution.log)

src/test/
├── java/com/pruebatecnica/rpa/
│   ├── CucumberRunnerTest.java    # Runner de Cucumber + JUnit Platform Suite
│   └── steps/
│       └── MigracionSteps.java    # Step definitions en espanol (Given/When/Then)
└── resources/features/
    └── migracion.feature          # 16 escenarios Gherkin (keywords ingles, contenido espanol)
```

## Como funciona

### 1. Carga del esquema (`SchemaLoader`)

Lee la hoja `DetalleTecnico` del Excel y extrae por cada campo:

| Columna Excel     | Propiedad       | Uso                                            |
|-------------------|-----------------|------------------------------------------------|
| No. Campo         | `numero`        | Orden del campo                                |
| Nombre del campo  | `nombre`        | Identificador unico (ej: `TIPIDECLI-IN`)       |
| Obligatoriedad    | `obligatorio`   | Si el campo debe tener datos                   |
| Regla de negocio  | `regla`         | Texto con reglas de generacion                 |
| Nombre lista ref  | `listaRef`      | Nombre de la lista de valores validos          |
| TIPO              | `tipo`          | `NUMERICO` o `ALFANUMERICO`                    |
| Datos aceptados   | `datosAceptados`| Valores permitidos                             |
| ENTEROS           | `enteros`       | Digitos enteros (campos numericos)             |
| DECIMALES         | `decimales`     | Digitos decimales                              |
| LONGITUD          | `longitud`      | Longitud total del campo en caracteres         |

### 2. Listas de referencia (15 listas)

| Lista              | Origen                              | Valores |
|--------------------|-------------------------------------|---------|
| TIP ID CLIENTE     | Hardcoded                           | C,E,I,J,L,P,R,T |
| LINEAS             | Hoja "Lineas de credito"            | 77 codigos |
| DANE               | Hoja "CodigoDANE"                   | 1123 codigos |
| Cod. Aseguradoras  | Hoja "CodAseguradoras"              | 47 codigos |
| CONVENIOS          | Hoja "Convenios"                    | 5 codigos |
| BASES              | Hoja "BaseLiquidacion"              | 4 codigos |
| COD TIP TASA       | Hardcoded                           | D,E,X,I,B,R |
| FORM PAG K+I       | Hardcoded                           | 01-12 |
| TEMPORALIDAD       | Hardcoded                           | A,B,C,D,E |
| SENAL ADMIN        | Hardcoded                           | 0,1,2,8 |
| COD RAN DIAS MORA  | Hardcoded                           | BB,01-07 |
| Asignacion_Comercial | Hardcoded                         | 0001-0005 |
| PROC DE LEY        | Hardcoded                           | 0,1,2,3 |
| Productos_FNG      | Hardcoded                           | 01,02,03 |
| TIP FRECH          | Hardcoded                           | 0,1,2 |

### 3. Generacion de datos (`FieldGenerator`)

Cadena de prioridad para generar el valor de cada campo:

1. **Signo** — Campos `SIG*` de longitud 1 → `+` o `-`
2. **Fecha** — Campos `FEC*` de longitud 8 → formato `yyyyMMdd` coherente segun el nombre
3. **Constante fija** — Detecta `"Siempre enviar X"` / `"Default X"` en las reglas
4. **Por nombre** — Logica especifica: CIUCLI (DANE), TELCLI (telefono colombiano), NUMPRESTA (CR+digitos), NOMBRECLI (nombres), DIRCLI (direcciones)
5. **Lista de referencia** — Toma valor aleatorio de la lista correspondiente
6. **Default ceros/espacios** — Si la regla dice `"default cero"` o `"default vacio"`
7. **Datos aceptados** — Extrae codigos de la columna `datosAceptados` o `regla`
8. **Numerico/Alfanumerico** — Genera valor aleatorio del tipo correcto

### 4. Reglas cruzadas (`PostProcessor`) — 22 reglas

| Regla                       | Que hace                                                           |
|-----------------------------|--------------------------------------------------------------------|
| `applyClaseCarteraRules`    | CODLINCRE → CLASECAR (C/O/H/M) segun tabla de lineas              |
| `applyNitEmpConRules`       | Si CODLINCRE != 131, NITEMPCON = ceros                             |
| `applyFinancialCoherence`   | CAPVIGACT + CAPVENCI no puede superar VALPRESTA                    |
| `applyTasaRules`            | TIPOTASA=F → TASDINAM=X, TIPCUOTA=F. TIPOTASA=D → TIPCUOTA=V     |
| `applyCuotaRules`           | TIPCUOTA=F → FORMPAINT = FORMPACAP                                 |
| `applyConceptoCalendarioRules` | TIPCUOTA=F → CONCAL1=F, CONCAL2=N. TIPCUOTA=V → CONCAL1=P, CONCAL2=I |
| `applyFormaPagoRules`       | Coherencia entre FORMPACAP y FORMPAINT                             |
| `applySaldoRules`           | VALSLDACT = CAPVIGACT + CAPVENCI                                   |
| `applyCapitalPagadoRules`   | VALCAPPAG = VALPRESTA - VALSLDACT (minimo 0)                       |
| `applyPlazoRules`           | PLAZO = (FECFINPACT - FECDESEMB) / 30 meses                       |
| `applyRotativoRules`        | CODLINCRE=14 → VALCAPPAG/NUMCUOPAG/NUMCUOAPA=0, FECFINPACT=99991231 |
| `applyReestructuradoRules`  | ESTADOPRE=05 → STRCALRES=A. Si no → RESTRCTRD=0                   |
| `applySenPreResRules`       | ESTADOPRE=05 → SENPRERES=1, si no → 0                             |
| `applyDiasMoraRules`        | NUMDIAMOR → DIASVEN (rangos BB, 01-07)                             |
| `applyEstadoRules`          | ESTADOPRE=06 (castigado) → SENACTADM=4                             |
| `applyEstCausRules`         | Segun dias mora y clase cartera → ESTCAUS = S u O                  |
| `applyProcJuridRules`       | SENACTADM=0 (normal) → PROCJURID=0                                 |
| `applyFngRules`             | ESFNG != 1 → fuerza 0 en 10 campos FNG                            |
| `applyFrechRules`           | CODLINCRE != 119/117 → SENENTTER=0 y campos FRECH en ceros         |
| `applyNumTitRules`          | NUM-TIT=0 y 43 campos de titulares adicionales en default           |
| `applyFactorMora`           | FACTLMOR siempre = 365                                             |
| `applySignos`               | Todos los campos SIG* de longitud 1 → "+" (salvo regla negativo)   |

### 5. Formato del archivo de salida

- **Ancho fijo**: cada registro tiene exactamente **2094 caracteres**
- **294 campos** por registro, sin separadores
- **Campos ALFANUMERICO**: alineados a la izquierda, rellenos con espacios a la derecha
- **Campos NUMERICO**: alineados a la derecha, rellenos con ceros a la izquierda
- **Decimales**: enteros + decimales concatenados sin separador (ej: `04200000` = 42.00000)
- **Codificacion**: UTF-8

## Estructura del Excel

El archivo `DetalleTecnico.xlsx` contiene 7 hojas:

| Hoja               | Contenido                                   |
|--------------------|---------------------------------------------|
| PruebaTecnica      | Instrucciones generales                     |
| DetalleTecnico     | 294 definiciones de campo (19 columnas)     |
| Lineas de credito  | 77 codigos de lineas con clase de cartera   |
| CodigoDANE         | 1123 codigos de municipios colombianos      |
| Convenios          | 5 convenios de libranza                     |
| BaseLiquidacion    | 4 bases de liquidacion                      |
| CodAseguradoras    | 47 codigos de aseguradoras                  |

## Dependencias

| Libreria                    | Version | Uso                                              |
|-----------------------------|---------|--------------------------------------------------|
| Apache POI                  | 5.2.5   | Lectura de archivos Excel (.xlsx)                |
| Log4j Core                  | 2.18.0  | Logging a consola y archivo (requerido por POI)  |
| Cucumber Java               | 7.18.0  | BDD testing con Gherkin en espanol               |
| Cucumber JUnit Platform     | 7.18.0  | Integracion Cucumber + JUnit 5                   |
| JUnit Jupiter               | 5.10.2  | Framework de testing                             |
| JUnit Platform Suite        | 1.10.2  | Suite runner para Cucumber                       |
| maven-cucumber-reporting    | 5.8.1   | Reporte HTML profesional con graficas            |

## Documentacion adicional

- `Plan_de_Pruebas_RPA_Migracion.docx` — Plan de pruebas con historias de usuario, criterios de aceptacion y casos de prueba
- `target/cucumber-html-reports/overview-features.html` — Reporte visual de los 16 tests (se genera con `mvn verify`)
- `execution-report.html` — Evidencia de ejecucion con campos clave del archivo generado (se genera al ejecutar el JAR)
