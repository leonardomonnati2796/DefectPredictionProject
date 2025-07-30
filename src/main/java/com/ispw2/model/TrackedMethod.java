package com.ispw2.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class TrackedMethod {
    private final String id;
    private final String signature;
    private final String filepath;
    private final Map<String, Number> features = new HashMap<>();

    public TrackedMethod(final String id, final String signature, final String filepath) {
        this.id = id;
        this.signature = signature;
        this.filepath = filepath;
    }

    public String id() { return id; }
    public String signature() { return signature; }
    public String filepath() { return filepath; }
    public Map<String, Number> getFeatures() { return features; }

    public void addFeature(final String name, final Number value) {
        this.features.put(name, value);
    }
    
    public void addAllFeatures(final Map<String, Number> newFeatures) {
        this.features.putAll(newFeatures);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != this.getClass()) return false;
        final TrackedMethod that = (TrackedMethod) obj;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TrackedMethod[id=" + id + ", signature=" + signature + ", filepath=" + filepath + "]";
    }
}