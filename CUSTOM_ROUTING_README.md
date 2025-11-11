# Custom GraphHopper mit CSV-basiertem EncodedValue

## Überblick

Dieses Setup ermöglicht es, OSM Ways basierend auf Daten aus einer CSV-Datei zu bewerten und im Routing zu bevorzugen/vermeiden.

## Dateien

### Core-Komponenten (in `core/src/main/java/`)

1. **`CustomEV.java`** - Definiert das `custom_present` BooleanEncodedValue
2. **`CustomCsvLoader.java`** - Lädt die CSV-Datei mit Way-IDs und Attributen
3. **`CustomPresentParser.java`** - Setzt das EncodedValue während des OSM-Imports

### Web-Komponente (in `web/src/main/java/`)

4. **`CustomGraphHopper.java`** - Erweitert GraphHopper und registriert das Custom EncodedValue

### Konfiguration

5. **`data/custom/custom_values.csv`** - CSV mit Way-IDs und Attributen
6. **`custom_models/car_prefer_custom_present.json`** - Custom Model für Routing
7. **`config-custom.yml`** - Beispiel-Konfiguration

## CSV-Format

```csv
way_id,attribute
123456789,pano
987654321,regular
555555555,none
```

- **way_id**: OSM Way ID
- **attribute**: `pano`, `regular` → `custom_present=true`; alles andere → `false`

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

## Verwendung

### 1. GraphHopper starten

```bash
# Mit Maven
mvn clean install -DskipTests
cd web
mvn exec:java -Dexec.mainClass=com.graphhopper.application.GraphHopperApplication \
  -Dexec.args="server ../config-custom.yml"
```

### 2. CustomGraphHopper programmatisch verwenden

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

### 3. HTTP API Anfrage

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

## Anpassungen

### CSV-Pfad ändern

In `CustomGraphHopper.java`:
```java
Long2ByteOpenHashMap map = CustomCsvLoader.load(Path.of("dein/pfad/zur/datei.csv"));
```

### Default-Wert ändern

In `CustomCsvLoader.java`:
```java
map.defaultReturnValue((byte)0); // Default: nicht vorhanden
```

### Attribut-Logik ändern

In `CustomCsvLoader.java`:
```java
byte present = (byte) (attr.equals("dein_wert") ? 1 : 0);
```

## Fehlerbehebung

### "Cannot find encoded value: custom_present"
→ CustomGraphHopper wird nicht verwendet. Stelle sicher, dass du CustomGraphHopper statt GraphHopper instanziierst.

### CSV wird nicht gefunden
→ Pfad zu `custom_values.csv` ist relativ zum Arbeitsverzeichnis. Nutze absoluten Pfad oder prüfe das Working Directory.

### Routing ignoriert custom_present
→ Prüfe ob:
1. CSV korrekt geladen wurde
2. Way IDs in CSV mit OSM Way IDs übereinstimmen
3. Custom Model richtig konfiguriert ist
4. Profil mit Custom Model verwendet wird

## Performance

- CSV wird einmal beim Import geladen und in eine HashMap gespeichert
- Lookup ist O(1) - sehr schnell
- EncodedValue nutzt nur 1 Bit pro Edge
- Custom Model hat minimalen Overhead beim Routing
