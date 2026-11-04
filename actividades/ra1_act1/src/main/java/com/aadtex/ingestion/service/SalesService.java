package com.aadtex.ingestion.service;

import com.aadtex.ingestion.model.SalesRow;
import com.aadtex.ingestion.model.SalesSummary;
import com.aadtex.ingestion.repository.CsvSalesRepository;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class SalesService {
    private final CsvSalesRepository sales;
    public SalesService(CsvSalesRepository sales) { this.sales = sales; }

    public SalesSummary summarize(InputStream input) throws IOException {
        Accumulator result = new Accumulator();
        sales.readRows(input, result::add);
        if (result.count == 0) throw new IOException("CSV sin ventas");
        return new SalesSummary(result.count, result.total, result.names);
    }

    private static final class Accumulator {
        private BigDecimal total = BigDecimal.ZERO.setScale(2);
        private long count;
        private final Set<String> names = new LinkedHashSet<>();

        void add(SalesRow row, long lineNumber) throws IOException {
            if (List.of(row.storeId(), row.storeName(), row.amount(), row.category(), row.date()).stream().anyMatch(String::isBlank))
                throw new IOException("Fila CSV incompleta: " + lineNumber);
            try {
                if (!row.amount().matches("[0-9]{1,15}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Importe inválido");
                BigDecimal amount = new BigDecimal(row.amount()).setScale(2);
                LocalDate.parse(row.date());
                total = total.add(amount);
            } catch (IllegalArgumentException | java.time.DateTimeException e) {
                throw new IOException("Importe o fecha inválidos en fila " + lineNumber, e);
            }
            names.add(row.storeName());
            if (names.size() > 10000) throw new IOException("Demasiadas tiendas en un lote");
            count++;
        }
    }
}
