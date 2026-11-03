package com.aadtex.ingestion;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CsvSalesReader {
    private static final String HEADER = "StoreID,StoreName,SalesAmount,Category,TransactionDate";
    public record Sales(long transactions, BigDecimal totalSales, Set<String> storeNames) { }

    public Sales read(InputStream input) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        var reader = new BufferedReader(new InputStreamReader(
                new FileOperations.LimitedInputStream(input, FileOperations.MAX_FILE_BYTES), decoder), 4096);
        String header = reader.readLine();
        if (header != null && header.startsWith("\uFEFF")) header = header.substring(1);
        if (!HEADER.equals(header)) throw new IOException("Cabecera CSV incorrecta");
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        long count = 0;
        Set<String> names = new LinkedHashSet<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            if (line.length() > 65536) throw new IOException("Fila CSV demasiado larga");
            List<String> fields = parseLine(line);
            if (fields.size() != 5 || fields.stream().anyMatch(String::isBlank))
                throw new IOException("Fila CSV incompleta: " + (count + 2));
            try {
                if (!fields.get(2).matches("[0-9]{1,15}(\\.[0-9]{1,2})?"))
                    throw new IllegalArgumentException("Importe inválido");
                BigDecimal amount = new BigDecimal(fields.get(2)).setScale(2);
                LocalDate.parse(fields.get(4));
                total = total.add(amount);
            } catch (IllegalArgumentException | java.time.DateTimeException e) {
                throw new IOException("Importe o fecha inválidos en fila " + (count + 2), e);
            }
            names.add(fields.get(1));
            if (names.size() > 10000) throw new IOException("Demasiadas tiendas en un lote");
            count++;
        }
        if (count == 0) throw new IOException("CSV sin ventas");
        return new Sales(count, total, Collections.unmodifiableSet(names));
    }

    // Admite comas y comillas escapadas dentro de campos entrecomillados (una fila por línea).
    private List<String> parseLine(String line) throws IOException {
        List<String> fields = new ArrayList<>();
        int i = 0;
        while (true) {
            StringBuilder field = new StringBuilder();
            if (i < line.length() && line.charAt(i) == '"') {
                i++;
                boolean closed = false;
                while (i < line.length()) {
                    char c = line.charAt(i++);
                    if (c == '"') {
                        if (i < line.length() && line.charAt(i) == '"') { field.append('"'); i++; }
                        else { closed = true; break; }
                    } else field.append(c);
                }
                if (!closed || (i < line.length() && line.charAt(i) != ',')) throw new IOException("Comillas CSV inválidas");
            } else {
                while (i < line.length() && line.charAt(i) != ',') {
                    char c = line.charAt(i++);
                    if (c == '"') throw new IOException("Comilla CSV fuera de campo");
                    field.append(c);
                }
            }
            fields.add(field.toString());
            if (i == line.length()) break;
            i++;
        }
        return fields;
    }
}
