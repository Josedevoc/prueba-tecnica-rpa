package com.pruebatecnica.rpa.generator;

import com.pruebatecnica.rpa.model.FieldDefinition;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FieldGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Random RANDOM = new Random();
    private static final Set<String> EXCLUDED_WORDS = Set.of(
            "Default", "Valor", "Enviar", "Para", "Si", "No", "De", "El", "La", "En",
            "Se", "Este", "Debe", "Los", "Las", "Del", "Con", "Por", "Que", "Un");

    private final Map<String, Set<String>> referenceLists;

    public FieldGenerator(Map<String, Set<String>> referenceLists) {
        this.referenceLists = referenceLists;
    }

    public String generate(FieldDefinition fd) {
        String nombre = fd.getNombre();
        int longitud = fd.getLongitud();

        if (isSignField(nombre, fd)) {
            return generateSign(fd);
        }

        if (isDateField(nombre, fd)) {
            return generateDate(fd);
        }

        String fixedConstant = detectFixedConstant(fd);
        if (fixedConstant != null) {
            return fixedConstant;
        }

        String nameOverride = generateByName(fd);
        if (nameOverride != null) {
            return nameOverride;
        }

        if (fd.getListaRef() != null && !fd.getListaRef().isBlank()) {
            return generateFromList(fd);
        }

        if (isDefaultZeros(fd)) {
            return padNumeric("0", longitud);
        }

        if (isDefaultSpaces(fd)) {
            return padAlpha("", longitud);
        }

        String datosAceptados = fd.getDatosAceptados();
        if (datosAceptados != null && hasFixedValues(datosAceptados)) {
            String result = generateFromAccepted(fd, datosAceptados);
            if (result != null && !result.isBlank()) {
                return result;
            }
        }

        String regla = fd.getRegla();
        if (regla != null && hasFixedValues(regla)) {
            String result = generateFromAccepted(fd, regla);
            if (result != null && !result.isBlank()) {
                return result;
            }
        }

        if (fd.isNumerico()) {
            return generateNumeric(fd);
        }

        return generateAlphanumeric(fd);
    }

    private String detectFixedConstant(FieldDefinition fd) {
        String regla = fd.getRegla() != null ? fd.getRegla() : "";
        String datos = fd.getDatosAceptados() != null ? fd.getDatosAceptados() : "";
        String combined = regla + "\n" + datos;

        Pattern pSiempre = Pattern.compile("(?i)siempre\\s+(?:enviar|registrar|en)?\\s+(\\S+)");
        Matcher mSiempre = pSiempre.matcher(combined);
        if (mSiempre.find()) {
            String val = mSiempre.group(1).trim();
            if (val.equalsIgnoreCase("vacio") || val.equalsIgnoreCase("blanco")) {
                return padAlpha("", fd.getLongitud());
            }
            if (val.equals("el") || val.equals("la") || val.equals("los")) {
                return null;
            }
            if (val.length() <= 3) {
                return fd.isNumerico() ? padNumeric(val, fd.getLongitud()) : padAlpha(val, fd.getLongitud());
            }
        }

        Pattern pDefault = Pattern.compile("(?i)default\\s+([A-Za-z])(?:\\s|$)");
        Matcher mDefault = pDefault.matcher(combined);
        if (mDefault.find()) {
            String val = mDefault.group(1);
            if (!val.equalsIgnoreCase("c") || !combined.toLowerCase().contains("default cero")) {
                return padAlpha(val.toUpperCase(), fd.getLongitud());
            }
        }

        return null;
    }

    private String generateByName(FieldDefinition fd) {
        String nombre = fd.getNombre();

        if (nombre.equals("CIUCLI-IN")) {
            Set<String> daneCodes = referenceLists.get("DANE");
            if (daneCodes != null && !daneCodes.isEmpty()) {
                List<String> list = new ArrayList<>(daneCodes);
                return padNumeric(list.get(RANDOM.nextInt(list.size())), fd.getLongitud());
            }
        }
        if (nombre.equals("TELCLI-IN")) {
            StringBuilder phone = new StringBuilder("3");
            for (int i = 1; i < 10; i++) phone.append(RANDOM.nextInt(10));
            return padAlpha(phone.toString(), fd.getLongitud());
        }
        if (nombre.equals("MODINTCTE-IN")) {
            return "V";
        }
        if (nombre.equals("NUMPRESTA-IN")) {
            return padAlpha("CR" + String.format("%012d", RANDOM.nextInt(999999999) + 1L), fd.getLongitud());
        }
        if (nombre.equals("NOMBRECLI-IN") || nombre.equals("SIGCLI-IN")) {
            return padAlpha(randomName(fd.getLongitud() - 2), fd.getLongitud());
        }
        if (nombre.equals("DIRCLI-IN")) {
            return padAlpha("CALLE " + (RANDOM.nextInt(99) + 1) + " NO " + (RANDOM.nextInt(99) + 1), fd.getLongitud());
        }
        if (nombre.equals("NUMCUECAR-IN")) {
            return padAlpha("0000000001", fd.getLongitud());
        }

        return null;
    }

    private boolean isSignField(String nombre, FieldDefinition fd) {
        return fd.getLongitud() == 1 && (nombre.startsWith("SIG") || nombre.contains("SIGNO"));
    }

    private String generateSign(FieldDefinition fd) {
        String regla = fd.getRegla();
        if (regla != null) {
            String lower = regla.toLowerCase();
            if (lower.contains("signo -") || lower.contains("negativo")) {
                return "-";
            }
        }
        return "+";
    }

    private boolean isDateField(String nombre, FieldDefinition fd) {
        if (fd.getLongitud() == 8 && nombre.startsWith("FEC")) return true;
        String regla = fd.getRegla() != null ? fd.getRegla() : "";
        String valid = fd.getDatosAceptados() != null ? fd.getDatosAceptados() : "";
        return (regla + valid).toLowerCase().contains("aaaammdd");
    }

    private String generateDate(FieldDefinition fd) {
        String regla = fd.getRegla() != null ? fd.getRegla().toLowerCase() : "";
        String datos = fd.getDatosAceptados() != null ? fd.getDatosAceptados().toLowerCase() : "";
        String combined = regla + " " + datos;

        if (combined.contains("default cero") || combined.contains("default 0")) {
            if (!fd.isObligatorio() || combined.contains("aplica unicamente si")) {
                return "00000000";
            }
        }

        LocalDate base = LocalDate.of(2025, 6, 15);
        String nombre = fd.getNombre();

        if (nombre.contains("DESEMB")) {
            return base.minusYears(2).minusMonths(RANDOM.nextInt(24)).format(DATE_FMT);
        }
        if (nombre.contains("FINPACT")) {
            return base.plusYears(3).plusMonths(RANDOM.nextInt(36)).format(DATE_FMT);
        }
        if (nombre.contains("PROABO") || nombre.contains("PROXAB")) {
            return base.plusMonths(1).format(DATE_FMT);
        }
        if (nombre.contains("ULTCAU")) {
            return base.minusDays(1).format(DATE_FMT);
        }
        if (nombre.contains("INICAU") || nombre.contains("ULTFAC")) {
            return base.format(DATE_FMT);
        }
        if (nombre.contains("ULTABO") || nombre.contains("ULTCUOPAG")) {
            return base.minusMonths(1).format(DATE_FMT);
        }

        return base.format(DATE_FMT);
    }

    private String generateFromList(FieldDefinition fd) {
        String listName = fd.getListaRef().trim();
        Set<String> values = referenceLists.get(listName);

        if (values == null || values.isEmpty()) {
            for (Map.Entry<String, Set<String>> entry : referenceLists.entrySet()) {
                if (entry.getKey().trim().equalsIgnoreCase(listName.trim())) {
                    values = entry.getValue();
                    break;
                }
            }
        }

        if (values == null || values.isEmpty()) {
            return fd.isNumerico() ? padNumeric("0", fd.getLongitud()) : padAlpha(" ", fd.getLongitud());
        }

        List<String> list = new ArrayList<>(values);
        String picked = list.get(RANDOM.nextInt(list.size()));

        if (fd.isNumerico()) {
            return padNumeric(picked, fd.getLongitud());
        }
        return padAlpha(picked, fd.getLongitud());
    }

    private boolean hasFixedValues(String datos) {
        if (datos == null) return false;
        if (datos.contains(" - ") || datos.contains(". ")) return true;

        long lineCount = datos.lines().count();
        if (lineCount >= 2) {
            if (datos.matches("(?s).*[A-Z0-9]\\s*[-.:)]\\s+\\w.*")) return true;

            boolean allCodeEntries = datos.lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty())
                    .allMatch(l -> l.matches("[A-Za-z0-9+\\-]{1,5}(\\s+.*)?"));
            if (allCodeEntries) return true;
        }
        return false;
    }

    private String generateFromAccepted(FieldDefinition fd, String datosAceptados) {
        List<String> extracted = extractValues(datosAceptados);
        if (extracted.isEmpty()) {
            return fd.isNumerico() ? padNumeric("0", fd.getLongitud()) : padAlpha("", fd.getLongitud());
        }

        String picked = extracted.get(RANDOM.nextInt(extracted.size()));
        if (fd.isNumerico()) {
            return padNumeric(picked, fd.getLongitud());
        }
        return padAlpha(picked, fd.getLongitud());
    }

    private List<String> extractValues(String text) {
        List<String> values = new ArrayList<>();

        // Pattern 1: code followed by separator char (-, ., :, ))
        Pattern p1 = Pattern.compile("^\\s*([A-Z0-9+\\-]{1,5})\\s*[-.:)]", Pattern.MULTILINE);
        Matcher m1 = p1.matcher(text);
        while (m1.find()) {
            values.add(m1.group(1).trim());
        }
        if (!values.isEmpty()) return values;

        // Pattern 2: code followed by space and description
        Pattern p2 = Pattern.compile("^\\s*([A-Za-z0-9]{1,5})\\s+\\S", Pattern.MULTILINE);
        Matcher m2 = p2.matcher(text);
        while (m2.find()) {
            String v = m2.group(1).trim();
            if (!EXCLUDED_WORDS.contains(v)) {
                values.add(v);
            }
        }
        if (!values.isEmpty()) return values;

        // Pattern 3: bare codes, one per line (e.g. "0\n1")
        List<String> lines = text.lines()
                .map(String::trim)
                .filter(l -> !l.isEmpty())
                .toList();
        if (lines.size() >= 2 && lines.stream().allMatch(l -> l.matches("[A-Za-z0-9+\\-]{1,5}"))) {
            values.addAll(lines);
        }

        return values;
    }

    private boolean isDefaultZeros(FieldDefinition fd) {
        String regla = fd.getRegla() != null ? fd.getRegla().toLowerCase() : "";
        String datos = fd.getDatosAceptados() != null ? fd.getDatosAceptados().toLowerCase() : "";
        return (regla.contains("default cero") || datos.contains("default cero")
                || regla.contains("siempre deben llegar en cero") || datos.contains("enviar en ceros"))
                && !regla.contains("diferente de cero") && !datos.contains("diferente de cero");
    }

    private boolean isDefaultSpaces(FieldDefinition fd) {
        String regla = fd.getRegla() != null ? fd.getRegla().toLowerCase() : "";
        String datos = fd.getDatosAceptados() != null ? fd.getDatosAceptados().toLowerCase() : "";
        return (regla.contains("default vacio") || regla.contains("default espacio")
                || datos.contains("default vacio") || datos.contains("defaulf espacio")
                || regla.contains("siempre en vacio") || datos.contains("siempre en vacio"))
                && !fd.isNumerico();
    }

    private String generateNumeric(FieldDefinition fd) {
        if (fd.tieneDecimales()) {
            long intPart = 10000 + RANDOM.nextInt(990000);
            String intStr = String.valueOf(intPart);
            if (intStr.length() > fd.getEnteros()) {
                intStr = intStr.substring(0, fd.getEnteros());
            }
            intStr = padLeftZeros(intStr, fd.getEnteros());

            long decPart = RANDOM.nextInt((int) Math.pow(10, fd.getDecimales()));
            String decStr = padLeftZeros(String.valueOf(decPart), fd.getDecimales());

            return intStr + decStr;
        }

        long maxVal = (long) Math.pow(10, Math.min(fd.getLongitud(), 15)) - 1;
        long val = 1 + (long) (RANDOM.nextDouble() * Math.min(maxVal, 999999));
        return padNumeric(String.valueOf(val), fd.getLongitud());
    }

    private String generateAlphanumeric(FieldDefinition fd) {
        int len = fd.getLongitud();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(len, 8); i++) {
            sb.append((char) ('A' + RANDOM.nextInt(26)));
        }
        return padAlpha(sb.toString(), len);
    }

    private String randomName(int maxLen) {
        String[] names = {"JUAN PEREZ", "MARIA GOMEZ", "CARLOS LOPEZ", "ANA MARTINEZ",
                "PEDRO RODRIGUEZ", "LUCIA GARCIA", "ANDRES DIAZ", "SOFIA HERNANDEZ"};
        String name = names[RANDOM.nextInt(names.length)];
        return name.length() > maxLen ? name.substring(0, maxLen) : name;
    }

    public static String padAlpha(String value, int length) {
        if (value == null) value = "";
        if (value.length() > length) {
            return value.substring(0, length);
        }
        return String.format("%-" + length + "s", value);
    }

    public static String padNumeric(String value, int length) {
        if (value == null) value = "0";
        value = value.replaceAll("[^0-9]", "");
        if (value.isEmpty()) value = "0";
        if (value.length() > length) {
            return value.substring(value.length() - length);
        }
        return padLeftZeros(value, length);
    }

    private static String padLeftZeros(String value, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = value.length(); i < length; i++) {
            sb.append('0');
        }
        sb.append(value);
        return sb.toString();
    }
}
