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
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;

@Service
public class ConversionService implements IConversionService {

    private static final String TEMPLATE_PATH = "calculadora_de_retenciones.xlsx"; // tu plantilla
    private static final String SHEET_NAME = "RETENCION 2025";
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
        double baseImponible = Double
                .parseDouble(facturaDoc.getElementsByTagName("cbc:LineExtensionAmount").item(0).getTextContent());

        // --- Valor total ---
        String valorTotal = facturaDoc.getElementsByTagName("cbc:PayableAmount").item(0).getTextContent();

        XSSFSheet xssfSheet = (XSSFSheet) sheet;
        XSSFTable table = xssfSheet.getTables().get(0); // tu tabla principal

        Workbook workbook = sheet.getWorkbook();
        CellStyle dateStyle = getDateStyle(workbook);

        insertRowInTableOnly(xssfSheet, table, newRow -> {
            writeCellWithStyle(newRow, 1, razonSocialProveedor, null);
            writeCellWithStyle(newRow, 2, nitProveedor, null);
            writeDateCell(newRow, 4, fecha, dateStyle);
            writeCellWithStyle(newRow, 5, numeroFactura, null);
            writeCellWithStyle(newRow, 6, Double.parseDouble(valorTotal), null);
            writeCellWithStyle(newRow, 7, baseImponible, null);
            writeCellWithStyle(newRow, 8, "Compras generales (declarantes)", null);
        });
    }

    private void writeCellWithStyle(Row row, int colIndex, String value, Row styleSource) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value);
        if (styleSource != null) {
            Cell src = styleSource.getCell(colIndex);
            if (src != null && src.getCellStyle() != null) {
                cell.setCellStyle(src.getCellStyle());
            }
        }
    }

    private void writeCellWithStyle(Row row, int colIndex, double value, Row styleSource) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value);
        if (styleSource != null) {
            Cell src = styleSource.getCell(colIndex);
            if (src != null && src.getCellStyle() != null) {
                cell.setCellStyle(src.getCellStyle());
            }
        }
    }

    private void insertRowInTableOnly(
            XSSFSheet sheet,
            XSSFTable table,
            Consumer<Row> rowFiller) {

        CellReference start = table.getStartCellReference();
        CellReference end = table.getEndCellReference();
        int lastTableCol = end.getCol();

        // Encontrar la fila de TOTALES en la hoja
        int totalsRowIndex = findTotalsRow(sheet);
        if (totalsRowIndex == -1) {
            throw new IllegalStateException("No se encontró la fila de TOTALES");
        }

        // Insertar SIEMPRE una fila ANTES de la fila de totales
        int newRowIndex = totalsRowIndex;

        // 1. Copiar fila de totales a la fila siguiente (manual, sin shiftRows)
        Row totalsRow = sheet.getRow(totalsRowIndex);
        Row newTotalsRow = sheet.createRow(totalsRowIndex + 1);

        copyRow(sheet, totalsRow, newTotalsRow);

        // 2. Limpiar la fila de totales (queda vacía la original)
        clearRow(totalsRow);

        // 3. Usar la fila original de totales como nueva fila de datos
        Row newRow = totalsRow;

        // Fila anterior a la fila nueva (para copiar estilo)
        Row styleSource = sheet.getRow(newRowIndex - 1);

        // Llenar la fila con los datos
        rowFiller.accept(newRow);

        // Copiar estilos
        if (styleSource != null) {
            for (int c = 0; c <= lastTableCol; c++) {
                Cell src = styleSource.getCell(c);
                Cell dst = newRow.getCell(c);
                if (src != null && dst != null) {
                    dst.setCellStyle(src.getCellStyle());
                }
            }
        }

        // Expandir la tabla evitando incluir la fila de totales
        AreaReference newArea = new AreaReference(
                start,
                new CellReference(newRowIndex, lastTableCol),
                sheet.getWorkbook().getSpreadsheetVersion());

        // ⬅️ OBLIGATORIO: actualizar área de la tabla
        table.setArea(newArea);

        // --- asegurar AutoFilter dentro de CTTable ---
        org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTable ctTable = table.getCTTable();
        String ref = newArea.formatAsString();

        if (ctTable.getAutoFilter() == null) {
            ctTable.addNewAutoFilter();
        }

        ctTable.getAutoFilter().setRef(ref);
        ctTable.setHeaderRowCount(1);

        // --- actualizar de forma segura el área de la tabla (evitar que Excel quite la
        // tabla) ---

        // newArea: AreaReference ya calculada (desde start hasta newEndRow,newEndCol)
        String newRef = newArea.formatAsString();

        // 1) actualizar la referencia principal del CTTable
        ctTable.setRef(newRef);

        // 2) actualizar AutoFilter dentro del CTTable si existe
        try {
            if (ctTable.getAutoFilter() != null) {
                ctTable.getAutoFilter().setRef(newRef);
            }
        } catch (Exception ignore) {
            /* defensivo */ }

        // 3) garantizar que tableColumns tenga la cuenta y columnas correctas
        // startCol y colCount ya definidos antes
        int startCol = start.getCol();
        int endCol = newArea.getLastCell().getCol();
        int colCount = endCol - startCol + 1;

        // obtener (o crear) el nodo tableColumns de forma compatible
        org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableColumns ctCols = ctTable.getTableColumns();
        if (ctCols == null) {
            ctCols = ctTable.addNewTableColumns();
        }

        // --- setCount: intentar con long, si no existe, con BigInteger ---
        try {
            // intentar método setCount(long)
            Method setCountLong = ctCols.getClass().getMethod("setCount", long.class);
            setCountLong.invoke(ctCols, (long) colCount);
        } catch (NoSuchMethodException nsme) {
            try {
                // intentar método setCount(BigInteger)
                Method setCountBI = ctCols.getClass().getMethod("setCount", BigInteger.class);
                setCountBI.invoke(ctCols, BigInteger.valueOf(colCount));
            } catch (Exception e) {
                throw new RuntimeException("No se pudo invocar setCount en CTTableColumns", e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error invocando setCount(long) en CTTableColumns", e);
        }

        // eliminar entradas existentes y recrearlas (más seguro que intentar editarlas)
        while (ctCols.sizeOfTableColumnArray() > 0) {
            ctCols.removeTableColumn(0);
        }

        // fila de encabezado (asumo que start.getRow() es la fila de headers)
        Row headerRow = sheet.getRow(start.getRow());
        for (int i = 0; i < colCount; i++) {
            org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableColumn newCol = ctCols.addNewTableColumn();

            // setId: intentar con long, si no existe, con BigInteger
            try {
                Method setIdLong = newCol.getClass().getMethod("setId", long.class);
                setIdLong.invoke(newCol, (long) (i + 1));
            } catch (NoSuchMethodException nsme) {
                try {
                    Method setIdBI = newCol.getClass().getMethod("setId", BigInteger.class);
                    setIdBI.invoke(newCol, BigInteger.valueOf(i + 1));
                } catch (Exception e) {
                    throw new RuntimeException("No se pudo invocar setId en CTTableColumn", e);
                }
            } catch (Exception e) {
                throw new RuntimeException("Error invocando setId(long) en CTTableColumn", e);
            }

            String headerName = null;
            if (headerRow != null) {
                Cell h = headerRow.getCell(startCol + i);
                if (h != null) {
                    if (h.getCellType() == CellType.STRING) {
                        headerName = h.getStringCellValue();
                    } else {
                        headerName = h.toString();
                    }
                }
            }
            if (headerName == null || headerName.isBlank()) {
                headerName = "Column" + (i + 1);
            }
            newCol.setName(headerName);
        }

    }

    private void clearRow(Row row) {
        for (Cell cell : row) {
            cell.setBlank();
        }
    }

    private void copyRow(Sheet sheet, Row src, Row dest) {
        for (int i = 0; i < src.getLastCellNum(); i++) {
            Cell oldCell = src.getCell(i);
            if (oldCell != null) {
                Cell newCell = dest.createCell(i);
                newCell.setCellStyle(oldCell.getCellStyle());

                switch (oldCell.getCellType()) {
                    case STRING:
                        newCell.setCellValue(oldCell.getStringCellValue());
                        break;
                    case NUMERIC:
                        newCell.setCellValue(oldCell.getNumericCellValue());
                        break;
                    case BOOLEAN:
                        newCell.setCellValue(oldCell.getBooleanCellValue());
                        break;
                    case FORMULA:
                        newCell.setCellFormula(oldCell.getCellFormula());
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private int findTotalsRow(Sheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING &&
                        cell.getStringCellValue().trim().equalsIgnoreCase(TOTALS_LABEL)) {
                    return row.getRowNum();
                }
            }
        }
        return -1;
    }

    private CellStyle getDateStyle(Workbook workbook) {
        CreationHelper creationHelper = workbook.getCreationHelper();
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("d/MM/yyyy"));
        return dateStyle;
    }

    private void writeDateCell(Row row, int colIndex, String dateStr, CellStyle style) {
        try {
            // convertir yyyy-MM-dd → java.util.Date
            java.util.Date date = java.sql.Date.valueOf(dateStr);

            Cell cell = row.createCell(colIndex);
            cell.setCellValue(date);

            if (style != null) {
                cell.setCellStyle(style);
            }

        } catch (Exception e) {
            // Si ocurre algo, escribe como texto
            Cell cell = row.createCell(colIndex);
            cell.setCellValue(dateStr);
        }
    }

}