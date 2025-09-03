package com.construmedicis.taxreturn.extraction.controller;

import com.construmedicis.taxreturn.extraction.services.IExctractionService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/extraction")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ExtractionController {
    private final IExctractionService iExctractionService;

    public ExtractionController(IExctractionService iExctractionService) {
        this.iExctractionService = iExctractionService;
    }

    @GetMapping("/extractInvoices")
    public void extractInvoices(String query, String outputDir) throws Exception {
        iExctractionService.extractInvoices("me",  query, outputDir);
    }
}
