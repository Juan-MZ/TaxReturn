package com.construmedicis.taxreturn.utils.configs;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

@Configuration
public class GmailConfig {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Value("${gmail.credentials.file}")
    private String credentialsFilePath;

    @Value("${gmail.tokens.directory}")
    private String tokensDirectoryPath;

    @Value("${gmail.application.name}")
    private String applicationName;

    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);

    @Bean
    public Gmail gmailService() throws Exception {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = getCredentials(httpTransport);

        return new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();
    }

    private Credential getCredentials(final com.google.api.client.http.HttpTransport httpTransport) throws Exception {
        // Cargar credenciales del archivo JSON (descargado desde Google Cloud Console)
        try (Reader reader = new FileReader(credentialsFilePath)) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);

            // Flujo de autorización
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(Paths.get(tokensDirectoryPath).toFile()))
                    .setAccessType("offline")
                    .build();

            // Servidor local para manejar el callback de OAuth2
            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

            // Usar navegador del sistema directamente
            AuthorizationCodeInstalledApp app = new AuthorizationCodeInstalledApp(flow, receiver, new AuthorizationCodeInstalledApp.DefaultBrowser());

            return app.authorize("user");
        }
    }
}
