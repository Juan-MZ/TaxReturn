# Guía detallada — Cómo obtener credentials.json (Gmail API)

Este documento explica paso a paso cómo crear el OAuth client en Google Cloud Console y descargar el `credentials.json` que tu aplicación TaxReturn necesita para autenticarse con la API de Gmail.

IMPORTANTE: Por seguridad, NUNCA subas `credentials.json` a un repositorio público. Manténlo privado y añádelo a `.gitignore`.

PRE: asumimos que quieres que la app use el redirect URI http://localhost:8080/auth/callback (este proyecto lo usa por defecto).

---

## 1) Crear o seleccionar un proyecto en Google Cloud Console

1. Abre https://console.cloud.google.com/
2. En el selector de proyectos (arriba a la izquierda), crea un nuevo proyecto o selecciona uno existente.

Consejo: usa un nombre que puedas reconocer como "TaxReturn-local" si es para pruebas.

## 2) Activar la API de Gmail

1. En el menú lateral, ve a "APIs y Servicios" → "Biblioteca".
2. En la búsqueda escribe "Gmail API" y selecciónala.
3. Haz clic en "Habilitar".

## 3) Configurar pantalla de consentimiento OAuth (OAuth consent screen)

1. Ve a "APIs y Servicios" → "Pantalla de consentimiento OAuth".
2. Selecciona "Externos" (si la app es de desarrollo local y sólo para cuentas de prueba) y pulsa "Crear".
3. Completa los campos obligatorios:
   - Nombre de la app: p. ej. "TaxReturn Local"
   - Email de contacto
   - Añade un logo si quieres (opcional)
4. Scopes: añade el scope que necesitarás. Para este proyecto al menos:
   - https://www.googleapis.com/auth/gmail.readonly
5. Usuarios de prueba (si la app está en modo de prueba): añade tu cuenta Gmail (la que usarás para probar). Si dejas la app en Producción necesitarás cumplir verificación para algunos scopes.
6. Guarda / continua.

Nota: Si tu cuenta no es de una organización y sólo vas a probar la app localmente, con poner el usuario de prueba es suficiente.

## 4) Crear las credenciales (OAuth 2.0 Client ID)

1. Ve a "APIs y servicios" → "Credenciales" → "Crear credenciales" → "ID de cliente OAuth".
2. Tipo de aplicación: elige **Web application** (importante).
3. Nombre: p. ej. "TaxReturn - Local web".
4. En **Authorized redirect URIs** añade la URI donde tu app recibirá el callback. Añade exactamente (case sensitive):
   - http://localhost:8080/auth/callback
   - (Opcionalmente añade rutas adicionales si tu entorno requiere otras: `http://localhost:8080/Callback`, `http://localhost:56955/Callback`, etc.)
5. En **Authorized JavaScript origins** no es necesario para esta app que usa server-side redirect.
6. Haz clic en "Crear".

Al crear verás los detalles del **Client ID** y **Client secret**. Pulsa en **Descargar** (botón de la página) para bajar el `credentials.json`.

## 5) Colocar `credentials.json` en el proyecto

1. Sitúa el archivo `credentials.json` descargado en la raíz del proyecto (por defecto esta app busca `credentials.json` en la carpeta del proyecto — por ejemplo `C:\Users\juanj\develop\repositorios\TaxReturn\credentials.json`).
2. Alternativa: puedes dejarlo en otra ruta y ajustar la propiedad `gmail.credentials.file` en `src/main/resources/application.properties` a la ruta completa o relativa que prefieras.

Ejemplo (por defecto en este proyecto):
```
gmail.credentials.file=credentials.json
gmail.tokens.directory=tokens
```

3. No te olvides de mantener `credentials.json` fuera del control de versiones (el repo ya contiene `credentials.json` en `.gitignore`).

## 6) Probar el flujo

1. Arranca la app: `./mvnw spring-boot:run` o ejecuta desde tu IDE.
2. Abre la pestaña "Autenticación" en la UI.
3. Pulsa "Iniciar autenticación" — la app abrirá el navegador con la URL de autorización (si no lo hace, copia la URL de la UI o consola y pégala en el navegador).
4. Completa la autorización en Google; Google redirigirá a `http://localhost:8080/auth/callback` con un `code`.
5. La app procesará el callback y guardará los tokens en la carpeta `tokens` (o la que tengas en `gmail.tokens.directory`).

## 7) Problemas frecuentes y soluciones

- redirect_uri_mismatch: significa que el redirect URI que usó la app (p. ej. http://localhost:8080/auth/callback) *no* está exactamente registrado en tu OAuth client. Asegúrate que el URI en Google Cloud (Authorized redirect URIs) coincida exactamente.
- Address already in use / BindException: significa que la app intentó abrir un receptor local en un puerto ocupado. Para la configuración recomendada no necesitas puertos aleatorios — usa el redirect URI del servidor `http://localhost:8080/auth/callback`.
- invalid_grant / Malformed auth code: pasa cuando el code se emitió para un redirect URI distinto; arregla el redirect_uri_mismatch y vuelve a generar un código nuevo.
- Desktop.browse no abre navegador: abre manualmente la URL que verás en la consola / UI.

## 8) Consideraciones de seguridad

- credentials.json contiene el client_secret: mantenlo privado.
- No lo añadas a repositorios públicos.
- En producción, rota las credenciales si sospechas que las has expuesto.

---

Si quieres, puedo añadir a este repo un script `./scripts/setup-gcloud.sh` con pasos para automatizar algunos pasos (por ejemplo, validar si existe `credentials.json`, crear folders `tokens/`, etc.) o una versión traducida en inglés. ¿Quieres que lo añada como archivo en `docs/` también? 
