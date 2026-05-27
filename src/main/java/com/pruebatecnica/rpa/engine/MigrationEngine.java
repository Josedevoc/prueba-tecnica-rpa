package com.pruebatecnica.rpa.engine;

import com.pruebatecnica.rpa.generator.FieldGenerator;
import com.pruebatecnica.rpa.generator.PostProcessor;
import com.pruebatecnica.rpa.loader.SchemaLoader;
import com.pruebatecnica.rpa.model.FieldDefinition;
import com.pruebatecnica.rpa.writer.FlatFileWriter;
import com.pruebatecnica.rpa.writer.RecordBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MigrationEngine {

    private static final Logger log = LogManager.getLogger(MigrationEngine.class);

    private final SchemaLoader schemaLoader;
    private FieldGenerator fieldGenerator;
    private PostProcessor postProcessor;
    private RecordBuilder recordBuilder;

    public MigrationEngine() {
        this.schemaLoader = new SchemaLoader();
    }

    public void loadSchema(String excelPath) throws IOException {
        log.info("Cargando esquema desde: {}", excelPath);
        schemaLoader.load(excelPath);
        fieldGenerator = new FieldGenerator(schemaLoader.getReferenceLists());
        postProcessor = new PostProcessor(schemaLoader.getLineaToClaseCartera());
        recordBuilder = new RecordBuilder(schemaLoader.getFields(), schemaLoader.getTotalRecordLength());
        log.info("Esquema cargado: {} campos | longitud registro: {} chars | {} listas de referencia",
                schemaLoader.getFields().size(),
                schemaLoader.getTotalRecordLength(),
                schemaLoader.getReferenceLists().size());
    }

    public List<String> generateRecords(int count) {
        List<FieldDefinition> fields = schemaLoader.getFields();
        List<String> records = new ArrayList<>(count);
        log.info("Iniciando generacion de {} registros...", count);

        int logStep = Math.max(1, count / 10);
        for (int i = 0; i < count; i++) {
            Map<String, String> record = new LinkedHashMap<>();

            for (FieldDefinition fd : fields) {
                record.put(fd.getNombre(), fieldGenerator.generate(fd));
            }

            postProcessor.process(record, fields);
            records.add(recordBuilder.build(record));

            if ((i + 1) % logStep == 0 || (i + 1) == count) {
                log.info("  Progreso: {}/{} registros generados", i + 1, count);
            }
        }

        log.info("Generacion completada: {} registros | {} chars por registro",
                records.size(), records.isEmpty() ? 0 : records.get(0).length());
        return records;
    }

    public void generateFile(int recordCount, Path outputPath) throws IOException {
        log.info("Escribiendo archivo de salida: {}", outputPath.toAbsolutePath());
        List<String> records = generateRecords(recordCount);
        new FlatFileWriter().write(outputPath, records);
        long sizeKb = Files.exists(outputPath) ? Files.size(outputPath) / 1024 : 0;
        log.info("Archivo escrito exitosamente: {} | {} registros | {} KB", outputPath.getFileName(), recordCount, sizeKb);
    }

    public int getTotalRecordLength() {
        return schemaLoader.getTotalRecordLength();
    }

    public int getFieldCount() {
        return schemaLoader.getFields().size();
    }

    public List<FieldDefinition> getFields() {
        return schemaLoader.getFields();
    }

    public Map<String, Set<String>> getReferenceLists() {
        return schemaLoader.getReferenceLists();
    }
}
