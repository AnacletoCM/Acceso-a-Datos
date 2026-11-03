# AadTex Store Ingestion Engine

**RA1 · Actividad 1 — Manejo de ficheros**

Motor de ingestión que procesa automáticamente los cierres de las tiendas AadTex: lee ventas, descifra balances, consume paquetes ZIP y publica resúmenes JSON.

[Repositorio](https://github.com/AnacletoCM/Acceso-a-Datos) · [Actividad en GitHub](https://github.com/AnacletoCM/Acceso-a-Datos/tree/main/actividades/ra1_act1) · [Verificación automática](https://github.com/AnacletoCM/Acceso-a-Datos/actions/workflows/ra1.yml)

## Requisitos

| Herramienta | Versión |
|---|---|
| JDK | 21 |
| Maven | 3.6.3 o superior |
| Spring Boot | 4.1.1, incluido en el proyecto |

La primera compilación necesita Internet para descargar las dependencias. No se necesita base de datos ni cuenta cloud.

## Ejecución

Desde la carpeta que contiene `pom.xml`:

```bash
mvn spring-boot:run
```

En un IDE, abre `pom.xml` como proyecto Maven, selecciona JDK 21 y ejecuta `AadApplication`.

La aplicación crea los directorios y los datos de ejemplo al arrancar. El Scheduler procesa los archivos cada **cinco segundos**. Para terminar, pulsa **Ctrl+C**.

## Directorio de datos

- **Windows:** `C:/workspace/store_data/`.
- **Linux y macOS:** `/workspace/store_data/`.

Si la ruta no existe, se crea automáticamente. Si el sistema no permite crear o utilizar la ruta predeterminada, se utiliza `store_data/` dentro de la carpeta desde la que se ha iniciado el programa. La consola indica la ubicación utilizada.

Para elegir una ruta concreta, utiliza la variable de entorno `AADTEX_BASE_DIR` o ejecuta:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--aadtex.base-dir=./store_data
```

Una ruta configurada explícitamente debe permitir escritura; si no es válida, el programa informa del error.

## Archivos y procesamiento

| Entrada | Tratamiento |
|---|---|
| CSV de España y China | Lectura UTF-8 y suma de ventas con `BigDecimal` |
| Balance `.enc` | Descifrado AES-128 en memoria |
| Lote `.zip` | Lectura por `ZipInputStream`, sin extraer el CSV a disco |
| Firma `.jpg` | Copia binaria con buffer de 4096 bytes |
| Archivo vacío o defectuoso | Alerta simulada y traslado a `output/error/` |

Los datos de prueba y la clave `AadTexSecret2026` se incluyen en `src/main/resources/demo/`. Se generan una sola vez por directorio de datos para evitar duplicados al reiniciar.

## Resultado esperado

| Lote | Ventas | Importe |
|---|---:|---:|
| España | 3 | 20100.80 |
| China | 2 | 41300.80 |
| Financiero | 1 | 95000.00 |
| EE. UU. | 3 | 68450.75 |

La demostración inicial produce **cuatro resúmenes JSON**, **cuatro firmas JPG** y **un archivo vacío aislado**.

```text
store_data/
├── input/
├── keys/
└── output/
    ├── processed/
    │   ├── batch_<UUID>/
    │   └── signatures/
    ├── error/
    └── bucket/aadtex-daily-reports/2026/09/
        └── summary_batch_<UUID>.json
```

El correo a soporte se simula mediante log y el bucket es local. El CSV financiero descifrado no se guarda en disco.

## Comprobación

```bash
mvn clean verify
```

El repositorio también ejecuta una [comprobación automática en Windows, Linux y macOS](https://github.com/AnacletoCM/Acceso-a-Datos/actions/workflows/ra1.yml) con Java 21: arranque, rutas, UTF-8, importes, archivos de salida, nuevas entradas y reinicio.

Para repetir los datos iniciales sin borrar resultados, elige un directorio nuevo con `AADTEX_BASE_DIR`. Para probar otra entrada en directo, deposita de nuevo `batch_usa.zip` en la carpeta `input` activa.
