package com.ispw2;

import java.util.Objects;

/**
 * Represents a software project with its name and Git repository URL.
 * This class is immutable and provides access to project identification information.
 */
public final class SoftwareProject {
    private final String name;
    private final String gitUrl;

    /**
     * Constructs a new SoftwareProject with the specified name and Git URL.
     * 
     * @param name The name of the software project
     * @param gitUrl The Git repository URL for the project
     */
    public SoftwareProject(String name, String gitUrl) {
        this.name = name;
        this.gitUrl = gitUrl;
    }

    /**
     * Returns the name of the software project.
     * 
     * @return The project name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the Git repository URL of the software project.
     * 
     * @return The Git URL
     */
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