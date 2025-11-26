Guía rápida para usuarios — Obtener credentials.json (Gmail)

1) Accede a Google Cloud Console:
   - Abre: https://console.cloud.google.com/

2) Crea o selecciona un proyecto:
   - Arriba a la izquierda usa el selector de proyectos. Puedes crear uno nuevo llamado "TaxReturn-local" si quieres.

3) Habilita la API de Gmail:
   - Ve a "APIs y servicios" → "Biblioteca" → busca "Gmail API" → haz clic en "Habilitar".

4) Configura la pantalla de consentimiento (rápido):
   - Ve a "Pantalla de consentimiento OAuth" y sigue los pasos básicos: nombre de la app y tu email. Añade tu cuenta como usuario de prueba si lo pide.

5) Crea credenciales (OAuth):
   - Ve a "Credenciales" → "Crear credenciales" → "ID de cliente OAuth".
   - Tipo: selecciona "Web application".
   - Añade como "Authorized redirect URIs" exactamente: http://localhost:8080/auth/callback
   - Guarda y descarga el archivo `credentials.json`.

6) Coloca `credentials.json` en la raíz del proyecto (donde ejecutas la app, por ejemplo `C:\Users\tuusuario\repos\TaxReturn\credentials.json`).

7) Inicia la app y sigue los pasos de la pestaña "Autenticación". La app abrirá el navegador para autorizar la cuenta.

Consejos:
- No compartas `credentials.json`. Está fuera del control del código (ponlo en .gitignore si no está ya).
- Si el navegador no abre automáticamente, copia la URL que aparece en la UI o consola y pégala en tu navegador.
