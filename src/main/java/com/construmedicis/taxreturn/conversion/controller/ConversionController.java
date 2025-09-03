package com.construmedicis.taxreturn.conversion.controller;


import com.construmedicis.taxreturn.conversion.services.IConversionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/conversion")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ConversionController {
    private final IConversionService iConversionService;

    public ConversionController(IConversionService iConversionService) {
        this.iConversionService = iConversionService;
    }

    @GetMapping("/convertInvoices")
    public void convertInvoices(@RequestParam final String xmlDirectoryPath, @RequestParam final String outputExcelPath) throws Exception {
        iConversionService.convertInvoices(xmlDirectoryPath, outputExcelPath);
    }
}
