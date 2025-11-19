# Custom GraphHopper: Bicycle Infrastructure EncodedValue

## Überblick

Das `bicycle_infra` EncodedValue kategorisiert **Fahrradinfrastruktur basierend auf OSM-Tags**. Anders als CSV-basierte EncodedValues werden die Werte direkt aus den OSM-Daten während des Imports extrahiert.

Die Kategorisierung basiert auf dem **FixMyBerlin Tilda BikelaneCategories** Schema ( https://github.com/FixMyBerlin/tilda-geo/blob/main/processing/topics/roads_bikelanes/bikelanes/BikelaneCategories.lua ) und unterscheidet verschiedene Arten von Radwegen, Radfahrstreifen, Schutzstreifen und anderen Infrastrukturtypen.

## Architektur

### Core-Komponenten (`core/src/main/java/`)

**EncodedValue Definition:**
- `com/graphhopper/routing/ev/BicycleInfra.java` - Enum mit allen Infrastruktur-Kategorien
- `com/graphhopper/routing/ev/BicycleInfraEV.java` - Factory für das EnumEncodedValue

**OSM-Tag Parsing:**
- `com/graphhopper/reader/osm/custom/BicycleInfraParser.java` - TagParser der OSM-Tags analysiert und kategorisiert
- `com/graphhopper/reader/osm/custom/BicycleInfraImportUnit.java` - ImportUnit zur Registrierung

### Web-Komponente (`web/src/main/java/`)

**GraphHopper Integration:**
- `com/graphhopper/custom/CustomGraphHopper.java` - Registriert `bicycle_infra` und fügt `BicycleInfraParser` hinzu

## Infrastruktur-Kategorien

Das Enum `BicycleInfra` definiert folgende Kategorien (Auswahl):



### Straßentypen
- `BICYCLE_ROAD` - Fahrradstraße
- `BICYCLE_ROAD_VEHICLE_DESTINATION` - Fahrradstraße mit Anlieger/Kfz frei
- `PEDESTRIAN_AREA_BICYCLE_YES` - Fußgängerzone, Fahrrad frei
- `CROSSING` - Straßenquerung
- `NONE` - Keine spezielle Fahrradinfrastruktur

### Radwege (baulich getrennt)
- `CYCLEWAY_ADJOINING` - Radweg, straßenbegleitend
- `CYCLEWAY_ISOLATED` - Radweg, selbstständig geführt (durch Wald/Feld)
- `CYCLEWAY_ADJOINING_OR_ISOLATED` - Radweg (wenn `is_sidepath` fehlt)
- `CYCLEWAY_LINK` - Radweg-Routing-Verbindungsstück

### Radfahrstreifen/Schutzstreifen (auf der Fahrbahn)
- `CYCLEWAY_ON_HIGHWAY_EXCLUSIVE` - Radfahrstreifen (durchgezogene Linie)
- `CYCLEWAY_ON_HIGHWAY_ADVISORY` - Schutzstreifen (gestrichelte Linie)
- `CYCLEWAY_ON_HIGHWAY_ADVISORY_OR_EXCLUSIVE` - Nicht spezifiziert
- `CYCLEWAY_ON_HIGHWAY_PROTECTED` - Geschützter Radfahrstreifen (Protected Bike Lane)
- `CYCLEWAY_ON_HIGHWAY_BETWEEN_LANES` - Radfahrstreifen in Mittellage ("Angstweiche")

### Kombinierte Geh- und Radwege
- `FOOT_AND_CYCLEWAY_SEGREGATED_ADJOINING` - Getrennter Geh-/Radweg, straßenbegleitend
- `FOOT_AND_CYCLEWAY_SEGREGATED_ISOLATED` - Getrennter Geh-/Radweg, selbstständig
- `FOOT_AND_CYCLEWAY_SHARED_ADJOINING` - Gemeinsamer Geh-/Radweg, straßenbegleitend
- `FOOT_AND_CYCLEWAY_SHARED_ISOLATED` - Gemeinsamer Geh-/Radweg, selbstständig
- `FOOTWAY_BICYCLE_YES_ADJOINING` - Gehweg mit Radfreigabe, straßenbegleitend
- `FOOTWAY_BICYCLE_YES_ISOLATED` - Gehweg mit Radfreigabe, selbstständig

### Busspuren
- `SHARED_BUS_LANE_BIKE_WITH_BUS` - Radfahrstreifen mit Freigabe Busverkehr
- `SHARED_BUS_LANE_BUS_WITH_BIKE` - Busspur mit Fahrrad frei

### Sonstige
- `SHARED_MOTOR_VEHICLE_LANE` - Gemeinsamer Fahrstreifen (Piktogramme)
- `NEEDS_CLARIFICATION` - Führungsform unklar (unzureichende OSM-Tags)

## Richtungsabhängigkeit

Das `bicycle_infra` EncodedValue ist **richtungsabhängig** (`directional=true`). Das bedeutet:

- Forward-Richtung und Backward-Richtung können unterschiedliche Kategorien haben
- Beispiel: `cycleway:right=lane` → Forward: `CYCLEWAY_ON_HIGHWAY_EXCLUSIVE`, Backward: `NONE`
- Bidirektionale Radwege (`cycleway:left:oneway=no`) werden für beide Richtungen berücksichtigt

Der Parser prüft für jede Richtung:
- Forward: `cycleway:right`, `cycleway:both`, `cycleway`
- Backward: `cycleway:left`, `cycleway:both`, `cycleway`

## OSM-Tag Mapping

Der `BicycleInfraParser` analysiert OSM-Tags in einer definierten **Precedence Order** (first match wins):

### Beispiel-Mappings

| OSM-Tags | Kategorie |
|----------|-----------|
| `bicycle_road=yes` | `BICYCLE_ROAD` |
| `highway=cycleway` + `is_sidepath=yes` | `CYCLEWAY_ADJOINING` |
| `highway=cycleway` + `is_sidepath=no` | `CYCLEWAY_ISOLATED` |
| `cycleway:right=lane` + `cycleway:right:lane=exclusive` | `CYCLEWAY_ON_HIGHWAY_EXCLUSIVE` |
| `cycleway:left=lane` + `cycleway:left:lane=advisory` | `CYCLEWAY_ON_HIGHWAY_ADVISORY` |
| `highway=pedestrian` + `bicycle=yes` | `PEDESTRIAN_AREA_BICYCLE_YES` |
| `highway=footway` + `bicycle=yes` + `is_sidepath=yes` | `FOOTWAY_BICYCLE_YES_ADJOINING` |
| `highway=path` + `foot=designated` + `bicycle=designated` + `segregated=yes` | `FOOT_AND_CYCLEWAY_SEGREGATED_ADJOINING_OR_ISOLATED` |

Die vollständige Logik findet sich in `BicycleInfraParser.categorizeForDirection()`.

## Implementierung: Schritt für Schritt

### 1. Konfigurationsdatei erstellen

Füge `bicycle_infra` zu den `graph.encoded_values` hinzu:

```yaml
graphhopper:
  # CustomGraphHopper verwenden (unterstützt bicycle_infra)
  graph.custom_class: com.graphhopper.custom.CustomGraphHopper

  # bicycle_infra EncodedValue registrieren
  graph.encoded_values: bicycle_infra

  datareader.file: "your-map.osm.pbf"
  graph.location: graph-cache

  profiles:
    - name: bike
      vehicle: bike
      weighting: fastest
      custom_model_files: [bike_prefer_cycleways.json]
```

### 2. Custom Model erstellen

Erstelle ein Custom Model das `bicycle_infra` nutzt (z.B. `bike_prefer_cycleways.json`):

```json
{
  "priority": [
    {
      "if": "bicycle_infra == CYCLEWAY_ADJOINING || bicycle_infra == CYCLEWAY_ISOLATED",
      "multiply_by": 1.5
    },
    {
      "if": "bicycle_infra == CYCLEWAY_ON_HIGHWAY_EXCLUSIVE",
      "multiply_by": 1.3
    },
    {
      "if": "bicycle_infra == CYCLEWAY_ON_HIGHWAY_ADVISORY",
      "multiply_by": 1.2
    },
    {
      "if": "bicycle_infra == BICYCLE_ROAD",
      "multiply_by": 1.4
    },
    {
      "if": "bicycle_infra == NONE",
      "multiply_by": 0.8
    }
  ]
}
```

### 3. GraphHopper starten

```bash
# Build
mvn clean install -DskipTests

# Server starten
cd web
mvn exec:java -Dexec.mainClass=com.graphhopper.application.GraphHopperApplication \
  -Dexec.args="server ../config.yml"
```

### 4. Routing nutzen

```bash
# HTTP API
curl "http://localhost:8989/route?point=52.5,13.4&point=52.6,13.5&profile=bike"
```

## Custom Model Beispiele

### Beispiel 1: Radwege stark bevorzugen

```json
{
  "distance_influence": 70,
  "priority": [
    {
      "if": "bicycle_infra == CYCLEWAY_ADJOINING || bicycle_infra == CYCLEWAY_ISOLATED",
      "multiply_by": 2.0
    },
    {
      "if": "bicycle_infra == CYCLEWAY_ON_HIGHWAY_EXCLUSIVE",
      "multiply_by": 1.5
    },
    {
      "if": "bicycle_infra == NONE",
      "multiply_by": 0.5
    }
  ]
}
```

### Beispiel 2: Fahrradstraßen und Protected Bike Lanes bevorzugen

```json
{
  "priority": [
    {
      "if": "bicycle_infra == BICYCLE_ROAD",
      "multiply_by": 1.8
    },
    {
      "if": "bicycle_infra == CYCLEWAY_ON_HIGHWAY_PROTECTED",
      "multiply_by": 1.7
    },
    {
      "if": "bicycle_infra == CYCLEWAY_ON_HIGHWAY_EXCLUSIVE",
      "multiply_by": 1.4
    }
  ]
}
```

### Beispiel 3: Schutzstreifen vermeiden

```json
{
  "priority": [
    {
      "if": "bicycle_infra == CYCLEWAY_ON_HIGHWAY_ADVISORY",
      "multiply_by": 0.3
    },
    {
      "if": "bicycle_infra == CYCLEWAY_ON_HIGHWAY_EXCLUSIVE",
      "multiply_by": 1.2
    }
  ]
}
```

### Beispiel 4: Gemeinsame Geh-/Radwege vermeiden

```json
{
  "priority": [
    {
      "if": "bicycle_infra == FOOT_AND_CYCLEWAY_SHARED_ADJOINING || bicycle_infra == FOOT_AND_CYCLEWAY_SHARED_ISOLATED",
      "multiply_by": 0.4
    },
    {
      "if": "bicycle_infra == FOOTWAY_BICYCLE_YES_ADJOINING || bicycle_infra == FOOTWAY_BICYCLE_YES_ISOLATED",
      "multiply_by": 0.3
    }
  ]
}
```

## Erweiterung der Kategorien

Um neue Kategorien hinzuzufügen:

1. **Enum erweitern:** Füge neue Werte zu `BicycleInfra.java` hinzu
2. **Parser-Logik:** Erweitere `BicycleInfraParser.categorizeForDirection()` mit der neuen Logik
3. **Precedence beachten:** Die Reihenfolge in `categorizeForDirection()` ist wichtig (first match wins)

## Performance

- EncodedValue nutzt **3 Bits** pro Edge pro Richtung (25 Kategorien passen in 5 Bits, aktuell 24)
- Kategorisierung erfolgt **einmal beim OSM-Import**, nicht beim Routing
- Parsing-Overhead ist minimal da OSM-Tags bereits im Speicher sind
- Custom Model Evaluation ist sehr schnell (einfache Enum-Vergleiche)

## Fehlerbehebung

### "Cannot find encoded value: bicycle_infra"

**Ursache:** EncodedValue ist nicht registriert oder CustomGraphHopper wird nicht verwendet.

**Lösung:**
- Füge `bicycle_infra` zu `graph.encoded_values` hinzu
- Stelle sicher dass `graph.custom_class: com.graphhopper.custom.CustomGraphHopper` gesetzt ist
- Lösche den Graph-Cache und importiere neu

### Falsche Kategorisierung

**Debugging:**
1. **OSM-Tags prüfen:** Schaue dir die OSM-Tags des Ways an (z.B. auf openstreetmap.org)
2. **Parser-Logik:** Prüfe `BicycleInfraParser.categorizeForDirection()` - welche Bedingung greift zuerst?
3. **Logging:** Füge Debug-Logging in `BicycleInfraParser` hinzu um zu sehen welche Tags gelesen werden

### Kategorien fehlen im Custom Model

**Ursache:** Custom Model erlaubt nur bestimmte EncodedValue-Typen.

**Lösung:**
- Prüfe ob Enum-Werte korrekt geschrieben sind (Case-sensitive!)
- Verwende `toString()` Werte: `CYCLEWAY_ADJOINING` nicht `cycleway_adjoining`

## Unterschied zu CSV-basierten EncodedValues

| Aspekt | CSV-basiert (`custom_present`) | OSM-basiert (`bicycle_infra`) |
|--------|-------------------------------|-------------------------------|
| **Datenquelle** | Externe CSV-Datei mit Way-IDs | OSM-Tags direkt aus PBF |
| **Flexibilität** | Beliebige externe Daten | Nur was in OSM verfügbar ist |
| **Maintenance** | CSV muss gepflegt werden | OSM-Daten werden automatisch aktualisiert |
| **EncodedValue-Typ** | Boolean (1 Bit) | Enum (3-5 Bits) |
| **Anwendungsfall** | Externe Messungen, Coverage | Infrastruktur-Kategorisierung |
| **Performance** | CSV-Lookup beim Import | Nur Tag-Parsing beim Import |

## Kombination beider Ansätze

Beide EncodedValues können **gleichzeitig** verwendet werden:

```yaml
graphhopper:
  graph.custom_class: com.graphhopper.custom.CustomGraphHopper

  # Beide EncodedValues registrieren
  graph.encoded_values: bicycle_infra, custom_present

  custom_encoded_value:
    name: custom_present
    csv_path: data/custom/mapillary_coverage.csv
    # ...
```

Custom Model:
```json
{
  "priority": [
    {
      "if": "bicycle_infra == CYCLEWAY_ADJOINING && custom_present == true",
      "multiply_by": 2.0
    },
    {
      "if": "bicycle_infra == CYCLEWAY_ADJOINING && custom_present == false",
      "multiply_by": 1.2
    }
  ]
}
```

Dies ermöglicht z.B. Radwege mit guter Mapillary-Coverage stärker zu bevorzugen.
