# TrafficAlert Ingestion Engine · DGT

**RA1 · Examen práctico — Manejo de ficheros**

Servicio de ingestión que combina partes de radares fijos y móviles, descifra el lote confidencial en memoria y genera un único informe JSON con las infracciones detectadas.

[![Verificación TrafficAlert](https://github.com/AnacletoCM/Acceso-a-Datos/actions/workflows/traffic-radar.yml/badge.svg)](https://github.com/AnacletoCM/Acceso-a-Datos/actions/workflows/traffic-radar.yml)

[Enunciado](docs/enunciado.md) · [Código fuente](src/main/java/com/ra1/ptp/dgt/traffic_radar/) · [Resultado de ejemplo](traffic_data/output/payload/dgt_dispatch_payload.json) · [Descargar entrega](entrega/ra1_ptp_app_dgt_traffic_radar.zip)

## Qué hace

| Entrada | Tratamiento | Destino |
|---|---|---|
| `radar_m30.csv` | Lectura UTF-8 y cálculo de métricas | `output/processed/` |
| `radar_movil_guardia_civil.enc` | Descifrado AES-128 / ECB / PKCS5Padding en RAM | `output/processed/` |
| `radar_corrupto.csv` | Detección de 0 bytes y alerta en consola | `output/error/` |

La clase `TrafficRadarApplication` implementa `CommandLineRunner` e invoca `trafficService.processTrafficData()` al arrancar. El servicio realiza una sola pasada y la aplicación termina al completar el procesamiento.

La clave se lee de `keys/dgt_secret.key`. El CSV confidencial se procesa mediante `ByteArrayInputStream`, sin escribir el texto descifrado en disco. Los importes se suman con `BigDecimal`.

## Resultado esperado

| Métrica | Valor |
|---|---:|
| Vehículos procesados | **4** |
| Infracciones | **3** |
| Velocidad máxima | **180,0** |
| Sanciones totales | **1.000,00 €** |

El vehículo que circula a 85 sobre un límite de 90 cuenta como vehículo procesado y queda fuera de la lista de infracciones. El JSON final conserva los modelos `Peugeot 308 (España)` y `BYD 唐 (China Edition)`.

## Requisitos

| Herramienta | Versión |
|---|---|
| JDK | 21 |
| Maven | 3.6.3 o superior |
| Spring Boot | 4.1.2 |

La primera compilación necesita conexión para descargar las dependencias. No se utiliza base de datos ni ningún servicio externo durante la ejecución.

## Ejecutar en Windows

Desde esta carpeta, donde se encuentra `pom.xml`:

```powershell
mvn clean verify
```

Preparar los datos del enunciado en su ubicación predeterminada:

```powershell
$datos = 'C:\workspace\traffic_data'
New-Item -ItemType Directory -Path $datos -Force | Out-Null
Copy-Item -LiteralPath '.\traffic_data\keys', '.\traffic_data\input' -Destination $datos -Recurse -Force
```

Arrancar la aplicación:

```powershell
java -jar target/traffic-radar-0.0.1-SNAPSHOT.jar
```

En un IDE también se puede abrir `pom.xml`, seleccionar JDK 21 y ejecutar `TrafficRadarApplication`.

La aplicación crea la carpeta base y las subcarpetas que falten, siempre que disponga de permiso de escritura. Los ficheros de entrada y la clave se aportan por separado; crear las carpetas no genera datos de ejemplo.

### Elegir otra carpeta

Si se necesita trabajar en otra ubicación, preparar allí las carpetas `keys` e `input` y pasar la ruta al ejecutar:

```powershell
java "-Dtraffic.data.path=C:/ruta/traffic_data" -jar target/traffic-radar-0.0.1-SNAPSHOT.jar
```

En Linux o macOS se puede usar una ruta local, por ejemplo `-Dtraffic.data.path=./datos`. La ruta predeterminada del ejercicio es `C:/workspace/traffic_data/`.

## Estructura y salida

```text
ra1_ptp_dgt_traffic_radar/
├── README.md
├── pom.xml
├── docs/enunciado.md
├── entrega/ra1_ptp_app_dgt_traffic_radar.zip
├── src/
│   ├── main/java/com/ra1/ptp/dgt/traffic_radar/
│   │   ├── TrafficRadarApplication.java
│   │   └── TrafficIngestionService.java
│   └── test/java/com/ra1/ptp/dgt/traffic_radar/
│       └── TrafficRadarApplicationTests.java
└── traffic_data/
    ├── keys/dgt_secret.key
    ├── input/
    └── output/
        ├── processed/
        ├── error/
        └── payload/dgt_dispatch_payload.json
```

`traffic_data/` contiene las entradas de ejemplo y una copia del resultado esperado. La clave de 16 bytes `DgtSecretKey2026` es la clave de prueba indicada en el enunciado.

Después de procesar un lote, los ficheros dejan de estar en la carpeta `input` utilizada. Para repetir la demostración, copiar de nuevo las entradas originales antes de arrancar. Una ejecución sin entradas genera un informe con cero vehículos.

## Comprobaciones

```powershell
mvn clean verify
```

Las cinco pruebas verifican:

- Creación de la estructura desde una ruta inexistente.
- Procesamiento combinado de CSV y AES, con el JSON exacto del enunciado.
- Aislamiento de registros defectuosos sin sumar datos parciales.
- Rechazo de UTF-8 incorrecto y ficheros cifrados dañados.
- Cálculo de métricas y exclusión de vehículos que circulan justo al límite.

La [verificación automática](https://github.com/AnacletoCM/Acceso-a-Datos/actions/workflows/traffic-radar.yml) compila y ejecuta estas pruebas en Windows con Java 21.

## Entrega

El archivo preparado para entregar es [**ra1_ptp_app_dgt_traffic_radar.zip**](entrega/ra1_ptp_app_dgt_traffic_radar.zip). Contiene el proyecto Maven, los datos de entrada y el resultado de ejemplo.

[← Todas las actividades](../README.md) · [Volver al repositorio](../../README.md)
