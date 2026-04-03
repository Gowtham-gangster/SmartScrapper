package com.smartscraper.service;

import com.opencsv.CSVWriter;
import com.smartscraper.entity.ScrapedData;
import com.smartscraper.service.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ScrapedDataCsvExportService {

    private static final Logger logger = LoggerFactory.getLogger(ScrapedDataCsvExportService.class);

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path exportsDir;

    public ScrapedDataCsvExportService(@Value("${app.exports.dir:exports}") String exportsDir) {
        this.exportsDir = Path.of(exportsDir);
    }

    /**
     * Exports the given {@link ScrapedData} rows into a CSV file using OpenCSV.
     *
     * CSV Columns:
     * - Title
     * - Description
     * - URL
     * - Source
     * - Date
     *
     * @return the generated CSV file path
     */
    public Path exportSelectedToCSV(List<ScrapedData> data) throws Exception {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("data must not be null/empty");
        }

        Files.createDirectories(exportsDir);

        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        String fileName = "export_" + timestamp + ".csv";
        Path target = exportsDir.resolve(fileName);

        logger.info("Exporting {} ScrapedData rows to CSV: {}", data.size(), target);

        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(Files.newOutputStream(target), StandardCharsets.UTF_8)
        )) {
            // Header row
            writer.writeNext(new String[]{"Title", "Description", "URL", "Source", "Date"});

            for (ScrapedData row : data) {
                String title = nullToEmpty(row.getTitle());
                String description = nullToEmpty(row.getContent());
                String url = nullToEmpty(row.getSourceUrl());
                String source = UrlUtils.extractDomain(row.getSourceUrl());
                if (source == null) source = "";

                String date = row.getScrapedAt() == null ? "" : row.getScrapedAt().toString();

                writer.writeNext(new String[]{title, description, url, source, date});
            }
        }

        return target;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

