package com.pruebatecnica.rpa.generator;

import com.pruebatecnica.rpa.model.FieldDefinition;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class PostProcessor {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Map<String, String> lineaToClaseCartera;

    public PostProcessor(Map<String, String> lineaToClaseCartera) {
        this.lineaToClaseCartera = lineaToClaseCartera;
    }

    public void process(Map<String, String> record, List<FieldDefinition> fields) {
        applyClaseCarteraRules(record, fields);
        applyNitEmpConRules(record, fields);
        applyFinancialCoherence(record, fields);
        applyTasaRules(record, fields);
        applyCuotaRules(record, fields);
        applyConceptoCalendarioRules(record, fields);
        applyFormaPagoRules(record, fields);
        applySaldoRules(record, fields);
        applyCapitalPagadoRules(record, fields);
        applyPlazoRules(record, fields);
        applyRotativoRules(record, fields);
        applyReestructuradoRules(record, fields);
        applySenPreResRules(record, fields);
        applyDiasMoraRules(record, fields);
        applyEstadoRules(record, fields);
        applyEstCausRules(record, fields);
        applyProcJuridRules(record, fields);
        applyFngRules(record, fields);
        applyFrechRules(record, fields);
        applyNumTitRules(record, fields);
        applyFactorMora(record, fields);
        applySignos(record, fields);
    }

    private void applyClaseCarteraRules(Map<String, String> record, List<FieldDefinition> fields) {
        String codLinea = record.get("CODLINCRE-IN");
        if (codLinea == null) return;

        String stripped = codLinea.trim().replaceAll("^0+", "");
        if (stripped.isEmpty()) stripped = "0";

        String clase = lineaToClaseCartera.get(stripped);
        if (clase == null) {
            clase = lineaToClaseCartera.get(codLinea.trim());
        }
        if (clase != null) {
            setField(record, fields, "CLASECAR-IN", clase);
        } else {
            setField(record, fields, "CLASECAR-IN", "O");
        }
    }

    private void applyNitEmpConRules(Map<String, String> record, List<FieldDefinition> fields) {
        String codLinea = record.get("CODLINCRE-IN");
        if (codLinea == null) return;

        String stripped = codLinea.trim().replaceAll("^0+", "");
        if (!"131".equals(stripped)) {
            FieldDefinition fd = findField(fields, "NITEMPCON-IN");
            if (fd != null) {
                setField(record, fields, "NITEMPCON-IN",
                        FieldGenerator.padNumeric("0", fd.getLongitud()));
            }
        }
    }

    private void applyFinancialCoherence(Map<String, String> record, List<FieldDefinition> fields) {
        long valPrestamo = parseLong(record.get("VALPRESTA-IN"));
        long capVigente = parseLong(record.get("CAPVIGACT-IN"));
        long capVencido = parseLong(record.get("CAPVENCI-IN"));
        long totalSaldo = capVigente + capVencido;

        if (totalSaldo > valPrestamo && valPrestamo > 0) {
            long newCapVigente = (long) (valPrestamo * 0.7);
            long newCapVencido = (long) (valPrestamo * 0.1);

            FieldDefinition fdVig = findField(fields, "CAPVIGACT-IN");
            FieldDefinition fdVen = findField(fields, "CAPVENCI-IN");

            if (fdVig != null) {
                record.put("CAPVIGACT-IN",
                        FieldGenerator.padNumeric(String.valueOf(newCapVigente), fdVig.getLongitud()));
            }
            if (fdVen != null) {
                record.put("CAPVENCI-IN",
                        FieldGenerator.padNumeric(String.valueOf(newCapVencido), fdVen.getLongitud()));
            }
        }
    }

    private void applyTasaRules(Map<String, String> record, List<FieldDefinition> fields) {
        String tipoTasa = record.get("TIPOTASA-IN");
        if (tipoTasa == null) return;

        if ("F".equals(tipoTasa.trim())) {
            setField(record, fields, "TASDINAM-IN", "X");
            setField(record, fields, "TIPCUOTA-IN", "F");
        } else if ("D".equals(tipoTasa.trim())) {
            String tasaDinam = record.get("TASDINAM-IN");
            if (tasaDinam == null || "X".equals(tasaDinam.trim())) {
                setField(record, fields, "TASDINAM-IN", "D");
            }
            setField(record, fields, "TIPCUOTA-IN", "V");
        }
    }

    private void applyCuotaRules(Map<String, String> record, List<FieldDefinition> fields) {
        String tipoCuota = record.get("TIPCUOTA-IN");
        if (tipoCuota == null) return;

        if ("F".equals(tipoCuota.trim())) {
            String formaCap = record.get("FORMPACAP-IN");
            if (formaCap != null) {
                setField(record, fields, "FORMPAINT-IN", formaCap.trim());
            }
        }
    }

    private void applyFormaPagoRules(Map<String, String> record, List<FieldDefinition> fields) {
        String tipoCuota = record.get("TIPCUOTA-IN");
        if ("F".equals(tipoCuota != null ? tipoCuota.trim() : "")) {
            String formaK = record.get("FORMPACAP-IN");
            String formaI = record.get("FORMPAINT-IN");
            if (formaK != null && formaI != null && !formaK.trim().equals(formaI.trim())) {
                setField(record, fields, "FORMPAINT-IN", formaK.trim());
            }
        }
    }

    private void applySaldoRules(Map<String, String> record, List<FieldDefinition> fields) {
        long capVigente = parseLong(record.get("CAPVIGACT-IN"));
        long capVencido = parseLong(record.get("CAPVENCI-IN"));
        long saldoActual = capVigente + capVencido;

        FieldDefinition fdSaldo = findField(fields, "VALSLDACT-IN");
        if (fdSaldo != null) {
            setField(record, fields, "VALSLDACT-IN",
                    FieldGenerator.padNumeric(String.valueOf(saldoActual), fdSaldo.getLongitud()));
        }
    }

    private void applyCapitalPagadoRules(Map<String, String> record, List<FieldDefinition> fields) {
        long valPrestamo = parseLong(record.get("VALPRESTA-IN"));
        long saldoActual = parseLong(record.get("VALSLDACT-IN"));
        long capitalPagado = valPrestamo - saldoActual;
        if (capitalPagado < 0) capitalPagado = 0;

        FieldDefinition fdPag = findField(fields, "VALCAPPAG-IN");
        if (fdPag != null) {
            setField(record, fields, "VALCAPPAG-IN",
                    FieldGenerator.padNumeric(String.valueOf(capitalPagado), fdPag.getLongitud()));
        }
    }

    private void applyPlazoRules(Map<String, String> record, List<FieldDefinition> fields) {
        String fecDesemb = record.get("FECDESEMB-IN");
        String fecFin = record.get("FECFINPACT-IN");

        if (fecDesemb != null && fecFin != null && fecDesemb.length() == 8 && fecFin.length() == 8) {
            try {
                LocalDate inicio = LocalDate.parse(fecDesemb, DATE_FMT);
                LocalDate fin = LocalDate.parse(fecFin, DATE_FMT);
                long days = ChronoUnit.DAYS.between(inicio, fin);
                long meses = Math.max(1, days / 30);

                FieldDefinition fdPlazo = findField(fields, "PLAZO-IN");
                if (fdPlazo != null) {
                    setField(record, fields, "PLAZO-IN",
                            FieldGenerator.padNumeric(String.valueOf(meses), fdPlazo.getLongitud()));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void applyRotativoRules(Map<String, String> record, List<FieldDefinition> fields) {
        String codLinea = record.get("CODLINCRE-IN");
        if (codLinea == null) return;

        String stripped = codLinea.trim().replaceAll("^0+", "");
        if ("14".equals(stripped)) {
            setFieldNumericZero(record, fields, "VALCAPPAG-IN");
            setFieldNumericZero(record, fields, "NUMCUOPAG-IN");
            setFieldNumericZero(record, fields, "NUMCUOAPA-IN");

            FieldDefinition fdFin = findField(fields, "FECFINPACT-IN");
            if (fdFin != null) {
                setField(record, fields, "FECFINPACT-IN",
                        FieldGenerator.padAlpha("99991231", fdFin.getLongitud()));
            }
        }
    }

    private void applyReestructuradoRules(Map<String, String> record, List<FieldDefinition> fields) {
        String estado = record.get("ESTADOPRE-IN");
        if (estado == null) return;

        if ("05".equals(estado.trim())) {
            String calReest = record.get("STRCALRES-IN");
            if (calReest == null || calReest.isBlank()) {
                setField(record, fields, "STRCALRES-IN", "A");
            }
        } else {
            FieldDefinition fdReest = findField(fields, "RESTRCTRD-IN");
            if (fdReest != null) {
                setField(record, fields, "RESTRCTRD-IN",
                        FieldGenerator.padAlpha("0", fdReest.getLongitud()));
            }
            FieldDefinition fdCalReest = findField(fields, "STRCALRES-IN");
            if (fdCalReest != null) {
                setField(record, fields, "STRCALRES-IN", " ");
            }
        }
    }

    private void applyDiasMoraRules(Map<String, String> record, List<FieldDefinition> fields) {
        int diasMora = (int) parseLong(record.get("NUMDIAMOR-IN"));

        String rangoCode;
        if (diasMora == 0) rangoCode = "BB";
        else if (diasMora <= 30) rangoCode = "01";
        else if (diasMora <= 60) rangoCode = "02";
        else if (diasMora <= 90) rangoCode = "03";
        else if (diasMora <= 180) rangoCode = "04";
        else if (diasMora <= 360) rangoCode = "05";
        else if (diasMora <= 720) rangoCode = "06";
        else rangoCode = "07";

        setField(record, fields, "DIASVEN-IN", rangoCode);

        int cuotasVencidas = (int) parseLong(record.get("NUMCUOVEN-IN"));
        if (cuotasVencidas > 0 && diasMora == 0) {
            FieldDefinition fd = findField(fields, "NUMDIAMOR-IN");
            if (fd != null) {
                setField(record, fields, "NUMDIAMOR-IN",
                        FieldGenerator.padNumeric(String.valueOf(cuotasVencidas * 30), fd.getLongitud()));
            }
        }
    }

    private void applyEstadoRules(Map<String, String> record, List<FieldDefinition> fields) {
        String estado = record.get("ESTADOPRE-IN");
        if ("06".equals(estado != null ? estado.trim() : "")) {
            setField(record, fields, "SENACTADM-IN", "4");
        }
    }

    private void applyEstCausRules(Map<String, String> record, List<FieldDefinition> fields) {
        int diasMora = (int) parseLong(record.get("NUMDIAMOR-IN"));
        String claseCartera = record.get("CLASECAR-IN");
        String clase = claseCartera != null ? claseCartera.trim() : "";

        String estCaus;
        if ("C".equals(clase)) {
            estCaus = diasMora > 90 ? "O" : "S";
        } else {
            estCaus = diasMora > 60 ? "O" : "S";
        }

        FieldDefinition fd = findField(fields, "ESTCAUS-IN");
        if (fd != null) {
            setField(record, fields, "ESTCAUS-IN", estCaus);
        }
    }

    private void applySenPreResRules(Map<String, String> record, List<FieldDefinition> fields) {
        String estado = record.get("ESTADOPRE-IN");
        String valor = "05".equals(estado != null ? estado.trim() : "") ? "1" : "0";
        setField(record, fields, "SENPRERES-IN", valor);
    }

    private void applyConceptoCalendarioRules(Map<String, String> record, List<FieldDefinition> fields) {
        String tipoCuota = record.get("TIPCUOTA-IN");
        if (tipoCuota == null) return;

        if ("F".equals(tipoCuota.trim())) {
            setField(record, fields, "CONCAL1-IN", "F");
            setField(record, fields, "CONCAL2-IN", "N");
        } else if ("V".equals(tipoCuota.trim())) {
            setField(record, fields, "CONCAL1-IN", "P");
            setField(record, fields, "CONCAL2-IN", "I");
        }
    }

    private void applyFngRules(Map<String, String> record, List<FieldDefinition> fields) {
        String esFng = record.get("ESFNG-IN");
        if (esFng == null || !"1".equals(esFng.trim())) {
            setField(record, fields, "ESFNG-IN", "0");
            String[] fngFields = {"TCBCOMFNG-IN", "FACCOBCOM-IN", "SIGVAL-COMI-FNG",
                    "VALCOMFNG-IN", "FECCOMFNG-IN", "NUMGTIAFNG-IN",
                    "CODDESFNG-IN", "CODPROFNG-IN", "SIGVAL-ABON-FNG", "VALABOFNG-IN"};
            for (String fieldName : fngFields) {
                FieldDefinition fd = findField(fields, fieldName);
                if (fd != null) {
                    if (fd.isNumerico()) {
                        record.put(fieldName, FieldGenerator.padNumeric("0", fd.getLongitud()));
                    } else if (fd.getLongitud() == 8 && fieldName.startsWith("FEC")) {
                        record.put(fieldName, "00000000");
                    } else {
                        record.put(fieldName, FieldGenerator.padAlpha("0", fd.getLongitud()));
                    }
                }
            }
        }
    }

    private void applyFrechRules(Map<String, String> record, List<FieldDefinition> fields) {
        String codLinea = record.get("CODLINCRE-IN");
        String stripped = "";
        if (codLinea != null) {
            stripped = codLinea.trim().replaceAll("^0+", "");
            if (stripped.isEmpty()) stripped = "0";
        }

        if (!"119".equals(stripped) && !"117".equals(stripped)) {
            setField(record, fields, "SENENTTER-IN", "0");
        }

        String senEntTer = record.get("SENENTTER-IN");
        if (senEntTer == null || "0".equals(senEntTer.trim())) {
            setField(record, fields, "SENENTTER-IN", "0");
            String[] frechFields = {"TIP-FRECH", "VAL-COB", "PORC-COB", "FEC-INICOB", "VALFRECH"};
            for (String fieldName : frechFields) {
                FieldDefinition fd = findField(fields, fieldName);
                if (fd != null) {
                    record.put(fieldName, FieldGenerator.padNumeric("0", fd.getLongitud()));
                }
            }
        }
    }

    private void applyProcJuridRules(Map<String, String> record, List<FieldDefinition> fields) {
        String senActAdm = record.get("SENACTADM-IN");
        String val = senActAdm != null ? senActAdm.trim() : "0";

        if ("0".equals(val)) {
            setField(record, fields, "PROCJURID-IN", "0");
        }
    }

    private void applyNumTitRules(Map<String, String> record, List<FieldDefinition> fields) {
        setField(record, fields, "NUM-TIT", "0");

        String[] titularFields = {
                "ID-LOC1", "TIPIDECLI-IN1", "NOMBRECLI-IN1", "SIGCLI-IN1",
                "VALSEG-IN1", "PORCSEG-IN1", "EXTPRIM1", "TIP-POLIZA1",
                "COD-ASEG1", "FEC-VTO1", "ACTECOPRE-IN1", "DIRCLI-IN1",
                "TELCLI-IN1", "CIUDCLI1", "FECNACIM1",
                "ID-LOC2", "TIPIDECLI-IN2", "NOMBRECLI-IN2", "SIGCLI-IN2",
                "VALSEG-IN2", "PORCSEG-IN2", "EXTPRIM2", "TIP-POLIZAVIV",
                "COD-ASEG2", "FEC-VTO2", "ACTECOPRE-IN2", "DIRCLI-IN2",
                "TELCLI-IN2", "CIUDCLI2", "FECNACIM2",
                "ID-LOC3", "TIPIDECLI-IN3", "NOMBRECLI-IN3", "SIGCLI-IN3",
                "VALSEG-IN3", "PORCSEG-IN3", "EXTPRIM3", "TIP-POLIZA3",
                "COD-ASEG3", "FEC-VTO3", "ACTECOPRE-IN3", "DIRCLI-IN3",
                "TELCLI-IN3"
        };
        for (String fieldName : titularFields) {
            FieldDefinition fd = findField(fields, fieldName);
            if (fd != null) {
                if (fd.isNumerico()) {
                    record.put(fieldName, FieldGenerator.padNumeric("0", fd.getLongitud()));
                } else if (fd.getLongitud() == 8 && (fieldName.startsWith("FEC") || fieldName.startsWith("FECN"))) {
                    record.put(fieldName, "00000000");
                } else {
                    record.put(fieldName, FieldGenerator.padAlpha("0", fd.getLongitud()));
                }
            }
        }
    }

    private void applyFactorMora(Map<String, String> record, List<FieldDefinition> fields) {
        FieldDefinition fd = findField(fields, "FACTLMOR-IN");
        if (fd != null) {
            setField(record, fields, "FACTLMOR-IN",
                    FieldGenerator.padNumeric("365", fd.getLongitud()));
        }
    }

    private void applySignos(Map<String, String> record, List<FieldDefinition> fields) {
        for (FieldDefinition fd : fields) {
            String nombre = fd.getNombre();
            if (nombre.startsWith("SIG") && fd.getLongitud() == 1) {
                String regla = fd.getRegla() != null ? fd.getRegla().toLowerCase() : "";
                if (regla.contains("signo -") || regla.contains("negativo")) {
                    record.put(nombre, "-");
                } else {
                    record.put(nombre, "+");
                }
            }
        }
    }

    private void setField(Map<String, String> record, List<FieldDefinition> fields, String nombre, String valor) {
        FieldDefinition fd = findField(fields, nombre);
        if (fd != null) {
            if (fd.isNumerico()) {
                record.put(nombre, FieldGenerator.padNumeric(valor, fd.getLongitud()));
            } else {
                record.put(nombre, FieldGenerator.padAlpha(valor, fd.getLongitud()));
            }
        } else {
            record.put(nombre, valor);
        }
    }

    private void setFieldNumericZero(Map<String, String> record, List<FieldDefinition> fields, String nombre) {
        FieldDefinition fd = findField(fields, nombre);
        if (fd != null) {
            record.put(nombre, FieldGenerator.padNumeric("0", fd.getLongitud()));
        }
    }

    private FieldDefinition findField(List<FieldDefinition> fields, String nombre) {
        return fields.stream()
                .filter(f -> nombre.equals(f.getNombre()))
                .findFirst()
                .orElse(null);
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
