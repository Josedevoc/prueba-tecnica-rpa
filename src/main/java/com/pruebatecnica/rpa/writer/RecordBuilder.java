package com.pruebatecnica.rpa.writer;

import com.pruebatecnica.rpa.model.FieldDefinition;

import java.util.List;
import java.util.Map;

public class RecordBuilder {

    private final List<FieldDefinition> fields;
    private final int totalLength;

    public RecordBuilder(List<FieldDefinition> fields, int totalLength) {
        this.fields = fields;
        this.totalLength = totalLength;
    }

    public String build(Map<String, String> record) {
        StringBuilder sb = new StringBuilder(totalLength);

        for (FieldDefinition fd : fields) {
            String value = record.getOrDefault(fd.getNombre(), "");
            int len = fd.getLongitud();

            if (value.length() > len) {
                value = value.substring(0, len);
            } else if (value.length() < len) {
                if (fd.isNumerico()) {
                    StringBuilder pad = new StringBuilder();
                    for (int i = value.length(); i < len; i++) pad.append('0');
                    pad.append(value);
                    value = pad.toString();
                } else {
                    value = String.format("%-" + len + "s", value);
                }
            }

            sb.append(value);
        }

        if (sb.length() != totalLength) {
            if (sb.length() < totalLength) {
                while (sb.length() < totalLength) sb.append(' ');
            } else {
                sb.setLength(totalLength);
            }
        }

        return sb.toString();
    }

    public int getTotalLength() {
        return totalLength;
    }
}
