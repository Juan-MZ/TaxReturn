package com.construmedicis.taxreturn.utils.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GmailConfig {

    @Value("${gmail.credentials.file}")
    private String credentialsFilePath;

    @Value("${gmail.tokens.directory}")
    private String tokensDirectoryPath;

    @Value("${gmail.application.name}")
    private String applicationName;

    // Nota: La inicialización y el flujo de OAuth se gestionan ahora
    // en GmailAuthService para que la aplicación no falle al arrancar
    // si falta el archivo de credenciales. Mantengo la configuración
    // de propiedades en esta clase por compatibilidad.

    public String getCredentialsFilePath() {
        return credentialsFilePath;
    }

    public String getTokensDirectoryPath() {
        return tokensDirectoryPath;
    }

    public String getApplicationName() {
        return applicationName;
    }
}
