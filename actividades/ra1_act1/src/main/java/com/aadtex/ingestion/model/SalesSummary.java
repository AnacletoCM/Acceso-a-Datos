package com.aadtex.ingestion.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record SalesSummary(long transactions, BigDecimal totalSales, Set<String> storeNames) {
    public SalesSummary {
        storeNames = Collections.unmodifiableSet(new LinkedHashSet<>(storeNames));
    }
}
