package com.pruebatecnica.rpa.steps;

import com.pruebatecnica.rpa.engine.MigrationEngine;
import com.pruebatecnica.rpa.model.FieldDefinition;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class MigracionSteps {

    private MigrationEngine engine;
    private List<String> generatedRecords;
    private Path outputPath;

    @Given("el archivo de estructura {string}")
    public void elArchivoDeEstructura(String fileName) throws IOException {
        engine = new MigrationEngine();

        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path excelPath = projectRoot.resolve(fileName);

        if (!Files.exists(excelPath)) {
            excelPath = projectRoot.getParent().resolve(fileName);
        }

        engine.loadSchema(excelPath.toString());
        outputPath = projectRoot.resolve("salida.txt");
    }

    @Then("el esquema debe tener {int} campos definidos")
    public void elEsquemaDebeTenerCamposDefinidos(int expectedCount) {
        assertEquals(expectedCount, engine.getFieldCount(),
                "El número de campos no coincide con lo esperado");
    }

    @And("las listas de referencia deben estar cargadas")
    public void lasListasDeReferenciaDebenEstarCargadas() {
        Map<String, Set<String>> refs = engine.getReferenceLists();
        assertFalse(refs.isEmpty(), "Las listas de referencia no se cargaron");

        assertTrue(refs.containsKey("TIP ID CLIENTE"), "Falta lista TIP ID CLIENTE");
        assertTrue(refs.get("TIP ID CLIENTE").contains("C"), "TIP ID CLIENTE debe contener 'C'");

        assertTrue(refs.containsKey("LINEAS"), "Falta lista LINEAS");
        assertFalse(refs.get("LINEAS").isEmpty(), "LINEAS no debe estar vacía");
    }

    @When("genero un archivo plano con {int} registros")
    public void generoUnArchivoPlano(int count) throws IOException {
        generatedRecords = engine.generateRecords(count);

        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        outputPath = projectRoot.resolve("salida.txt");
        engine.generateFile(count, outputPath);
    }

    @Then("el archivo {string} debe existir")
    public void elArchivoDebeExistir(String fileName) {
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path filePath = projectRoot.resolve(fileName);
        assertTrue(Files.exists(filePath),
                "El archivo " + fileName + " no fue generado en " + filePath);
    }

    @And("cada linea debe tener la longitud total esperada")
    public void cadaLineaDebeTenerLaLongitudEsperada() throws IOException {
        int expectedLength = engine.getTotalRecordLength();
        List<String> lines = Files.readAllLines(outputPath);

        for (int i = 0; i < lines.size(); i++) {
            assertEquals(expectedLength, lines.get(i).length(),
                    String.format("Línea %d tiene longitud %d, esperada %d",
                            i + 1, lines.get(i).length(), expectedLength));
        }
    }

    @And("todos los campos obligatorios deben tener datos validos")
    public void todosLosCamposObligatoriosDebenTenerDatos() throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();

        for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
            String line = lines.get(lineNum);

            for (FieldDefinition fd : fields) {
                if (!fd.isObligatorio()) continue;
                if (allowsBlankDefault(fd)) continue;
                int start = fd.getInicio() - 1;
                int end = fd.getFin();

                if (end > line.length()) continue;

                String value = line.substring(start, end);
                assertFalse(value.isBlank(),
                        String.format("Línea %d: campo obligatorio %s está vacío",
                                lineNum + 1, fd.getNombre()));
            }
        }
    }

    @Then("si TIPOTASA-IN es {string} entonces TASDINAM-IN debe ser {string}")
    public void validarTasaDinamica(String tipoTasa, String tasaDinamEsperada) throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();

        FieldDefinition fdTipo = findField(fields, "TIPOTASA-IN");
        FieldDefinition fdDinam = findField(fields, "TASDINAM-IN");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String tipo = extractField(line, fdTipo);
            if (tipoTasa.equals(tipo.trim())) {
                String dinam = extractField(line, fdDinam);
                assertEquals(tasaDinamEsperada, dinam.trim(),
                        String.format("Línea %d: TIPOTASA=F pero TASDINAM=%s", i + 1, dinam.trim()));
            }
        }
    }

    @And("si TIPOTASA-IN es {string} entonces TIPCUOTA-IN debe ser {string}")
    public void validarTipoCuota(String tipoTasa, String tipoCuotaEsperada) throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();

        FieldDefinition fdTipo = findField(fields, "TIPOTASA-IN");
        FieldDefinition fdCuota = findField(fields, "TIPCUOTA-IN");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String tipo = extractField(line, fdTipo);
            if (tipoTasa.equals(tipo.trim())) {
                String cuota = extractField(line, fdCuota);
                assertEquals(tipoCuotaEsperada, cuota.trim(),
                        String.format("Línea %d: TIPOTASA=F pero TIPCUOTA=%s", i + 1, cuota.trim()));
            }
        }
    }

    @Then("VALSLDACT-IN debe ser igual a CAPVIGACT-IN mas CAPVENCI-IN")
    public void validarSaldoCalculado() throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();

        FieldDefinition fdSaldo = findField(fields, "VALSLDACT-IN");
        FieldDefinition fdVigente = findField(fields, "CAPVIGACT-IN");
        FieldDefinition fdVencido = findField(fields, "CAPVENCI-IN");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            long saldo = Long.parseLong(extractField(line, fdSaldo).trim());
            long vigente = Long.parseLong(extractField(line, fdVigente).trim());
            long vencido = Long.parseLong(extractField(line, fdVencido).trim());

            assertEquals(vigente + vencido, saldo,
                    String.format("Línea %d: VALSLDACT(%d) != CAPVIGACT(%d) + CAPVENCI(%d)",
                            i + 1, saldo, vigente, vencido));
        }
    }

    @Then("todos los campos de fecha deben tener formato aaaammdd valido")
    public void validarFormatoFechas() throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (FieldDefinition fd : fields) {
                if (!fd.getNombre().startsWith("FEC") || fd.getLongitud() != 8) continue;

                String val = extractField(line, fd).trim();
                if ("00000000".equals(val)) continue;

                try {
                    LocalDate.parse(val, fmt);
                } catch (DateTimeParseException e) {
                    fail(String.format("Línea %d: campo %s tiene fecha inválida '%s'",
                            i + 1, fd.getNombre(), val));
                }
            }
        }
    }

    @And("el archivo debe contener exactamente {int} lineas")
    public void elArchivoDebeContenerLineas(int expectedLines) throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        assertEquals(expectedLines, lines.size(),
                String.format("El archivo tiene %d líneas, se esperaban %d", lines.size(), expectedLines));
    }

    private FieldDefinition findField(List<FieldDefinition> fields, String nombre) {
        return fields.stream()
                .filter(f -> nombre.equals(f.getNombre()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Campo no encontrado: " + nombre));
    }

    private String extractField(String line, FieldDefinition fd) {
        int start = fd.getInicio() - 1;
        int end = fd.getFin();
        if (end > line.length()) {
            return "";
        }
        return line.substring(start, end);
    }

    private boolean allowsBlankDefault(FieldDefinition fd) {
        String regla = fd.getRegla() != null ? fd.getRegla().toLowerCase() : "";
        String datos = fd.getDatosAceptados() != null ? fd.getDatosAceptados().toLowerCase() : "";
        String combined = regla + " " + datos;
        return combined.contains("default ' '") || combined.contains("default espacio")
                || combined.contains("default vacio") || combined.contains("defaulf espacio");
    }

    private static class AssertionError extends RuntimeException {
        AssertionError(String message) { super(message); }
    }
}
