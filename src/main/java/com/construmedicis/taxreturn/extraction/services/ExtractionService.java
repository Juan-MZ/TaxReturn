package com.construmedicis.taxreturn.extraction.services;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class ExtractionService implements IExctractionService {

    private final Gmail gmail;

    // Inyecta la instancia de Gmail (configurada con OAuth2 en otra clase)
    public ExtractionService(Gmail gmail) {
        this.gmail = gmail;
    }

    @Override
    public void extractInvoices(String userId, String query, String outputDir) throws Exception {
        // Buscar mensajes con la query (ej: "label:facturas after:2025/08/01 before:2025/08/31")
        ListMessagesResponse response = gmail.users().messages().list(userId)
                .setQ(query)
                .execute();

        if (response.getMessages() == null) {
            System.out.println("No se encontraron mensajes.");
            return;
        }

        for (Message msgRef : response.getMessages()) {
            Message message = gmail.users().messages().get(userId, msgRef.getId()).execute();

            if (message.getPayload().getParts() != null) {
                for (MessagePart part : message.getPayload().getParts()) {
                    if (part.getFilename() != null && !part.getFilename().isEmpty()) {
                        // Procesar el adjunto
                        saveAttachment(userId, message.getId(), part, outputDir);
                    }
                }
            }
        }
    }

    private void saveAttachment(String userId, String messageId, MessagePart part, String outputDir) throws Exception {
        String filename = part.getFilename();
        String attId = part.getBody().getAttachmentId();
        MessagePartBody attachPart = gmail.users().messages().attachments()
                .get(userId, messageId, attId)
                .execute();

        byte[] fileBytes = Base64.getUrlDecoder().decode(attachPart.getData());
        Path filePath = Path.of(outputDir, filename);
        Files.write(filePath, fileBytes);

        System.out.println("Guardado: " + filePath);

        // Si es un ZIP, lo descomprimimos en una subcarpeta llamada "extracted"
        if (filename.toLowerCase().endsWith(".zip")) {
            Path extractedDir = Path.of(outputDir, "extracted");
            Files.createDirectories(extractedDir); // crea la carpeta si no existe
            unzipFile(filePath, extractedDir);
        }
    }

    private void unzipFile(Path zipPath, Path extractedDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                File outFile = extractedDir.resolve(entry.getName()).toFile();

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                    continue;
                }

                // Crear carpetas intermedias si es necesario
                outFile.getParentFile().mkdirs();

                try (InputStream in = zipFile.getInputStream(entry);
                     OutputStream out = new FileOutputStream(outFile)) {
                    in.transferTo(out);
                }
                System.out.println("Extraído: " + outFile.getAbsolutePath());
            }
        }
    }
}
