# TrafficAlert Ingestion Engine · DGT

RA1 · Examen práctico · Manejo de ficheros

Lee los partes de un radar fijo y un radar móvil, descifra el lote confidencial en memoria y genera el informe JSON de infracciones.

[Enunciado](docs/enunciado.md) · [Código](src/main/java/com/ra1/ptp/dgt/traffic_radar/) · [ZIP de entrega](entrega/ra1_ptp_app_dgt_traffic_radar.zip)

## Ejecución

Requiere Java 21 y Maven 3.6.3 o superior. Utiliza Spring Boot 4.1.2.

Desde la carpeta de la actividad:

```powershell
mvn clean verify
```

Copiar los datos de ejemplo a la ruta del enunciado:

```powershell
$datos = 'C:\workspace\traffic_data'
New-Item -ItemType Directory -Path $datos -Force | Out-Null
Copy-Item -LiteralPath '.\traffic_data\keys', '.\traffic_data\input' -Destination $datos -Recurse -Force
```

Ejecutar la aplicación:

```powershell
java -jar target/traffic-radar-0.0.1-SNAPSHOT.jar
```

También se puede abrir `pom.xml` en el IDE y ejecutar `TrafficRadarApplication`.

La aplicación crea las carpetas que falten, procesa las entradas una vez y termina. La clave y los ficheros de entrada se aportan por separado.

Para utilizar otra ubicación con permiso de escritura, preparar allí `keys` e `input` e indicar la ruta:

```powershell
java "-Dtraffic.data.path=C:/ruta/traffic_data" -jar target/traffic-radar-0.0.1-SNAPSHOT.jar
```

En Linux y macOS se puede indicar una ruta local, por ejemplo `-Dtraffic.data.path=./datos`.

## Archivos

| Entrada | Tratamiento | Destino |
|---|---|---|
| `radar_m30.csv` | Lectura UTF-8 | `output/processed/` |
| `radar_movil_guardia_civil.enc` | AES-128 / ECB / PKCS5Padding en RAM | `output/processed/` |
| `radar_corrupto.csv` | Detección de 0 bytes y alerta en consola | `output/error/` |

La clave de prueba `DgtSecretKey2026` tiene 16 bytes y se lee de `keys/dgt_secret.key`. El CSV descifrado se procesa con `ByteArrayInputStream` y no se guarda en disco.

## Resultado

| Dato | Valor |
|---|---:|
| Vehículos | 4 |
| Infracciones | 3 |
| Velocidad máxima | 180,0 |
| Sanciones | 1.000,00 € |

El informe se guarda en `output/payload/dgt_dispatch_payload.json`. Solo se incluyen como infracciones los registros cuya velocidad supera el límite de la vía.

La carpeta `traffic_data/` contiene las entradas originales y un [JSON de ejemplo](traffic_data/output/payload/dgt_dispatch_payload.json). Para repetir el lote, copiar de nuevo las entradas antes de arrancar. Sin entradas, el informe tendrá cero vehículos.

## Pruebas

```powershell
mvn clean verify
```

Las cinco pruebas cubren la creación de carpetas, el lote completo, los registros defectuosos, los errores de UTF-8 y cifrado, y el cálculo de infracciones.

## Entrega

[ra1_ptp_app_dgt_traffic_radar.zip](entrega/ra1_ptp_app_dgt_traffic_radar.zip)

[Volver a actividades](../README.md)
