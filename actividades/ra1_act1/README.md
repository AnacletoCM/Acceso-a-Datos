# AadTex Store Ingestion Engine

RA1 · Actividad 1 · Manejo de ficheros

Procesa cierres de tiendas en CSV y ZIP, descifra el balance financiero y genera los resúmenes JSON con sus firmas.

## Ejecución

Requiere Java 21 y Maven 3.6.3 o superior. Utiliza Spring Boot 4.1.1.

Desde la carpeta de la actividad:

```bash
mvn spring-boot:run
```

También se puede abrir `pom.xml` en el IDE y ejecutar `AadApplication`.

Al arrancar se crean los directorios y los datos de ejemplo. El procesamiento se ejecuta cada cinco segundos. Para detener la aplicación, pulsar Ctrl+C.

## Datos

La ruta predeterminada es `C:/workspace/store_data/` en Windows y `/workspace/store_data/` en Linux y macOS. Si no se puede utilizar, se emplea `store_data/` dentro de la carpeta de ejecución.

Para indicar otra ubicación:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--aadtex.base-dir=./store_data
```

También se puede usar la variable `AADTEX_BASE_DIR`.

Los ejemplos y la clave `AadTexSecret2026` están en `src/main/resources/demo/`. Se copian una sola vez por directorio de datos.

| Entrada | Procesamiento |
|---|---|
| CSV de España y China | Lectura UTF-8 y suma con `BigDecimal` |
| Balance `.enc` | Descifrado AES-128 en memoria |
| Paquete ZIP | Lectura con `ZipInputStream` |
| Firma JPG | Copia binaria con buffer de 4096 bytes |
| Archivo vacío o defectuoso | Traslado a `output/error/` |

## Resultados

| Lote | Ventas | Importe |
|---|---:|---:|
| España | 3 | 20100.80 |
| China | 2 | 41300.80 |
| Financiero | 1 | 95000.00 |
| EE. UU. | 3 | 68450.75 |

La primera ejecución genera cuatro resúmenes JSON, cuatro firmas JPG y un archivo vacío aislado. Los resúmenes se guardan en `output/bucket/aadtex-daily-reports/2026/09/`.

Los archivos procesados se archivan en `output/processed/`. El balance descifrado no se guarda en disco. El bucket es local y el aviso a soporte se simula en el log.

Para repetir los ejemplos, utilizar un directorio de datos nuevo. Para procesar otra entrada durante la ejecución, copiar `batch_usa.zip` de los recursos a la carpeta `input` activa.

## Pruebas

```bash
mvn clean verify
```

[Volver a actividades](../README.md)
