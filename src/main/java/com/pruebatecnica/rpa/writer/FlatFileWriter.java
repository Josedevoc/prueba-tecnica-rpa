package com.pruebatecnica.rpa.writer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FlatFileWriter {

    public void write(Path outputPath, List<String> records) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            for (int i = 0; i < records.size(); i++) {
                writer.write(records.get(i));
                if (i < records.size() - 1) {
                    writer.newLine();
                }
            }
        }
    }
}
