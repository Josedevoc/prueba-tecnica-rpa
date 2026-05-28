package com.pruebatecnica.rpa;

import com.pruebatecnica.rpa.engine.MigrationEngine;
import com.pruebatecnica.rpa.report.ExecutionReport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        String excelPath  = args.length > 0 ? args[0] : "DetalleTecnico.xlsx";
        String outputFile = args.length > 2 ? args[2] : "salida.txt";
        int recordCount;

        try {
            recordCount = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        } catch (NumberFormatException e) {
            log.error("ERROR: El segundo parametro debe ser un numero entero (cantidad de registros). Valor recibido: '{}'", args[1]);
            System.exit(1);
            return;
        }

        log.info("=== Generador de Archivo Plano de Migracion ===");
        log.info("Excel de estructura : {}", excelPath);
        log.info("Registros a generar : {}", recordCount);
        log.info("Archivo de salida   : {}", outputFile);

        long startTime = System.currentTimeMillis();

        try {
            MigrationEngine engine = new MigrationEngine();
            engine.loadSchema(excelPath);

            Path outputPath = Paths.get(outputFile);
            engine.generateFile(recordCount, outputPath);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Proceso completado en {} ms", elapsed);
            log.info("Archivo generado: {}", outputPath.toAbsolutePath());

            ExecutionReport.generate(engine, recordCount, outputPath, startTime);

        } catch (java.nio.file.NoSuchFileException e) {
            log.error("ERROR: No se encontro el archivo Excel '{}'", excelPath);
            log.error("Asegurese de que DetalleTecnico.xlsx este en el directorio de ejecucion.");
            System.exit(1);
        } catch (java.io.IOException e) {
            log.error("ERROR de entrada/salida: {}", e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            log.error("ERROR inesperado: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
