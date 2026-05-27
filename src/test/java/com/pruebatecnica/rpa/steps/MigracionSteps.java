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

    @Then("CLASECAR-IN debe contener un valor valido del mapeo de lineas de credito")
    public void validarClaseCartera() throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();
        FieldDefinition fd = findField(fields, "CLASECAR-IN");
        Set<String> validValues = Set.of("C", "O", "H", "M");

        for (int i = 0; i < lines.size(); i++) {
            String val = extractField(lines.get(i), fd).trim();
            assertTrue(validValues.contains(val),
                    String.format("Línea %d: CLASECAR-IN='%s' no es válido (C/O/H/M)", i + 1, val));
        }
    }

    @Then("para CODLINCRE-IN igual a {string} los campos rotativos deben cumplir la regla especial")
    public void validarCreditoRotativo(String codLineaRef) throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();

        FieldDefinition fdCodLinea  = findField(fields, "CODLINCRE-IN");
        FieldDefinition fdCapPag    = findField(fields, "VALCAPPAG-IN");
        FieldDefinition fdNumCuoPag = findField(fields, "NUMCUOPAG-IN");
        FieldDefinition fdNumCuoApa = findField(fields, "NUMCUOAPA-IN");
        FieldDefinition fdFecFin    = findField(fields, "FECFINPACT-IN");

        String refStripped = codLineaRef.replaceAll("^0+", "");
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String cod = extractField(line, fdCodLinea).trim().replaceAll("^0+", "");
            if (!refStripped.equals(cod)) continue;

            assertEquals(0L, Long.parseLong(extractField(line, fdCapPag).trim()),
                    String.format("Línea %d (rotativo): VALCAPPAG-IN debe ser 0", i + 1));
            assertEquals(0L, Long.parseLong(extractField(line, fdNumCuoPag).trim()),
                    String.format("Línea %d (rotativo): NUMCUOPAG-IN debe ser 0", i + 1));
            assertEquals(0L, Long.parseLong(extractField(line, fdNumCuoApa).trim()),
                    String.format("Línea %d (rotativo): NUMCUOAPA-IN debe ser 0", i + 1));
            assertEquals("99991231", extractField(line, fdFecFin).trim(),
                    String.format("Línea %d (rotativo): FECFINPACT-IN debe ser 99991231", i + 1));
        }
    }

    @Then("TIPIDECLI-IN debe pertenecer a la lista de referencia TIP ID CLIENTE")
    public void validarTipoIdCliente() throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();
        Set<String> validValues = engine.getReferenceLists().get("TIP ID CLIENTE");
        FieldDefinition fd = findField(fields, "TIPIDECLI-IN");

        for (int i = 0; i < lines.size(); i++) {
            String val = extractField(lines.get(i), fd).trim();
            assertTrue(validValues.contains(val),
                    String.format("Línea %d: TIPIDECLI-IN='%s' no pertenece a la lista TIP ID CLIENTE", i + 1, val));
        }
    }

    @Then("CIUCLI-IN debe contener un codigo DANE valido de la lista de referencia")
    public void validarCiuCliDane() throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();
        Set<String> daneList = engine.getReferenceLists().get("DANE");
        FieldDefinition fd = findField(fields, "CIUCLI-IN");

        for (int i = 0; i < lines.size(); i++) {
            String raw = extractField(lines.get(i), fd).trim();
            String stripped = raw.replaceAll("^0+", "");
            if (stripped.isEmpty()) stripped = "0";
            assertTrue(daneList.contains(raw) || daneList.contains(stripped),
                    String.format("Línea %d: CIUCLI-IN='%s' no existe en la lista DANE", i + 1, raw));
        }
    }

    @Then("VALSLDACT-IN debe ser menor o igual a VALPRESTA-IN en todos los registros")
    public void validarValsldactMenorIgualValpresta() throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();
        FieldDefinition fdPresta = findField(fields, "VALPRESTA-IN");
        FieldDefinition fdSaldo  = findField(fields, "VALSLDACT-IN");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            long valPresta = Long.parseLong(extractField(line, fdPresta).trim());
            long valSaldo  = Long.parseLong(extractField(line, fdSaldo).trim());
            assertTrue(valSaldo <= valPresta,
                    String.format("Línea %d: VALSLDACT(%d) > VALPRESTA(%d)", i + 1, valSaldo, valPresta));
        }
    }

    @Then("FACTLMOR-IN debe ser siempre 365 en todos los registros")
    public void validarFactorMora() throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();
        FieldDefinition fd = findField(fields, "FACTLMOR-IN");

        for (int i = 0; i < lines.size(); i++) {
            long val = Long.parseLong(extractField(lines.get(i), fd).trim());
            assertEquals(365L, val,
                    String.format("Línea %d: FACTLMOR-IN=%d, se esperaba 365", i + 1, val));
        }
    }

    @Then("VALCAPPAG-IN debe ser igual a VALPRESTA-IN menos VALSLDACT-IN con minimo cero")
    public void validarCapitalPagado() throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();

        FieldDefinition fdCodLinea = findField(fields, "CODLINCRE-IN");
        FieldDefinition fdPresta   = findField(fields, "VALPRESTA-IN");
        FieldDefinition fdSaldo    = findField(fields, "VALSLDACT-IN");
        FieldDefinition fdCapPag   = findField(fields, "VALCAPPAG-IN");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // CODLINCRE=14 (rotativo): VALCAPPAG siempre 0 por regla rotativa, se excluye
            String codLinea = extractField(line, fdCodLinea).trim().replaceAll("^0+", "");
            if ("14".equals(codLinea)) continue;

            long valPresta = Long.parseLong(extractField(line, fdPresta).trim());
            long valSaldo  = Long.parseLong(extractField(line, fdSaldo).trim());
            long capPagado = Long.parseLong(extractField(line, fdCapPag).trim());
            long expected  = Math.max(0, valPresta - valSaldo);

            assertEquals(expected, capPagado,
                    String.format("Línea %d: VALCAPPAG(%d) != max(0, VALPRESTA(%d) - VALSLDACT(%d))",
                            i + 1, capPagado, valPresta, valSaldo));
        }
    }

    @Then("si CODLINCRE-IN es diferente de {string} entonces NITEMPCON-IN debe ser ceros")
    public void validarNitEmpCon(String codLineaRef) throws IOException {
        List<String> lines = Files.readAllLines(outputPath);
        List<FieldDefinition> fields = engine.getFields();

        FieldDefinition fdCodLinea  = findField(fields, "CODLINCRE-IN");
        FieldDefinition fdNitEmpCon = findField(fields, "NITEMPCON-IN");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String codLinea = extractField(line, fdCodLinea).trim().replaceAll("^0+", "");
            if (codLinea.isEmpty()) codLinea = "0";

            if (!codLineaRef.equals(codLinea)) {
                long nitVal = Long.parseLong(extractField(line, fdNitEmpCon).trim());
                assertEquals(0L, nitVal,
                        String.format("Línea %d: CODLINCRE=%s (distinto de %s) pero NITEMPCON no es ceros",
                                i + 1, codLinea, codLineaRef));
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
