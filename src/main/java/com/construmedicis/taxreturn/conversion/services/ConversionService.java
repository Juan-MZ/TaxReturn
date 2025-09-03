package com.construmedicis.taxreturn.conversion.services;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Service
public class ConversionService implements IConversionService {

    private static final String TEMPLATE_PATH = "calculadora_de_retenciones.xlsx"; // tu plantilla
    private static final String SHEET_NAME = "RETENCION";
    private static final String TOTALS_LABEL = "TOTALES"; // texto que identifica la fila de totales

    @Override
    public void convertInvoices(String xmlDirectoryPath, String outputExcelPath) throws Exception {
        File dir = new File(xmlDirectoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("La ruta no es un directorio válido: " + xmlDirectoryPath);
        }

        // Si no existe Excel de salida, copiar desde la plantilla
        File excelFile = new File(outputExcelPath);
        if (!excelFile.exists()) {
            Files.copy(new File(TEMPLATE_PATH).toPath(), excelFile.toPath());
        }

        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new IllegalStateException("No existe la hoja: " + SHEET_NAME);
            }

            // Recorremos todos los XML del directorio
            for (File xmlFile : dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xml"))) {
                processXmlAndInsert(xmlFile, sheet);
            }

            // Guardar cambios en el Excel
            try (FileOutputStream fos = new FileOutputStream(excelFile)) {
                workbook.write(fos);
            }
        }
    }

    private void processXmlAndInsert(File xmlFile, Sheet sheet) throws Exception {
        // Parsear el XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);

        // Extraer el CDATA de la factura
        String facturaXml = doc.getElementsByTagName("cbc:Description").item(0).getTextContent();

        // Parsear el XML de la factura interna
        Document facturaDoc = builder.parse(new ByteArrayInputStream(facturaXml.getBytes(StandardCharsets.UTF_8)));

        // --- Datos del proveedor ---
        String nitProveedor = facturaDoc.getElementsByTagName("cbc:CompanyID").item(0).getTextContent();
        String razonSocialProveedor = facturaDoc.getElementsByTagName("cbc:RegistrationName").item(0).getTextContent();

        // --- Datos de la factura ---
        String numeroFactura = facturaDoc.getElementsByTagName("cbc:ID").item(0).getTextContent();
        String fecha = facturaDoc.getElementsByTagName("cbc:IssueDate").item(0).getTextContent();

        // --- rete ica y rete fuente ---
        NodeList retenciones = facturaDoc.getElementsByTagName("cac:WithholdingTaxTotal");
        double retFuente = 0.0;
        double retIca = 0.0;

        for (int i = 0; i < retenciones.getLength(); i++) {
            Element ret = (Element) retenciones.item(i);
            String valor = ret.getElementsByTagName("cbc:TaxAmount").item(0).getTextContent();

            Element scheme = (Element) ret.getElementsByTagName("cac:TaxScheme").item(0);
            String nombre = scheme.getElementsByTagName("cbc:Name").item(0).getTextContent();

            if (nombre.toUpperCase().contains("RENTA")) {
                retFuente = Double.parseDouble(valor);
            } else if (nombre.toUpperCase().contains("ICA")) {
                retIca = Double.parseDouble(valor);
            }
        }

        // --- Base imponible ---
        double baseImponible = Double.parseDouble(facturaDoc.getElementsByTagName("cbc:LineExtensionAmount").item(0).getTextContent());

        // --- Valor total ---
        String valorTotal = facturaDoc.getElementsByTagName("cbc:PayableAmount").item(0).getTextContent();

        // ==============================
        // Buscar la fila de TOTALES
        // ==============================
        int totalsRowIndex = findTotalsRow(sheet);

        // ==============================
        // Insertar justo antes de TOTALES
        // ==============================
        sheet.shiftRows(totalsRowIndex, sheet.getLastRowNum(), 1);

        Row newRow = sheet.createRow(totalsRowIndex);

        // ==============================
        // Escribir valores en la nueva fila
        // ==============================
        newRow.createCell(1).setCellValue(razonSocialProveedor);
        newRow.createCell(2).setCellValue(nitProveedor);
        newRow.createCell(4).setCellValue(fecha);
        newRow.createCell(5).setCellValue(numeroFactura);
        newRow.createCell(6).setCellValue(Double.parseDouble(valorTotal));
        newRow.createCell(7).setCellValue(baseImponible);
        newRow.createCell(8).setCellValue("Compras generales (declarantes)");
        newRow.createCell(12).setCellValue("compras");
        newRow.createCell(18).setCellValue(retIca);
        newRow.createCell(19).setCellValue(retFuente);


        expandTable(sheet, totalsRowIndex);
    }

    private int findTotalsRow(Sheet sheet) {
        for (Row row : sheet) {
            Cell firstCell = row.getCell(1); // columna B (índice 1)
            if (firstCell != null && TOTALS_LABEL.equalsIgnoreCase(firstCell.getStringCellValue().trim())) {
                return row.getRowNum();
            }
        }
        throw new IllegalStateException("No se encontró la fila de TOTALES en la hoja " + sheet.getSheetName());
    }

    private void expandTable(Sheet sheet, int newRowIndex) {
        if (sheet instanceof XSSFSheet xssfSheet) {
            for (XSSFTable table : xssfSheet.getTables()) {
                CellReference startRef = table.getStartCellReference();
                CellReference endRef = table.getEndCellReference();

                if (newRowIndex >= startRef.getRow()) {
                    // Nuevo rango desde inicio de tabla hasta la nueva fila
                    AreaReference newArea = new AreaReference(
                            new CellReference(startRef.getRow(), startRef.getCol()),
                            new CellReference(newRowIndex, endRef.getCol()),
                            sheet.getWorkbook().getSpreadsheetVersion()
                    );

                    // Actualizar rango en el XML
                    table.getCTTable().setRef(newArea.formatAsString());

                    // Actualizar también el área en el objeto de Java
                    table.setArea(newArea);
                }
            }
        }
    }
}