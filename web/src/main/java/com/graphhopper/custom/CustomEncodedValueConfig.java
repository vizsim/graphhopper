package com.graphhopper.custom;

import java.util.Arrays;
import java.util.List;

public class CustomEncodedValueConfig {
    private String name = "custom_present";
    private String csvPath = "data/custom/custom_values.csv";
    private String csvColumnId = "way_id";
    private String csvColumnAttribute = "attribute";
    private List<String> trueValues = Arrays.asList("pano", "regular");

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCsvPath() {
        return csvPath;
    }

    public void setCsvPath(String csvPath) {
        this.csvPath = csvPath;
    }

    public String getCsvColumnId() {
        return csvColumnId;
    }

    public void setCsvColumnId(String csvColumnId) {
        this.csvColumnId = csvColumnId;
    }

    public String getCsvColumnAttribute() {
        return csvColumnAttribute;
    }

    public void setCsvColumnAttribute(String csvColumnAttribute) {
        this.csvColumnAttribute = csvColumnAttribute;
    }

    public List<String> getTrueValues() {
        return trueValues;
    }

    public void setTrueValues(List<String> trueValues) {
        this.trueValues = trueValues;
    }
}
