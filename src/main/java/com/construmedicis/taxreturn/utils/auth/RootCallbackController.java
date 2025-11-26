package com.construmedicis.taxreturn.utils.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class RootCallbackController {

    private final GmailAuthService gmailAuthService;

    public RootCallbackController(GmailAuthService gmailAuthService) {
        this.gmailAuthService = gmailAuthService;
    }

    // Aceptar /Callback y /callback at root (algunas librerías de OAuth usan
    // /Callback con mayúscula)
    @GetMapping({ "/Callback", "/callback" })
    public ResponseEntity<String> rootCallback(@RequestParam(value = "code", required = false) String code,
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
