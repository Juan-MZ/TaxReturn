package com.construmedicis.taxreturn.utils.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuthController {

    private final GmailAuthService gmailAuthService;

    public AuthController(GmailAuthService gmailAuthService) {
        this.gmailAuthService = gmailAuthService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(gmailAuthService.getStatus().name());
    }

    @GetMapping("/url")
    public ResponseEntity<?> url() {
        String last = gmailAuthService.getLastAuthUrl();
        if (last == null)
            return ResponseEntity.noContent().build();
        return ResponseEntity.ok(last);
    }

    @PostMapping("/start")
    public ResponseEntity<?> start() {
        gmailAuthService.startAuthentication();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/uploadCredentials")
    public ResponseEntity<?> uploadCredentials(@RequestParam("file") MultipartFile file) {
        try {
            gmailAuthService.uploadCredentials(file.getInputStream());
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @PostMapping("/finish")
    public ResponseEntity<?> finish(@RequestParam("code") String code) {
        try {
            gmailAuthService.completeAuthenticationWithCode(code);
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    /**
     * Endpoint que Google redirige tras el login. Recibe el 'code' y completa el
     * flujo.
     */
    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error) {
        if (error != null) {
            return ResponseEntity.badRequest().body("Autenticación fallida: " + error);
        }

        if (code == null) {
            return ResponseEntity.badRequest().body("Falta el parámetro 'code' en la callback.");
        }

        try {
            gmailAuthService.handleCallback(code);
            String html = "<html><body><h3>Autenticación completada. Puedes cerrar esta ventana y volver a la aplicación.</h3></body></html>";
            return ResponseEntity.ok().header("Content-Type", "text/html").body(html);
        } catch (Exception ex) {
            ex.printStackTrace();
            String html = "<html><body><h3>Error al completar la autenticación: " + ex.getMessage()
                    + "</h3></body></html>";
            return ResponseEntity.status(500).header("Content-Type", "text/html").body(html);
        }
    }
}
