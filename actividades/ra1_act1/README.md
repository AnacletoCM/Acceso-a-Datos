# ra1_act1: AadTex Store Ingestion Engine

Repositorio: [AnacletoCM/Acceso-a-Datos](https://github.com/AnacletoCM/Acceso-a-Datos).

## Ejecución

Requisitos: Java 21 o superior y Maven 3.6.3 o superior.

Desde la carpeta que contiene `pom.xml`:

```text
mvn spring-boot:run
```

También se puede abrir `pom.xml` en el IDE y ejecutar `AadApplication`.

La aplicación crea los datos de prueba al arrancar y procesa los lotes cada cinco segundos. Los datos se guardan en `C:/workspace/store_data/` en Windows y `/workspace/store_data/` en Linux o macOS. Los resultados se encuentran en `output/processed/`, `output/error/` y `output/bucket/aadtex-daily-reports/2026/09/`.

Para detener la aplicación, pulsa Ctrl+C.
