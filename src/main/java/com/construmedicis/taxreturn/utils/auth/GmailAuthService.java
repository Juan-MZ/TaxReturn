package com.construmedicis.taxreturn.utils.auth;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.services.gmail.GmailScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class GmailAuthService {

    public enum AuthStatus {
        NOT_CONFIGURED, PENDING, AUTHENTICATED, ERROR
    }

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);

    @Value("${gmail.credentials.file}")
    private String credentialsFilePath;

    @Value("${gmail.tokens.directory}")
    private String tokensDirectoryPath;

    @Value("${gmail.application.name}")
    private String applicationName;

    private final AtomicReference<AuthStatus> status = new AtomicReference<>(AuthStatus.NOT_CONFIGURED);
    private final AtomicReference<Gmail> gmailRef = new AtomicReference<>(null);
    // Guarda la última URL de autorización que se generó (útil para UI o fallback
    // manual)
    private final java.util.concurrent.atomic.AtomicReference<String> lastAuthUrl = new java.util.concurrent.atomic.AtomicReference<>(
            null);
    private final java.util.concurrent.atomic.AtomicReference<GoogleAuthorizationCodeFlow> lastFlowRef = new java.util.concurrent.atomic.AtomicReference<>(
            null);

    private ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "gmail-auth-thread"));

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // If credentials file is present, try to start a non-blocking auth attempt
        Path p = Paths.get(credentialsFilePath);
        if (Files.exists(p)) {
            // If we already have stored tokens and they are still valid/refreshable,
            // restore the saved credential and skip the interactive flow.
            try {
                boolean restored = tryRestoreStoredCredential();
                if (restored) {
                    System.out.println("GmailAuthService: restored credential from tokens, skipping authentication flow");
                    return;
                }
            } catch (Exception ex) {
                System.out.println("GmailAuthService: error while trying to restore stored credential: " + ex.getMessage());
            }

            startAuthentication();
        } else {
            status.set(AuthStatus.NOT_CONFIGURED);
        }
    }

    /**
     * Attempt to restore a stored credential from the tokens directory.
     * Returns true when a usable credential is loaded and gmail client is created.
     */
    private boolean tryRestoreStoredCredential() throws Exception {
        // We try two approaches:
        // 1) If credentials.json exists, create a flow and load the stored credential
        // using the FileDataStoreFactory (preferred — allows refreshing the token).
        // 2) If credentials.json is missing but there is a stored token file under
        //    tokens/StoredCredential/user with a still-valid access token, we can
        //    restore a lightweight credential (read-only) and use it to create the
        //    Gmail client (won't be able to refresh without client secrets).
        Path credPath = Paths.get(credentialsFilePath);

        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        try (Reader reader = new FileReader(credentialsFilePath)) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(Paths.get(tokensDirectoryPath).toFile()))
                    .setAccessType("offline")
                    .build();

            // Try to load a previously stored credential for the default "user"
            Credential credential = flow.loadCredential("user");
            if (credential == null) {
                // Continue: maybe tokens were saved under file system layout but for
                // some reason flow.loadCredential returned null — fall through to token
                // file heuristic below.
            }

            // If we don't have an access token but we have a refresh token, try to refresh
            boolean usable = false;
            if (credential != null) {
                if (credential.getAccessToken() != null) {
                // if there is an access token, consider it usable (we'll still try to
                // refresh if it's near expiry)
                usable = true;
            }

                Long expiresIn = credential.getExpiresInSeconds();
            if (expiresIn != null && expiresIn <= 60) {
                // near expiry — try to refresh using stored refresh token
                try {
                    boolean refreshed = credential.refreshToken();
                    if (refreshed) {
                        usable = true;
                    }
                } catch (Exception ex) {
                    // refresh failed — fall back to interactive flow
                    ex.printStackTrace();
                    usable = false;
                }
            }

                // If we had no expiry data but had a refresh token, try to refresh now
                if (!usable && credential.getRefreshToken() != null) {
                try {
                    boolean refreshed = credential.refreshToken();
                    if (refreshed) {
                        usable = true;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    usable = false;
                }
            }

                if (usable) {
                Gmail gmail = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                        .setApplicationName(applicationName)
                        .build();

                gmailRef.set(gmail);
                status.set(AuthStatus.AUTHENTICATED);
                // cleanup any pending flow or URL reference
                lastFlowRef.set(null);
                lastAuthUrl.set(null);
                return true;
            }
            }
        }

        // If we couldn't build/load a credential via Client Secrets (or the
        // credentials file is missing), try a lightweight restore directly from
        // the tokens directory (tokens/StoredCredential/user). This allows skipping
        // auth when an access token is still valid even without the credentials
        // file present.
        try {
            Path tokenFile = Paths.get(tokensDirectoryPath, "StoredCredential", "user");
            if (Files.exists(tokenFile)) {
                String content = Files.readString(tokenFile);

                // naive extraction of access_token and expiration_time_millis
                String accessToken = null;
                Long expirationMillis = null;

                java.util.regex.Matcher mTok = java.util.regex.Pattern.compile("\"access_token\"\s*:\s*\"([^\"]+)\"")
                        .matcher(content);
                if (mTok.find()) {
                    accessToken = mTok.group(1);
                }

                java.util.regex.Matcher mExp = java.util.regex.Pattern.compile("\"expiration_time_millis\"\s*:\s*(\\d+)")
                        .matcher(content);
                if (mExp.find()) {
                    try {
                        expirationMillis = Long.parseLong(mExp.group(1));
                    } catch (NumberFormatException ignore) {
                    }
                }

                boolean valid = false;
                if (accessToken != null) {
                    if (expirationMillis == null) {
                        // We don't have expiry info — assume token ok (best-effort)
                        valid = true;
                    } else {
                        long now = System.currentTimeMillis();
                        if (expirationMillis > now + 5000) { // at least 5s left
                            valid = true;
                        }
                    }
                }

                if (valid) {
                    Credential credential = new Credential(com.google.api.client.auth.oauth2.BearerToken.authorizationHeaderAccessMethod())
                            .setAccessToken(accessToken);

                    httpTransport = GoogleNetHttpTransport.newTrustedTransport();
                    Gmail gmail = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                            .setApplicationName(applicationName)
                            .build();

                    gmailRef.set(gmail);
                    status.set(AuthStatus.AUTHENTICATED);
                    lastFlowRef.set(null);
                    lastAuthUrl.set(null);
                    return true;
                }
            }
        } catch (Exception ex) {
            // ignore and return false
            ex.printStackTrace();
        }

        return false;
    }

    public AuthStatus getStatus() {
        return status.get();
    }

    public boolean isAuthenticated() {
        return getStatus() == AuthStatus.AUTHENTICATED && gmailRef.get() != null;
    }

    public Gmail getGmail() {
        return gmailRef.get();
    }

    public void startAuthentication() {
        // avoid starting twice
        if (status.get() == AuthStatus.PENDING)
            return;

        // Si no existe el archivo de credenciales, no intentamos iniciar el flujo
        Path p = Paths.get(credentialsFilePath);
        if (!Files.exists(p)) {
            System.out.println("GmailAuthService: credentials.json no encontrado en " + credentialsFilePath);
            status.set(AuthStatus.NOT_CONFIGURED);
            lastAuthUrl.set(null);
            return;
        }

        // If we already have stored credentials, try to restore them before
        // starting a new interactive flow (avoid opening browser unnecessarily)
        try {
            boolean restored = tryRestoreStoredCredential();
            if (restored) {
                System.out.println("GmailAuthService: restored credential from tokens in startAuthentication, skipping interactive flow");
                return;
            }
        } catch (Exception ex) {
            System.out.println("GmailAuthService: error while trying to restore stored credential in startAuthentication: " + ex.getMessage());
        }

        status.set(AuthStatus.PENDING);
        executor.submit(() -> {
            try {
                var httpTransport = GoogleNetHttpTransport.newTrustedTransport();

                try (Reader reader = new FileReader(credentialsFilePath)) {
                    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);

                    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                            httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                            .setDataStoreFactory(new FileDataStoreFactory(Paths.get(tokensDirectoryPath).toFile()))
                            .setAccessType("offline")
                            .build();

                    // Usaremos el servidor embebido (Tomcat en el puerto 8080) como receptor de
                    // callback.
                    // El cliente OAuth debe tener autorizado el redirect URI:
                    // http://localhost:8080/auth/callback
                    String redirectUri = "http://localhost:8080/auth/callback";

                    // Guardar flow para que el callback pueda completar la autorización
                    lastFlowRef.set(flow);

                    String authorizationUrl = flow.newAuthorizationUrl().setRedirectUri(redirectUri).build();
                    lastAuthUrl.set(authorizationUrl);

                    System.out.println("GmailAuthService: usando redirectUri -> " + redirectUri);
                    System.out.println("GmailAuthService: auth url -> " + authorizationUrl);

                    try {
                        if (java.awt.Desktop.isDesktopSupported()) {
                            java.awt.Desktop.getDesktop().browse(new java.net.URI(authorizationUrl));
                        } else {
                            System.out
                                    .println("GmailAuthService: Desktop.browse no soportado, URL: " + authorizationUrl);
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                        System.out.println("GmailAuthService: intenta abrir manualmente: " + authorizationUrl);
                    }

                    // dejamos que el callback HTTP (controlador Spring) complete el intercambio
                    status.set(AuthStatus.PENDING);
                }

            } catch (FileNotFoundException fnf) {
                // No credentials file yet
                status.set(AuthStatus.NOT_CONFIGURED);
            } catch (GeneralSecurityException | IOException ex) {
                ex.printStackTrace();
                status.set(AuthStatus.ERROR);
            } catch (Exception ex) {
                ex.printStackTrace();
                status.set(AuthStatus.ERROR);
            }
        });
    }

    public void uploadCredentials(InputStream credentialsStream) throws IOException {
        Path target = Paths.get(credentialsFilePath);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(credentialsStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // After uploading, start auth
        startAuthentication();
    }

    public String getLastAuthUrl() {
        return lastAuthUrl.get();
    }

    /**
     * Completa el flujo de autenticación a partir del código que el usuario pegó
     * manualmente.
     */
    public void completeAuthenticationWithCode(String code) throws Exception {
        GoogleAuthorizationCodeFlow flow = lastFlowRef.get();
        if (flow == null) {
            throw new IllegalStateException(
                    "No hay flujo de autorización disponible. Inicie la autenticación primero.");
        }

        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        TokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri("urn:ietf:wg:oauth:2.0:oob")
                .execute();

        Credential credential = flow.createAndStoreCredential(tokenResponse, "user");

        Gmail gmail = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();

        gmailRef.set(gmail);
        status.set(AuthStatus.AUTHENTICATED);

        // limpieza
        lastFlowRef.set(null);
        lastAuthUrl.set(null);
    }

    /**
     * Completa el flujo cuando Google redirige a /auth/callback con el code.
     */
    public void handleCallback(String code) throws Exception {
        GoogleAuthorizationCodeFlow flow = lastFlowRef.get();
        if (flow == null) {
            throw new IllegalStateException(
                    "No hay flujo de autorización disponible. Inicie la autenticación primero.");
        }

        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        TokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri("http://localhost:8080/auth/callback")
                .execute();

        Credential credential = flow.createAndStoreCredential(tokenResponse, "user");

        Gmail gmail = new Gmail.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();

        gmailRef.set(gmail);
        status.set(AuthStatus.AUTHENTICATED);

        // limpieza
        lastFlowRef.set(null);
        lastAuthUrl.set(null);
    }

}
