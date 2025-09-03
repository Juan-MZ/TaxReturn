# 📂 Gmail Invoice Extractor

Este proyecto es un servicio en **Spring Boot** que permite extraer facturas recibidas en **Gmail**, descargarlas en una carpeta local y, en caso de que los adjuntos estén comprimidos en `.zip`, los descomprime automáticamente en una subcarpeta.

---

## 🚀 Características

- 🔎 Filtrado de correos usando queries de Gmail (ejemplo: `label:facturas after:2025/08/01 before:2025/08/31`).
- 📥 Descarga automática de adjuntos.
- 📂 Extracción automática de archivos `.zip` en una subcarpeta `extracted/`.
- ⚡ Implementado en **Spring Boot** y usando la API de **Gmail**.

---

## 📦 Requisitos

- **Java 21**
- **Maven**
- Una cuenta de **Google Cloud** con acceso a la API de Gmail y credenciales OAuth2 configuradas.
- Token de acceso válido de Gmail.

---

## 🔧 Instalación

1. Clona este repositorio:
   ```bash
   git clone https://github.com/Juan-MZ/TaxReturn.git
   cd TaxReturn
