package com.construmedicis.taxreturn.gui;

import com.construmedicis.taxreturn.TaxreturnApplication;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

public class MainUI extends Application {

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        springContext = new SpringApplicationBuilder(TaxreturnApplication.class).run();
    }

    @Override
    public void start(Stage stage) {
        TabPane tabPane = new TabPane();

        tabPane.getTabs().add(createDownloadTab(stage));
        tabPane.getTabs().add(createConversionTab(stage));

        Scene scene = new Scene(tabPane, 650, 450);

        // Estilos básicos
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        stage.setTitle("📊 TaxReturn - Gestor de Facturas");
        stage.setScene(scene);
        stage.show();
    }

    // ========================
    // TAB 1: Descarga Facturas
    // ========================
    private Tab createDownloadTab(Stage stage) {
        VBox downloadPane = new VBox(15);
        downloadPane.setPadding(new Insets(20));

        Label lblTitulo = new Label("📥 Descargar Facturas");
        lblTitulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        DatePicker fechaInicio = new DatePicker();
        DatePicker fechaFin = new DatePicker();
        TextField txtEtiqueta = new TextField();
        TextField txtRutaDescarga = new TextField();
        Button btnExplorarDescarga = new Button("Seleccionar carpeta");

        btnExplorarDescarga.setOnAction(e -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Seleccionar carpeta de descarga");
            java.io.File dir = directoryChooser.showDialog(stage);
            if (dir != null) {
                txtRutaDescarga.setText(dir.getAbsolutePath());
            }
        });

        Button btnDescargar = new Button("🚀 Descargar Facturas");
        btnDescargar.getStyleClass().add("primary-button");
        btnDescargar.setOnAction(e -> handleDownload(fechaInicio, fechaFin, txtEtiqueta, txtRutaDescarga));

        VBox form = new VBox(10,
                createLabeledField("Fecha inicio:", fechaInicio),
                createLabeledField("Fecha fin:", fechaFin),
                createLabeledField("Etiqueta de correos:", txtEtiqueta),
                createLabeledField("Ruta de salida:", new HBox(10, txtRutaDescarga, btnExplorarDescarga)),
                btnDescargar
        );

        downloadPane.getChildren().addAll(lblTitulo, new Separator(), form);

        Tab tab = new Tab("Descargar Facturas", downloadPane);
        tab.setClosable(false);
        return tab;
    }

    private void handleDownload(DatePicker fechaInicio, DatePicker fechaFin, TextField txtEtiqueta, TextField txtRutaDescarga) {
        try {
            if (fechaInicio.getValue() == null || fechaFin.getValue() == null ||
                    txtEtiqueta.getText().isEmpty() || txtRutaDescarga.getText().isEmpty()) {
                showAlert("Error", "Debe completar todos los campos antes de descargar.");
                return;
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
            String fechaIni = fechaInicio.getValue().format(formatter);
            String fechaFinal = fechaFin.getValue().format(formatter);
            String etiqueta = txtEtiqueta.getText();
            String rutaSalida = txtRutaDescarga.getText();

            String query = "label:" + etiqueta + " after:" + fechaIni + " before:" + fechaFinal;
            String queryEncoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String outputDirEncoded = URLEncoder.encode(rutaSalida, StandardCharsets.UTF_8);

            String urlStr = "http://localhost:8080/extraction/extractInvoices?query="
                    + queryEncoded + "&outputDir=" + outputDirEncoded;

            sendGetRequest(urlStr, "Facturas descargadas correctamente");
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Error", "Ocurrió un problema: " + ex.getMessage());
        }
    }

    // ========================
    // TAB 2: Conversión
    // ========================
    private Tab createConversionTab(Stage stage) {
        VBox conversionPane = new VBox(15);
        conversionPane.setPadding(new Insets(20));

        Label lblTitulo = new Label("🔄 Conversión a Excel");
        lblTitulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        TextField txtRutaXML = new TextField();
        Button btnExplorarXML = new Button("Seleccionar carpeta");

        btnExplorarXML.setOnAction(e -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Seleccionar carpeta de XMLs");
            java.io.File dir = directoryChooser.showDialog(stage);
            if (dir != null) {
                txtRutaXML.setText(dir.getAbsolutePath());
            }
        });

        TextField txtPlantilla = new TextField();
        Button btnExplorarPlantilla = new Button("Seleccionar archivo");

        btnExplorarPlantilla.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Seleccionar plantilla Excel");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivos Excel", "*.xlsx"));
            java.io.File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                txtPlantilla.setText(file.getAbsolutePath());
            }
        });

        Button btnConvertir = new Button("⚙️ Convertir a Excel");
        btnConvertir.getStyleClass().add("primary-button");
        btnConvertir.setOnAction(e -> handleConversion(txtRutaXML, txtPlantilla));

        VBox form = new VBox(10,
                createLabeledField("Ruta XMLs:", new HBox(10, txtRutaXML, btnExplorarXML)),
                createLabeledField("Plantilla Excel:", new HBox(10, txtPlantilla, btnExplorarPlantilla)),
                btnConvertir
        );

        conversionPane.getChildren().addAll(lblTitulo, new Separator(), form);

        Tab tab = new Tab("Conversión", conversionPane);
        tab.setClosable(false);
        return tab;
    }

    private void handleConversion(TextField txtRutaXML, TextField txtPlantilla) {
        try {
            if (txtRutaXML.getText().isEmpty() || txtPlantilla.getText().isEmpty()) {
                showAlert("Error", "Debe seleccionar la carpeta de XMLs y la plantilla Excel.");
                return;
            }

            String xmlDir = txtRutaXML.getText();
            String plantilla = txtPlantilla.getText();

            String xmlDirEncoded = URLEncoder.encode(xmlDir, StandardCharsets.UTF_8);
            String plantillaEncoded = URLEncoder.encode(plantilla, StandardCharsets.UTF_8);

            String urlStr = "http://localhost:8080/conversion/convertInvoices"
                    + "?xmlDirectoryPath=" + xmlDirEncoded
                    + "&outputExcelPath=" + plantillaEncoded;

            sendGetRequest(urlStr, "Conversión realizada correctamente");
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Error", "Ocurrió un problema: " + ex.getMessage());
        }
    }

    // ========================
    // UTILIDADES
    // ========================
    private HBox createLabeledField(String label, javafx.scene.Node field) {
        VBox container = new VBox(5);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-weight: bold;");
        container.getChildren().addAll(lbl, field);
        return new HBox(container);
    }

    private void sendGetRequest(String urlStr, String successMessage) throws Exception {
        System.out.println("Ejecutando request: " + urlStr);

        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        int responseCode = con.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            showAlert("Éxito", successMessage + ":\n" + response);
        } else {
            showAlert("Error", "Error en la petición. Código HTTP: " + responseCode);
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}