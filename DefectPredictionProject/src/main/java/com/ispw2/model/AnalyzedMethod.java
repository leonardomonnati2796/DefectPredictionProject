package com.ispw2.model;

import com.ispw2.util.LoggingUtils;
import com.ispw2.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class AnalyzedMethod {
    private static final Logger log = LoggerFactory.getLogger(AnalyzedMethod.class);

    private final String id;
    private final String signature;
    private final String filepath;
    private final Map<String, Number> features = new HashMap<>();

    public AnalyzedMethod(final String id, final String signature, final String filepath) {
        this.id = id;
        this.signature = signature;
        this.filepath = filepath;
        LoggingUtils.debugIfEnabled(log, "Creating new AnalyzedMethod: id={}, signature='{}', filepath='{}'", id, signature, filepath);
    }

    public String id() { return id; }
    public String signature() { return signature; }
    public String filepath() { return filepath; }
    public Map<String, Number> getFeatures() { return CollectionUtils.defensiveCopy(features); }

    public void addFeature(final String name, final Number value) {
        LoggingUtils.debugIfEnabled(log, "Adding feature to method {}: {} = {}", this.id, name, value);
        this.features.put(name, value);
    }
    
    public void addAllFeatures(final Map<String, Number> newFeatures) {
        LoggingUtils.debugIfEnabled(log, "Adding {} features to method {}: {}", newFeatures.size(), this.id, newFeatures);
        this.features.putAll(newFeatures);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final AnalyzedMethod that = (AnalyzedMethod) obj;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return new StringBuilder("AnalyzedMethod[")
                .append("id=").append(id)
                .append(", signature='").append(signature).append('\'')
                .append(", filepath='").append(filepath).append('\'')
                .append(", features=").append(features)
                .append(']')
                .toString();
    }
}