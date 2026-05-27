package com.pruebatecnica.rpa;

import com.pruebatecnica.rpa.engine.MigrationEngine;
import com.pruebatecnica.rpa.report.ExecutionReport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        String excelPath = args.length > 0 ? args[0] : "DetalleTecnico.xlsx";
        int recordCount = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        String outputFile = args.length > 2 ? args[2] : "salida.txt";

        log.info("=== Generador de Archivo Plano de Migracion ===");
        log.info("Excel de estructura : {}", excelPath);
        log.info("Registros a generar : {}", recordCount);
        log.info("Archivo de salida   : {}", outputFile);

        long startTime = System.currentTimeMillis();

        MigrationEngine engine = new MigrationEngine();
        engine.loadSchema(excelPath);

        Path outputPath = Paths.get(outputFile);
        engine.generateFile(recordCount, outputPath);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Proceso completado en {} ms", elapsed);
        log.info("Archivo generado: {}", outputPath.toAbsolutePath());

        ExecutionReport.generate(engine, recordCount, outputPath, startTime);
    }
}
