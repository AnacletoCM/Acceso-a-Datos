# 🚗 Examen Práctico RA1: TrafficAlert Ingestion Engine (DGT)

## Implementación de Servicio de Ingesta, Criptografía en RAM, Transcodificación UTF-8 y Consolidación JSON

- **Módulo Profesional:** Acceso a Datos (AAD)
- **Resultado de Aprendizaje:** RA1 — Manejo de Ficheros
- **Tiempo límite:** 2 horas
- **Punto de partida:** Arquetipo base proporcionado por el profesor (`traffic-radar.zip`).
- **Clase principal de arranque:** `TrafficRadarApplication`
- **Condiciones:** Sin conexión a Internet ni asistentes de IA. Material permitido: código fuente propio de la práctica `ra1_act1` y apuntes `ra1-manejo-ficheros.md`.

## 1. Contexto de Negocio

La Dirección General de Tráfico (DGT) necesita completar el microservicio encargado de procesar los datos registrados por cinemómetros (radares de tramo y fijos).

El proyecto base contenido en `traffic-radar.zip` cuenta con la infraestructura Spring Boot configurada y su clase principal `TrafficRadarApplication`.

Tu labor consiste en:

1. Hacer que la clase de inicio `TrafficRadarApplication` implemente la interfaz `CommandLineRunner`.
2. Sobrescribir e implementar su método `run(String... args)` para que inyecte e invoque al método orquestador del servicio (`trafficService.processTrafficData()`).
3. Completar la implementación de los métodos del servicio para procesar los ficheros recibidos y generar el informe JSON final.

En el punto de entrada local se depositan:

- La clave secreta del cinemómetro en su carpeta de claves (`keys/dgt_secret.key`).
- Un parte estándar de radar fijo en formato CSV (con caracteres internacionales).
- Un parte confidencial emitido por un radar móvil de la Guardia Civil, cifrado en origen mediante AES-128.
- Ficheros defectuosos o vacíos que deben ser aislados.

## 2. Estructura de Directorios del Sistema

El arquetipo trabaja sobre la ruta base `c:/workspace/traffic_data/`:

```text
c:/workspace/traffic_data/
├── keys/
│   └── dgt_secret.key                  <-- Clave simétrica AES-128 ("DgtSecretKey2026")
├── input/                              <-- Punto de entrada donde se depositan los partes
│   ├── radar_m30.csv                   <-- CSV estándar en UTF-8
│   ├── radar_corrupto.csv              <-- Fichero defectuoso de 0 Bytes
│   └── radar_movil_guardia_civil.enc   <-- Fichero cifrado con AES-128
└── output/                             <-- Salida que debe generar tu servicio
    ├── processed/                      <-- Ficheros válidos trasladados tras leerse
    ├── error/                          <-- Ficheros corruptos aislados
    └── payload/                        <-- JSON generado para el endpoint
        └── dgt_dispatch_payload.json
```

## 3. Especificación de Ficheros de Entrada

### A. Clave de Cifrado (`keys/dgt_secret.key`)

**Tipo:** Fichero de texto plano (UTF-8, 16 bytes).

**Contenido:**

```text
DgtSecretKey2026
```

### B. Lote Estándar Radar Fijo (`input/radar_m30.csv`)

**Codificación:** UTF-8 estricto.

**Contenido:**

```csv
Matricula,MarcaModelo,VelocidadMedida,LimiteVia,SancionEuros
1234-KBB,Peugeot 308 (España),115.5,90,100.00
5678-CJK,BYD 唐 (China Edition),135.0,90,300.00
9012-LMN,Seat León Cupra,85.0,90,0.00
```

### C. Lote Radar Móvil Cifrado (`input/radar_movil_guardia_civil.enc`)

**Formato:** Binario cifrado con AES-128 / ECB / PKCS5Padding usando la clave contenida en `keys/dgt_secret.key`.

**Texto en claro original equivalente:**

```csv
Matricula,MarcaModelo,VelocidadMedida,LimiteVia,SancionEuros
0001-DIR,Audi A8 Blindado Oficial,180.0,90,600.00
```

### D. Lote Defectuoso (`input/radar_corrupto.csv`)

**Formato:** Fichero vacío de 0 Bytes.

## 4. Tareas a Implementar

### 📍 Tarea 0: Disparo de la Aplicación en `TrafficRadarApplication`

1. Modificar la clase principal `TrafficRadarApplication` para que implemente la interfaz `CommandLineRunner`:

   ```java
   @SpringBootApplication
   public class TrafficRadarApplication implements CommandLineRunner { ... }
   ```

2. Inyectar el servicio de ingestión (`TrafficIngestionService` o equivalente en el esqueleto).
3. Implementar el método `run(String... args)` para invocar al método orquestador principal del servicio (por ejemplo: `trafficService.processTrafficData()`).

### 📍 Tarea 1: Control de Directorios y Aislamiento con NIO.2

1. Asegurar la creación de los directorios de salida (`/output/processed/`, `/output/error/` y `/output/payload/`) con `Files.createDirectories()`.
2. Explorar los archivos existentes en `/input/`.
3. **Detección de 0 Bytes:** Comprobar si `Files.size(path) == 0`:
   - Emitir por consola/log: `[ALERTA DGT] Fichero corrupto detectado y aislado: <nombre_fichero>`.
   - Moverlo atómicamente a `/output/error/` mediante `Files.move(..., StandardCopyOption.REPLACE_EXISTING)`.

### 📍 Tarea 2: Lectura UTF-8 y Desencriptación AES en RAM

**Lectura del CSV estándar (`radar_m30.csv`):**

- Leer las líneas con `BufferedReader` e `InputStreamReader` forzando explícitamente `StandardCharsets.UTF_8`.
- Descartar la cabecera y parsear cada registro acumulando sus métricas.

**Descifrado de `radar_movil_guardia_civil.enc`:**

- Leer la clave secreta desde `keys/dgt_secret.key` para inicializar el descifrado simétrico AES (`javax.crypto.Cipher`).
- Desencriptar los bytes del fichero `.enc` y volcarlos en un `ByteArrayInputStream`.
- Leer las líneas con `BufferedReader` en memoria RAM, sin guardar texto en claro en el disco.

**Mover procesados:** Tras leer con éxito cada archivo válido (`.csv` o `.enc`), moverlo a `/output/processed/`.

### 📍 Tarea 3: Consolidación y Generación del Payload JSON

Con los datos combinados de ambos orígenes (3 vehículos del radar fijo + 1 del radar móvil), calcular:

- **`totalVehicles`:** 4
- **`totalInfractions`** (donde `VelocidadMedida > LimiteVia`): 3
- **`maxSpeed`:** 180.0
- **`totalFines`:** 1000.00 € (400.00 € del fijo + 600.00 € del móvil)

Escribir el fichero `/output/payload/dgt_dispatch_payload.json` con la siguiente estructura exacta:

```json
{
  "radarId": "RADAR-M30-KM12",
  "status": "READY_FOR_DISPATCH",
  "totalVehicles": 4,
  "totalInfractions": 3,
  "maxSpeed": 180.0,
  "totalFines": 1000.00,
  "infractions": [
    {
      "plate": "1234-KBB",
      "model": "Peugeot 308 (España)",
      "speed": 115.5,
      "fine": 100.00
    },
    {
      "plate": "5678-CJK",
      "model": "BYD 唐 (China Edition)",
      "speed": 135.0,
      "fine": 300.00
    },
    {
      "plate": "0001-DIR",
      "model": "Audi A8 Blindado Oficial",
      "speed": 180.0,
      "fine": 600.00
    }
  ]
}
```

## 5. Entrega

El proyecto completado se entregará comprimido en un archivo ZIP llamado estrictamente **`ra1_ptp_app_dgt_traffic_radar.zip`**.

Al ejecutar la aplicación lanzando `TrafficRadarApplication`, el método `run()` ejecutará el servicio en una sola pasada, mostrando el avance en consola y dejando la estructura de `/output/` con los ficheros procesados y el payload JSON final correctamente generado.
