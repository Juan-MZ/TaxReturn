package com.construmedicis.taxreturn.extraction.services;

public interface IExctractionService {
    void extractInvoices(String userId, String query, String outputDir) throws Exception;
}
