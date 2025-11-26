package com.construmedicis.taxreturn.extraction.controller;

import com.construmedicis.taxreturn.extraction.services.IExctractionService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> extractInvoices(String query, String outputDir) {
        try {
            iExctractionService.extractInvoices("me", query, outputDir);
            return ResponseEntity.ok("Extraction started");
        } catch (IllegalStateException ise) {
            // Not authenticated or user-facing issue
            return ResponseEntity.status(503).body(ise.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }
}
