package com.construmedicis.taxreturn.conversion.services;

public interface IConversionService {
    void convertInvoices(String xmlDirectoryPath, String outputExcelPath) throws Exception;
}
