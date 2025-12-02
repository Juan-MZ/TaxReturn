# 📊 TaxReturn - Sistema de Gestión de Retenciones

Aplicación de escritorio desarrollada en **Spring Boot** y **JavaFX** que automatiza la extracción de facturas electrónicas desde **Gmail** y su procesamiento para cálculo de retenciones fiscales (ReteFuente, ReteICA) en una plantilla de Excel.

---

## 🚀 Características

### 📧 Extracción de Facturas
- 🔎 Filtrado inteligente de correos usando queries de Gmail (ejemplo: `label:facturas after:2025/08/01 before:2025/08/31`)
- 📥 Descarga automática de adjuntos XML de facturas electrónicas colombianas
- 📂 Extracción automática de archivos `.zip` en subcarpeta `extracted/`
- 🔐 Autenticación OAuth2 con restauración automática de tokens (sin reautenticación repetida)

### 💼 Procesamiento de Retenciones
- 📊 Conversión automática de facturas XML a Excel
- 🧮 Cálculo de retenciones (ReteFuente, ReteICA) según normativa colombiana
- 📋 Inserción de datos en tabla Excel "RETENCIONES 2025" sin sobreescribir datos previos
- 🎨 Preservación de formatos, fórmulas y estilos de tabla
- ✅ Detección y preservación automática de fila de totales

### 🖥️ Interfaz Gráfica
- 🎯 Interfaz JavaFX intuitiva con pestañas para descarga y conversión
- ⏳ Indicadores de progreso con overlay de carga
- 🔄 Comunicación asíncrona con backend REST

---

## 📦 Requisitos

- **Java 21** o superior
- **Maven 3.6+**
- **Cuenta de Google Cloud** con:
  - API de Gmail habilitada
  - Credenciales OAuth2 configuradas (`credentials.json`)
- **Plantilla Excel**: `calculadora_de_retenciones.xlsx` con hoja "RETENCIONES 2025"

---

## 🔧 Instalación

1. Clona este repositorio:
   ```bash
   git clone https://github.com/Juan-MZ/TaxReturn.git
   cd TaxReturn
   ```

2. Compila el proyecto con Maven:
   ```bash
   ./mvnw clean package
   ```

3. Configura las credenciales de Gmail:
   - Descarga el archivo `credentials.json` desde Google Cloud Console
   - Colócalo en la raíz del proyecto o en el directorio configurado
   - En el primer uso, el navegador se abrirá para autenticar tu cuenta
   - El token se guardará en `tokens/StoredCredential/user` para futuros usos

4. Prepara la plantilla Excel:
   - Asegúrate de tener el archivo `calculadora_de_retenciones.xlsx`
   - Debe contener una hoja llamada "RETENCIONES 2025" con una tabla estructurada
   - La tabla debe tener columnas para: Razón Social, NIT, Fecha, Número Factura, Valor Total, Base Imponible, Concepto, Tipo, ReteICA, ReteFuente

---

## ▶️ Uso

### Iniciar la Aplicación

```bash
./mvnw spring-boot:run
```

La interfaz gráfica JavaFX se abrirá automáticamente junto con el servidor Spring Boot en `http://localhost:8080`.

### Descargar Facturas

1. Navega a la pestaña **"Descargar"** en la interfaz
2. Ingresa el query de Gmail (ejemplo: `label:facturas after:2025/11/01`)
3. Selecciona la carpeta de destino
4. Haz clic en **"Descargar"**
5. Las facturas XML se descargarán y los archivos ZIP se extraerán automáticamente

### Convertir Facturas a Excel

1. Navega a la pestaña **"Conversión"**
2. Selecciona la carpeta con los archivos XML de facturas
3. La aplicación procesará cada factura y:
   - Extraerá datos del XML (Razón Social, NIT, Fecha, Valor, etc.)
   - Insertará una nueva fila en la tabla "RETENCIONES 2025"
   - Copiará formatos y fórmulas de la fila anterior
   - Preservará la fila de totales al final de la tabla
4. El archivo Excel se actualizará con los nuevos datos

---

## 🏗️ Arquitectura

### Tecnologías Principales

- **Spring Boot 3.5.5**: Framework backend con servidor embebido Tomcat
- **JavaFX 21.0.2**: Interfaz gráfica de escritorio
- **Apache POI 5.4.1**: Manipulación de archivos Excel (XSSFWorkbook, XSSFTable)
- **Google API Client 2.7.0**: Integración con Gmail API y OAuth2
- **Jackson XML 2.17.2**: Parsing de facturas electrónicas XML colombianas

### Estructura de Paquetes

```
com.construmedicis.taxreturn
├── TaxreturnApplication.java          # Punto de entrada Spring Boot
├── conversion/
│   ├── controller/
│   │   └── ConversionController.java  # Endpoints REST para conversión
│   └── services/
│       ├── ConversionService.java     # Lógica de procesamiento XML→Excel
│       └── IConversionService.java
├── extraction/
│   ├── controller/
│   │   └── ExtractionController.java  # Endpoints REST para descarga
│   └── services/
│       ├── ExtractionService.java     # Lógica de descarga desde Gmail
│       └── IExtractionService.java
├── gui/
│   └── MainUI.java                    # Interfaz JavaFX principal
└── utils/
    └── configs/
        └── GmailConfig.java           # Configuración OAuth2 y Gmail API
```

---

## 🔑 Características Técnicas

### Autenticación OAuth2
- Flujo de autorización con `GoogleAuthorizationCodeFlow`
- Almacenamiento persistente de tokens con `FileDataStoreFactory`
- Restauración automática de credenciales guardadas
- Refresh automático de tokens expirados sin reautenticación

### Procesamiento de Excel
- Detección automática de tablas Excel (`XSSFTable`)
- Inserción de filas preservando estructura de tabla
- Copia inteligente de estilos y fórmulas (`CellStyle`, `CellFormula`)
- Detección de fila de totales (busca palabras clave: "TOTALES", "TOTAL", "SUMA")
- Expansión segura de área de tabla con validación de referencias
- Preservación de filtros automáticos y estilos de tabla

### Parsing de Facturas XML
- Soporte para formato de factura electrónica colombiana
- Extracción de datos desde CDATA con XML anidado
- Mapeo de campos: `<Factura>`, `<Emisor>`, `<Receptor>`, `<DetallesFactura>`

---

## 🧪 Testing

Ejecutar tests unitarios:

```bash
./mvnw test
```

Compilar sin ejecutar tests:

```bash
./mvnw -DskipTests package
```

---

## 📝 Notas de Desarrollo

### Mejoras Recientes

- ✅ Restauración automática de tokens OAuth2 (evita reautenticación en cada ejecución)
- ✅ Corrección de inserción en tabla Excel (añade datos sin sobreescribir totales)
- ✅ Preservación de formatos, fórmulas y estructura de tabla
- ✅ Validación de referencias de área de tabla (mínimo 3 filas)
- ✅ Detección de fila de totales de abajo hacia arriba (evita falsos positivos en encabezados)

### Trabajo en Progreso

- 🔄 Refactorización de `expandTable()` para usar API pública `XSSFTable.setArea()`
- 🔄 Eliminación de manipulación directa de CTTable XML
- 🔄 Pruebas adicionales para prevenir corrupción de archivos Excel

---

## 📄 Licencia

Este proyecto es de código abierto. Consulta el archivo LICENSE para más detalles.

---

## 👤 Autor

**Juan-MZ**

- GitHub: [@Juan-MZ](https://github.com/Juan-MZ)
- Proyecto: [TaxReturn](https://github.com/Juan-MZ/TaxReturn)

---

## 🤝 Contribuciones

Las contribuciones son bienvenidas. Por favor:

1. Haz fork del proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

---

## 📞 Soporte

Si encuentras algún problema o tienes sugerencias, por favor abre un [issue](https://github.com/Juan-MZ/TaxReturn/issues) en GitHub.
