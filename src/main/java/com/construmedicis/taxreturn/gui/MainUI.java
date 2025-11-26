package com.construmedicis.taxreturn.gui;

import com.construmedicis.taxreturn.TaxreturnApplication;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import com.construmedicis.taxreturn.utils.auth.GmailAuthService;
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

        // Obtener servicio de autenticación para saber si debemos mostrar vista de
        // espera
        GmailAuthService authService = springContext.getBean(GmailAuthService.class);

        java.util.concurrent.atomic.AtomicReference<Tab> authTabRef = new java.util.concurrent.atomic.AtomicReference<>();
        if (!authService.isAuthenticated()) {
            authTabRef.set(createAuthTab(stage, authService));
            tabPane.getTabs().add(authTabRef.get());
        }

        tabPane.getTabs().add(createDownloadTab(stage));
        tabPane.getTabs().add(createConversionTab(stage));

        // Usamos un StackPane para poder superponer un overlay de carga sobre la UI
        StackPane root = new StackPane(tabPane);
        Scene scene = new Scene(root, 650, 450);

        // Estilos básicos
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        stage.setTitle("📊 TaxReturn - Gestor de Facturas");
        stage.setScene(scene);

        // Ensure that closing the JavaFX window also stops the Spring context and exits
        // the JVM
        stage.setOnCloseRequest(ev -> {
            try {
                if (springContext != null) {
                    springContext.close();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                javafx.application.Platform.exit();
                System.exit(0);
            }
        });

        // Construir overlay de carga y añadir al root (inicialmente oculto)
        loadingOverlay = createLoadingOverlay();
        loadingOverlay.setVisible(false);
        loadingOverlay.setManaged(false);
        root.getChildren().add(loadingOverlay);

        stage.show();

        // Si había una pestaña de autenticación, arrancamos un pequeño polling para
        // actualizar su estado
        if (authTabRef.get() != null) {
            var executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(() -> {
                var status = authService.getStatus();
                javafx.application.Platform.runLater(() -> {
                    if (status != null) {
                        // buscar el label dentro del authTab para actualizar
                        var content = (javafx.scene.layout.VBox) authTabRef.get().getContent();
                        for (javafx.scene.Node node : content.getChildren()) {
                            if (node instanceof javafx.scene.control.Label && "authStatusLabel".equals(node.getId())) {
                                ((javafx.scene.control.Label) node).setText("Estado: " + status.name());
                            }
                            if (node instanceof javafx.scene.control.TextField && "authUrlField".equals(node.getId())) {
                                String lastUrl = authService.getLastAuthUrl();
                                ((javafx.scene.control.TextField) node).setText(lastUrl == null ? "" : lastUrl);
                            }
                        }

                        if (status == GmailAuthService.AuthStatus.AUTHENTICATED) {
                            // autenticado: simplemente remover la pestaña
                            tabPane.getTabs().remove(authTabRef.get());
                            executor.shutdown();
                        }
                    }
                });
            }, 0, 2, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Override
    public void stop() throws Exception {
        // This is called when JavaFX shuts down. Make sure we also stop Spring and the
        // JVM.
        try {
            if (springContext != null) {
                springContext.close();
            }
        } finally {
            super.stop();
            System.exit(0);
        }
    }

    private Tab createAuthTab(Stage stage, GmailAuthService authService) {
        VBox authPane = new VBox(12);
        authPane.setPadding(new Insets(20));

        Label lblTitulo = new Label("🔐 Autenticación de Gmail");
        lblTitulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label lblStatus = new Label("Estado: " + authService.getStatus().name());
        lblStatus.setId("authStatusLabel");

        Label lblInfo = new Label(
                "Si falta 'credentials.json' sube el archivo aquí o pulsa 'Iniciar autenticación' para abrir el navegador.");
        lblInfo.setWrapText(true);

        TextField txtAuthUrl = new TextField();
        txtAuthUrl.setEditable(false);
        txtAuthUrl.setId("authUrlField");
        txtAuthUrl.setPromptText("URL de autorización (se mostrará aquí si la apertura falla)");

        Button btnOpenManual = new Button("Abrir manualmente");
        btnOpenManual.setOnAction(ev -> {
            String url = authService.getLastAuthUrl();
            if (url != null && !url.isBlank()) {
                try {
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                    } else {
                        showAlert("Abrir URL", "Abra manualmente: " + url);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showAlert("Error", "No se pudo abrir la URL: " + ex.getMessage());
                }
            }
        });

        Button btnCopy = new Button("Copiar URL");
        btnCopy.setOnAction(ev -> {
            String url = authService.getLastAuthUrl();
            if (url != null && !url.isBlank()) {
                final javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                final javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(url);
                clipboard.setContent(content);
                showAlert("Copiado", "URL copiada al portapapeles.");
            }
        });

        Button btnUpload = new Button("Subir credentials.json");
        btnUpload.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Seleccionar credentials.json");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
            java.io.File file = fileChooser.showOpenDialog(stage);
            if (file != null) {
                try (var in = new java.io.FileInputStream(file)) {
                    authService.uploadCredentials(in);
                    lblStatus.setText("Estado: " + authService.getStatus().name());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showAlert("Error", "No se pudo subir credentials: " + ex.getMessage());
                }
            }
        });

        Button btnStart = new Button("Iniciar autenticación (abrir navegador)");
        btnStart.getStyleClass().add("primary-button");
        btnStart.setOnAction(e -> {
            // arrancar el auth en background
            authService.startAuthentication();

            // actualizamos estado inmediato (startAuthentication detectará si faltan
            // credenciales)
            var st = authService.getStatus();
            lblStatus.setText("Estado: " + st.name());

            if (st == GmailAuthService.AuthStatus.NOT_CONFIGURED) {
                showAlert("Faltan credenciales",
                        "No se encontró credentials.json. Por favor súbelo usando 'Subir credentials.json' o colócalo en la carpeta raíz del proyecto (credentials.json). También puedes configurar otra ruta en application.properties.");
            } else if (st == GmailAuthService.AuthStatus.PENDING && authService.getLastAuthUrl() != null) {
                showAlert("Autenticación",
                        "Se ha intentado abrir el navegador para el flujo de autenticación. Si no se abrió, copia la URL que aparece en la UI o en la consola.");
            } else {
                showAlert("Autenticación",
                        "Flujo de autenticación en curso. Revise la consola o el campo URL en la UI si necesita abrir manualmente.");
            }
        });

        TextField txtAuthCode = new TextField();
        txtAuthCode.setPromptText("Pega aquí el código de autorización (si tu navegador no puede callback)");

        Button btnFinalize = new Button("Finalizar autenticación (pegar código)");
        btnFinalize.setOnAction(ev -> {
            String code = txtAuthCode.getText();
            if (code == null || code.isBlank()) {
                showAlert("Error", "Introduce el código de autorización antes de finalizar.");
                return;
            }

            try {
                authService.completeAuthenticationWithCode(code.trim());
                lblStatus.setText("Estado: " + authService.getStatus().name());
                showAlert("Autenticación", "Autenticación completada correctamente.");
            } catch (Exception ex) {
                ex.printStackTrace();
                showAlert("Error", "No se pudo finalizar la autenticación: " + ex.getMessage());
            }
        });

        // Botón para abrir la guía en PDF incluida en el repositorio
        Button btnOpenPdf = new Button("Abrir guía PDF");
        btnOpenPdf.setOnAction(ev -> {
            try {
                java.nio.file.Path pdf = java.nio.file.Path.of("docs", "CREDENTIALS_SIMPLE.pdf");
                if (!java.nio.file.Files.exists(pdf)) {
                    showAlert("No disponible", "No se encontró el PDF en: " + pdf.toAbsolutePath());
                    return;
                }

                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(pdf.toFile());
                } else {
                    showAlert("Abrir PDF", "No se puede abrir el PDF automáticamente. Ruta: " + pdf.toAbsolutePath());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                showAlert("Error", "No se pudo abrir el PDF: " + ex.getMessage());
            }
        });

        authPane.getChildren().addAll(lblTitulo, new Separator(), lblStatus, lblInfo, new HBox(10, btnUpload, btnStart),
                txtAuthUrl, new HBox(10, btnOpenManual, btnCopy), txtAuthCode, btnFinalize, new Separator(),
                btnOpenPdf);

        Tab tab = new Tab("Autenticación", authPane);
        tab.setClosable(false);
        return tab;
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
        btnDescargar.setOnAction(e -> handleDownload(fechaInicio, fechaFin, txtEtiqueta, txtRutaDescarga, stage));

        VBox form = new VBox(10,
                createLabeledField("Fecha inicio:", fechaInicio),
                createLabeledField("Fecha fin:", fechaFin),
                createLabeledField("Etiqueta de correos:", txtEtiqueta),
                createLabeledField("Ruta de salida:", new HBox(10, txtRutaDescarga, btnExplorarDescarga)),
                btnDescargar);

        downloadPane.getChildren().addAll(lblTitulo, new Separator(), form);

        Tab tab = new Tab("Descargar Facturas", downloadPane);
        tab.setClosable(false);
        return tab;
    }

    private void handleDownload(DatePicker fechaInicio, DatePicker fechaFin, TextField txtEtiqueta,
            TextField txtRutaDescarga, Stage stage) {
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

            sendGetRequestAsync(stage, urlStr, "Facturas descargadas correctamente");
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
        btnConvertir.setOnAction(e -> handleConversion(txtRutaXML, txtPlantilla, stage));

        VBox form = new VBox(10,
                createLabeledField("Ruta XMLs:", new HBox(10, txtRutaXML, btnExplorarXML)),
                createLabeledField("Plantilla Excel:", new HBox(10, txtPlantilla, btnExplorarPlantilla)),
                btnConvertir);

        conversionPane.getChildren().addAll(lblTitulo, new Separator(), form);

        Tab tab = new Tab("Conversión", conversionPane);
        tab.setClosable(false);
        return tab;
    }

    private void handleConversion(TextField txtRutaXML, TextField txtPlantilla, Stage stage) {
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

            sendGetRequestAsync(stage, urlStr, "Conversión realizada correctamente");
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

    private void sendGetRequestAsync(Stage stage, String urlStr, String successMessage) {
        // Mostrar overlay
        showLoading();

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
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

                    String resp = response.toString();
                    javafx.application.Platform.runLater(() -> {
                        hideLoading();
                        showAlert("Éxito", successMessage + ":\n" + resp);
                    });
                } else {
                    javafx.application.Platform.runLater(() -> {
                        hideLoading();
                        showAlert("Error", "Error en la petición. Código HTTP: " + responseCode);
                    });
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    hideLoading();
                    showAlert("Error", "Ocurrió un problema: " + ex.getMessage());
                });
            }
        });
    }

    private VBox loadingOverlay;

    private VBox createLoadingOverlay() {
        VBox overlay = new VBox(10);
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.4); -fx-padding: 20px;");
        overlay.setAlignment(Pos.CENTER);

        ProgressIndicator pi = new ProgressIndicator();
        Label lbl = new Label("Procesando, por favor espere...");
        lbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        overlay.getChildren().addAll(pi, lbl);
        overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        return overlay;
    }

    private void showLoading() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(true);
            loadingOverlay.setManaged(true);
        }
    }

    private void hideLoading() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(false);
            loadingOverlay.setManaged(false);
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