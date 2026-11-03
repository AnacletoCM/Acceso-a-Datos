<h1 align="center">Acceso a Datos</h1>

<p align="center">Actividades prácticas · Ficheros · Persistencia · Integración de datos</p>

<p align="center">
  <a href="https://github.com/AnacletoCM/Acceso-a-Datos/actions/workflows/ra1.yml"><img src="https://github.com/AnacletoCM/Acceso-a-Datos/actions/workflows/ra1.yml/badge.svg" alt="Verificación RA1"></a>
</p>

<p align="center">
  <a href="actividades/">Actividades</a> ·
  <a href="actividades/ra1_act1/">RA1 · Actividad 1</a> ·
  <a href="actividades/ra1_ptp_dgt_traffic_radar/">TrafficAlert · DGT</a> ·
  <a href="https://github.com/AnacletoCM/Acceso-a-Datos/actions/workflows/ra1.yml">Comprobaciones</a>
</p>

---

Repositorio de proyectos del módulo **Acceso a Datos**. Cada actividad tiene su propia carpeta, código fuente, recursos y pasos de ejecución.

## Actividades

| Resultado de aprendizaje | Actividad | Contenido | Proyecto |
|---|---|---|---|
| **RA1 · Manejo de ficheros** | **Actividad 1** | Ingestión de ventas, ZIP, AES-128, UTF-8, buffers y tareas programadas | [AadTex Store Ingestion Engine →](actividades/ra1_act1/) |
| **RA1 · Manejo de ficheros** | **Examen práctico · DGT** | Radares, CSV, AES en RAM, UTF-8 y consolidación JSON | [TrafficAlert Ingestion Engine →](actividades/ra1_ptp_dgt_traffic_radar/) |

## Puesta en marcha

**Requisitos:** JDK 21 y Maven 3.6.3 o superior. La primera compilación necesita Internet para descargar las dependencias.

```bash
git clone https://github.com/AnacletoCM/Acceso-a-Datos.git
cd Acceso-a-Datos/actividades/ra1_act1
mvn spring-boot:run
```

También se puede abrir el `pom.xml` de la actividad en un IDE y ejecutar su clase principal.

## Organización

```text
Acceso-a-Datos/
├── actividades/
│   ├── ra1_act1/
│   │   ├── pom.xml
│   │   ├── README.md
│   │   └── src/
│   └── ra1_ptp_dgt_traffic_radar/
│       ├── README.md
│       ├── pom.xml
│       ├── docs/
│       ├── entrega/
│       ├── src/
│       └── traffic_data/
└── .github/
    └── workflows/
```

## Comprobación de funcionamiento

La actividad AadTex (`ra1_act1`) dispone de una [verificación automática](https://github.com/AnacletoCM/Acceso-a-Datos/actions/workflows/ra1.yml) en **Windows, Linux y macOS con Java 21**. Comprueba la compilación, el arranque, los importes, los caracteres internacionales, la cuarentena de errores y el reinicio sin duplicados.

Los requisitos y los resultados esperados se detallan en el [README de la actividad](actividades/ra1_act1/README.md).

La práctica [TrafficAlert · DGT](actividades/ra1_ptp_dgt_traffic_radar/) incluye una [verificación en Windows con Java 21](https://github.com/AnacletoCM/Acceso-a-Datos/actions/workflows/traffic-radar.yml), cinco pruebas, el enunciado y el ZIP preparado para entregar.
