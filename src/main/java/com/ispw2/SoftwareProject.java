package com.ispw2;

import java.util.Objects;

public final class SoftwareProject {
    private final String name;
    private final String gitUrl;

    public SoftwareProject(String name, String gitUrl) {
        this.name = name;
        this.gitUrl = gitUrl;
    }

    public String name() {
        return name;
    }

    public String gitUrl() {
        return gitUrl;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SoftwareProject that = (SoftwareProject) obj;
        return Objects.equals(name, that.name) && Objects.equals(gitUrl, that.gitUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, gitUrl);
    }

    @Override
    public String toString() {
        return "SoftwareProject{" +
                "name='" + name + '\'' +
                ", gitUrl='" + gitUrl + '\'' +
                '}';
    }
}