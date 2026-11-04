package com.aadtex.ingestion.repository;

import com.aadtex.ingestion.io.LimitedInputStream;
import com.aadtex.ingestion.model.SalesRow;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class CsvSalesRepository {
    private static final String HEADER = "StoreID,StoreName,SalesAmount,Category,TransactionDate";

    @FunctionalInterface
    public interface RowConsumer { void accept(SalesRow row, long lineNumber) throws IOException; }

    public void readRows(InputStream input, RowConsumer consumer) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        var reader = new BufferedReader(new InputStreamReader(
                new LimitedInputStream(input, LimitedInputStream.MAX_BYTES), decoder), 4096);
        String header = reader.readLine();
        if (header != null && header.startsWith("\uFEFF")) header = header.substring(1);
        if (!HEADER.equals(header)) throw new IOException("Cabecera CSV incorrecta");
        String line;
        long lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) continue;
            if (line.length() > 65536) throw new IOException("Fila CSV demasiado larga");
            List<String> fields = parseLine(line);
            if (fields.size() != 5) throw new IOException("Fila CSV incompleta: " + lineNumber);
            consumer.accept(new SalesRow(fields.get(0), fields.get(1), fields.get(2), fields.get(3), fields.get(4)), lineNumber);
        }
    }

    private List<String> parseLine(String line) throws IOException {
        List<String> fields = new ArrayList<>();
        int position = 0;
        while (true) {
            StringBuilder field = new StringBuilder();
            if (position < line.length() && line.charAt(position) == '"') {
                position++;
                boolean closed = false;
                while (position < line.length()) {
                    char value = line.charAt(position++);
                    if (value == '"') {
                        if (position < line.length() && line.charAt(position) == '"') { field.append('"'); position++; }
                        else { closed = true; break; }
                    } else field.append(value);
                }
                if (!closed || (position < line.length() && line.charAt(position) != ','))
                    throw new IOException("Comillas CSV inválidas");
            } else {
                while (position < line.length() && line.charAt(position) != ',') {
                    char value = line.charAt(position++);
                    if (value == '"') throw new IOException("Comilla CSV fuera de campo");
                    field.append(value);
                }
            }
            fields.add(field.toString());
            if (position == line.length()) break;
            position++;
        }
        return fields;
    }
}
