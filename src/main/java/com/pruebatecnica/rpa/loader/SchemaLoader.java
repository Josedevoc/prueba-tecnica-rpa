package com.pruebatecnica.rpa.loader;

import com.pruebatecnica.rpa.model.FieldDefinition;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class SchemaLoader {

    private List<FieldDefinition> fields;
    private Map<String, Set<String>> referenceLists;
    private Map<String, String> lineaToClaseCartera;
    private int totalRecordLength;

    public void load(String excelPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(excelPath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            loadFieldDefinitions(workbook);
            loadReferenceLists(workbook);
        }
    }

    private void loadFieldDefinitions(Workbook workbook) {
        Sheet sheet = findSheet(workbook, "DetalleTécnico", "DetalleTecnico");
        fields = new ArrayList<>();

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String nombre = getCellString(row, 1);
            if (nombre == null || nombre.isBlank()) continue;

            FieldDefinition fd = new FieldDefinition();
            fd.setNumero(getCellInt(row, 0));
            fd.setNombre(nombre.trim());
            fd.setTipo(getCellString(row, 11) != null ? getCellString(row, 11).trim() : "ALFANUMERICO");

            int enteros = getCellInt(row, 13);
            int decimales = getCellInt(row, 14);
            int longitud = getCellInt(row, 15);

            if (longitud == 0 && enteros > 0) {
                longitud = enteros + decimales;
            }

            fd.setEnteros(enteros);
            fd.setDecimales(decimales);
            fd.setLongitud(longitud);
            fd.setObligatorio("SI".equalsIgnoreCase(getCellString(row, 5)));
            fd.setRegla(getCellString(row, 6));
            fd.setListaRef(getCellString(row, 8));
            fd.setDefaultVal(getCellString(row, 4));
            fd.setDatosAceptados(getCellString(row, 12));

            fields.add(fd);
        }

        computePositions();
    }

    private void computePositions() {
        int pos = 1;
        for (FieldDefinition fd : fields) {
            fd.setInicio(pos);
            fd.setFin(pos + fd.getLongitud() - 1);
            pos += fd.getLongitud();
        }
        totalRecordLength = pos - 1;
    }

    private void loadReferenceLists(Workbook workbook) {
        referenceLists = new LinkedHashMap<>();
        lineaToClaseCartera = new LinkedHashMap<>();

        loadTipIdCliente();
        loadCodTipTasa();
        loadFormPago();
        loadSenalAdmin();
        loadCodRanDiasMora();
        loadTemporalidad();

        loadLineasCredito(workbook);
        loadSimpleCodeList(workbook, "CodigoDANE", "DANE", 0);
        loadConvenios(workbook);
        loadSimpleCodeList(workbook, "BaseLiquidacion", "BASES", 0);
        loadSimpleCodeList(workbook, "CodAseguradoras", "Cod. Aseguradoras", 0);

        loadMissingListDefaults();
    }

    private void loadTipIdCliente() {
        referenceLists.put("TIP ID CLIENTE",
                new LinkedHashSet<>(Arrays.asList("C", "E", "I", "J", "L", "P", "R", "T")));
    }

    private void loadCodTipTasa() {
        referenceLists.put("COD TIP TASA",
                new LinkedHashSet<>(Arrays.asList("D", "E", "X", "I", "B", "R")));
    }

    private void loadFormPago() {
        referenceLists.put("FORM PAG K + I",
                new LinkedHashSet<>(Arrays.asList("01", "02", "03", "04", "05", "06", "12")));
    }

    private void loadSenalAdmin() {
        referenceLists.put("SEÑAL ADMIN",
                new LinkedHashSet<>(Arrays.asList("0", "1", "2", "8")));
    }

    private void loadCodRanDiasMora() {
        referenceLists.put("COD RAN DIAS MORA",
                new LinkedHashSet<>(Arrays.asList("BB", "01", "02", "03", "04", "05", "06", "07")));
    }

    private void loadTemporalidad() {
        referenceLists.put("TEMPORALIDAD",
                new LinkedHashSet<>(Arrays.asList("A", "B", "C", "D", "E")));
    }

    private void loadLineasCredito(Workbook workbook) {
        Sheet sheet = findSheet(workbook, "Lineas de credito");
        if (sheet == null) return;

        Set<String> codigos = new LinkedHashSet<>();
        for (int r = 2; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String codigoFija = getCellString(row, 0);
            String claseFija = getCellString(row, 2);

            if (codigoFija != null && !codigoFija.isBlank() && !"None".equals(codigoFija)) {
                for (String code : codigoFija.split("\\n")) {
                    code = code.trim();
                    if (!code.isEmpty()) {
                        String stripped = code.replaceAll("^0+", "");
                        if (stripped.isEmpty()) stripped = "0";
                        codigos.add(stripped);
                        if (claseFija != null && !claseFija.isBlank()) {
                            String clase = claseFija.trim().split("\\n")[0].trim();
                            if (clase.length() == 1 && "COHM".contains(clase)) {
                                lineaToClaseCartera.put(stripped, clase);
                            }
                        }
                    }
                }
            }

            String codigoVar = getCellString(row, 3);
            String claseVar = getCellString(row, 5);

            if (codigoVar != null && !codigoVar.isBlank() && !"None".equals(codigoVar)) {
                for (String code : codigoVar.split("\\n")) {
                    code = code.trim();
                    if (!code.isEmpty()) {
                        String stripped = code.replaceAll("^0+", "");
                        if (stripped.isEmpty()) stripped = "0";
                        codigos.add(stripped);
                        if (claseVar != null && !claseVar.isBlank()) {
                            String clase = claseVar.trim().split("\\n")[0].trim();
                            if (clase.length() == 1 && "COHM".contains(clase)) {
                                lineaToClaseCartera.putIfAbsent(stripped, clase);
                            }
                        }
                    }
                }
            }
        }
        referenceLists.put("LINEAS", codigos);
    }

    private void loadConvenios(Workbook workbook) {
        Sheet sheet = findSheet(workbook, "Convenios");
        if (sheet == null) return;

        Set<String> convenios = new LinkedHashSet<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String val = getCellString(row, 0);
            if (val != null && !val.isBlank()) {
                convenios.add(val.trim());
            }
        }
        referenceLists.put("CONVENIOS", convenios);
    }

    private void loadMissingListDefaults() {
        referenceLists.putIfAbsent("Asignacion_Comercial",
                new LinkedHashSet<>(Arrays.asList("0001", "0002", "0003", "0004", "0005")));

        referenceLists.putIfAbsent("PROC DE LEY",
                new LinkedHashSet<>(Arrays.asList("0", "1", "2", "3")));

        referenceLists.putIfAbsent("Productos_FNG",
                new LinkedHashSet<>(Arrays.asList("01", "02", "03")));

        referenceLists.putIfAbsent("TIP FRECH",
                new LinkedHashSet<>(Arrays.asList("0", "1", "2")));

        referenceLists.putIfAbsent("SEÑAL ADMIN",
                new LinkedHashSet<>(Arrays.asList("0", "1", "2", "8")));
    }

    private void loadSimpleCodeList(Workbook workbook, String sheetName, String listName, int codeCol) {
        Sheet sheet = findSheet(workbook, sheetName);
        if (sheet == null) return;

        Set<String> codes = new LinkedHashSet<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String val = getCellString(row, codeCol);
            if (val != null && !val.isBlank()) {
                codes.add(val.trim());
            }
        }
        referenceLists.put(listName, codes);
    }

    private Sheet findSheet(Workbook workbook, String... names) {
        for (String name : names) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String sheetName = workbook.getSheetAt(i).getSheetName();
                if (sheetName.equalsIgnoreCase(name) || normalize(sheetName).equalsIgnoreCase(normalize(name))) {
                    return workbook.getSheetAt(i);
                }
            }
        }
        return null;
    }

    private String normalize(String s) {
        return s.replaceAll("[éè]", "e").replaceAll("[áà]", "a")
                .replaceAll("[íì]", "i").replaceAll("[óò]", "o")
                .replaceAll("[úù]", "u");
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    yield String.valueOf((long) d);
                }
                yield String.valueOf(d);
            }
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        double d = cell.getNumericCellValue();
                        if (d == Math.floor(d)) yield String.valueOf((long) d);
                        yield String.valueOf(d);
                    } catch (Exception e2) {
                        yield null;
                    }
                }
            }
            default -> null;
        };
    }

    private int getCellInt(Row row, int col) {
        String val = getCellString(row, col);
        if (val == null || val.isBlank()) return 0;
        val = val.trim().split("[,.]")[0].replaceAll("[^0-9]", "");
        if (val.isEmpty()) return 0;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public List<FieldDefinition> getFields() { return fields; }
    public Map<String, Set<String>> getReferenceLists() { return referenceLists; }
    public Map<String, String> getLineaToClaseCartera() { return lineaToClaseCartera; }
    public int getTotalRecordLength() { return totalRecordLength; }
}
