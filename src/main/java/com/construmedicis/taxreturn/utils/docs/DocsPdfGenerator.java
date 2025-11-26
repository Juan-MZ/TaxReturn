package com.construmedicis.taxreturn.utils.docs;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class DocsPdfGenerator {

    private static final String OUTPUT = "CREDENTIALS_SIMPLE.pdf";

    @EventListener(ApplicationReadyEvent.class)
    public void generateIfMissing() {
        try {
            Path out = Paths.get(OUTPUT).toAbsolutePath();
            if (Files.exists(out)) {
                System.out.println("DocsPdfGenerator: PDF already exists at " + out);
                return;
            }

            Files.createDirectories(out.getParent() == null ? Paths.get(".") : out.getParent());

            try (PDDocument doc = new PDDocument()) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                    cs.newLineAtOffset(60, 700);
                    cs.showText("Guía rápida para obtener credentials.json");
                    cs.endText();

                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 11);
                    cs.newLineAtOffset(60, 670);

                    String[] lines = new String[]{
                            "Paso 1: Abre https://console.cloud.google.com/ y crea o selecciona un proyecto.",
                            "Paso 2: En 'APIs y Servicios' -> Biblioteca, habilita 'Gmail API'.",
                            "Paso 3: En 'Pantalla de consentimiento OAuth' configura el nombre y añade tu email como usuario de prueba.",
                            "Paso 4: En 'Credenciales' -> 'Crear credenciales' -> 'ID de cliente OAuth' crea un cliente de tipo 'Web application'.",
                            "Paso 5: Añade el redirect URI exacto: http://localhost:8080/auth/callback . Guarda y descarga credentials.json.",
                            "Paso 6: Copia credentials.json a la carpeta del proyecto (donde ejecutas la app).",
                            "Paso 7: Ejecuta la app y usa la pestaña 'Autenticación' para completar el login en Google.",
                            "Consejo: No subas credentials.json a repositorios públicos."
                    };

                    for (String line : lines) {
                        cs.showText(line);
                        cs.newLineAtOffset(0, -14);
                    }
                    cs.endText();
                }

                doc.save(out.toFile());
            }

            System.out.println("DocsPdfGenerator: created " + out.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("DocsPdfGenerator: error generating PDF: " + e.getMessage());
        }
    }
}
