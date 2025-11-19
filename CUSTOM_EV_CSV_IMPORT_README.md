# Custom GraphHopper: CSV-basierte EncodedValues

## Überblick

Diese GraphHopper-Erweiterung ermöglicht es, **externe Daten aus CSV-Dateien** in das Routing einzubinden. OSM Ways können basierend auf Way-IDs aus der CSV-Datei bewertet und beim Routing bevorzugt oder gemieden werden.

Dies ist nützlich, wenn Sie externe Datenquellen (z.B. Mapillary-Coverage, Straßenzustand, Messungen) haben, die nicht direkt in OSM verfügbar sind.

## Architektur

### Core-Komponenten (`core/src/main/java/`)

**EncodedValue Definition:**
- `com/graphhopper/routing/ev/CustomEV.java` - Definiert das `custom_present` BooleanEncodedValue

**CSV-Import:**
- `com/graphhopper/reader/osm/custom/CustomCsvLoader.java` - Lädt CSV-Datei und erstellt Way-ID → Attribut Mapping
- `com/graphhopper/reader/osm/custom/CustomPresentParser.java` - TagParser der das EncodedValue beim OSM-Import setzt
- `com/graphhopper/reader/osm/custom/CustomPresentImportUnit.java` - ImportUnit zur Registrierung

### Web-Komponente (`web/src/main/java/`)

**GraphHopper Integration:**
- `com/graphhopper/custom/CustomGraphHopper.java` - Erweiterte GraphHopper-Klasse die:
  - Custom ImportRegistry mit CSV-basierten EncodedValues bereitstellt
  - Konfiguration aus YAML einliest
  - CustomPresentParser zum OSM-Parser-Set hinzufügt

**Konfigurationsklassen:**
- `com/graphhopper/custom/CustomEncodedValueConfig.java` - Bean für CSV-basierte EncodedValue-Konfiguration

## Implementierung: Schritt für Schritt

### 1. CSV-Datei vorbereiten

Erstelle eine CSV-Datei mit OSM Way-IDs und Attributen:

```csv
way_id,attribute
123456789,pano
987654321,regular
555555555,none
```

- **Spalte 1**: OSM Way ID (muss mit OSM-IDs im PBF übereinstimmen)
- **Spalte 2**: Attribut-Wert (wird gegen `true_values` geprüft)
- **Mapping-Logik**: `pano`, `regular` → `custom_present=true`; alles andere → `false`

Die Spaltennamen und Matching-Werte sind konfigurierbar (siehe Konfiguration).

## Custom Model Beispiele

### Einfach - nur Penalty für fehlende Werte

```json
{
  "priority": [
    {
      "if": "custom_present == false",
      "multiply_by": 0.5
    }
  ],
  "speed": [
    {
      "if": "true",
      "limit_to": "car_average_speed"
    }
  ]
}
```

### Erweitert - Penalty für Priority UND Speed

```json
{
  "distance_influence": 70,
  "priority": [
    {
      "if": "custom_present == false",
      "multiply_by": 0.5
    }
  ],
  "speed": [
    {
      "if": "true",
      "limit_to": "car_average_speed"
    },
    {
      "if": "custom_present == false",
      "multiply_by": 0.9
    }
  ]
}
```

### Sehr stark - Straßen ohne custom_present fast ausschließen

```json
{
  "priority": [
    {
      "if": "custom_present == false",
      "multiply_by": 0.1
    }
  ]
}
```

### 2. Konfigurationsdatei erstellen

Erstelle eine YAML-Konfiguration (z.B. `config.yml`):

```yaml
graphhopper:
  # WICHTIG: CustomGraphHopper statt Standard-GraphHopper verwenden
  graph.custom_class: com.graphhopper.custom.CustomGraphHopper

  # CSV-basiertes EncodedValue konfigurieren
  custom_encoded_value:
    name: custom_present                           # Name des EncodedValues
    csv_path: data/custom/custom_values.csv        # Pfad zur CSV (relativ zum Working Directory)
    csv_column_id: way_id                          # CSV-Spalte mit OSM Way IDs
    csv_column_attribute: attribute                # CSV-Spalte mit Attributen
    true_values: pano,regular                      # Werte die zu true führen (komma-getrennt)

  # EncodedValue muss registriert werden
  graph.encoded_values: custom_present

  datareader.file: "your-map.osm.pbf"
  graph.location: graph-cache

  profiles:
    - name: car
      custom_model_files: [car.json]
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

### 4. CustomGraphHopper programmatisch verwenden

```java
import com.graphhopper.custom.CustomGraphHopper;
import com.graphhopper.config.Profile;

CustomGraphHopper hopper = new CustomGraphHopper();
hopper.setOSMFile("map.osm.pbf");
hopper.setGraphHopperLocation("graph-cache");
hopper.setProfiles(
    new Profile("car_custom")
        .setWeighting("custom")
        .setCustomModel(customModel)
);
hopper.importOrLoad();

GHResponse rsp = hopper.route(new GHRequest(lat1, lon1, lat2, lon2)
    .setProfile("car_custom"));
```

### 5. HTTP API Anfrage

```bash
curl "http://localhost:8989/route?point=51.5,0.1&point=51.6,0.2&profile=car_custom"
```

## Custom Model Parameter verstehen

- **`distance_influence`**: Wie stark die Distanz gewichtet wird (0-100+)
  - Niedrig (z.B. 30): Bevorzugt schnellere Routen
  - Hoch (z.B. 100): Bevorzugt kürzere Routen

- **`priority`**: Ändert die Routing-Priorität
  - `multiply_by: 0.5`: Halbiert die Priorität (Straße wird gemieden)
  - `multiply_by: 2.0`: Verdoppelt die Priorität (Straße wird bevorzugt)
  - `multiply_by: 0.1`: Straße wird stark gemieden

- **`speed`**: Ändert die angenommene Geschwindigkeit
  - `multiply_by: 0.9`: 10% langsamer
  - `multiply_by: 0.5`: 50% langsamer

## Erweiterte Konfiguration

### Mehrere CSV-basierte EncodedValues

Aktuell unterstützt das System **ein** konfigurierbares CSV-basiertes EncodedValue. Um mehrere hinzuzufügen, müsste die Konfiguration und `CustomGraphHopper` erweitert werden.

### Anpassung der Matching-Logik

Die Logik welche CSV-Werte zu `true` führen ist in der Config als `true_values` Liste definiert:

```yaml
custom_encoded_value:
  true_values: pano,regular,high_quality  # Alle diese Werte → true
```

Für komplexere Logik kann `CustomCsvLoader.java` angepasst werden.

## Fehlerbehebung

### "Cannot find encoded value: custom_present"

**Ursache:** CustomGraphHopper wird nicht verwendet oder EncodedValue ist nicht in `graph.encoded_values` registriert.

**Lösung:** 
- Stelle sicher dass `graph.custom_class: com.graphhopper.custom.CustomGraphHopper` in der Config gesetzt ist
- Füge `custom_present` (oder dein Name) zu `graph.encoded_values` hinzu

### CSV wird nicht gefunden

**Ursache:** Pfad in `csv_path` ist relativ zum Working Directory.

**Lösung:** 
- Nutze absoluten Pfad: `csv_path: /home/user/data/custom_values.csv`
- Oder stelle sicher dass du GraphHopper aus dem richtigen Verzeichnis startest
- Logging zeigt den geladenen Pfad beim Start

### Routing ignoriert custom_present

**Debugging-Schritte:**
1. **CSV-Import:** Prüfe Logs beim Start - `CustomCsvLoader` sollte Anzahl geladener Entries loggen
2. **Way-IDs:** Stelle sicher dass CSV Way-IDs mit OSM Way-IDs übereinstimmen (nicht Node-IDs!)
3. **Custom Model:** Prüfe ob `custom_present` im Custom Model verwendet wird
4. **Profil:** Stelle sicher dass du das richtige Profil beim Routing nutzt

### Custom Class wird nicht geladen

**Ursache:** `graph.custom_class` wird von manchen GraphHopper-Versionen nicht unterstützt.

**Lösung:** 
- GraphHopper programmatisch starten und direkt `new CustomGraphHopper()` verwenden
- Oder `CustomGraphHopper` als Main-Class in der Application verwenden

## Performance

- CSV wird einmal beim Import geladen und in eine HashMap gespeichert
- Lookup ist O(1) - sehr schnell
- EncodedValue nutzt nur 1 Bit pro Edge
- Custom Model hat minimalen Overhead beim Routing
