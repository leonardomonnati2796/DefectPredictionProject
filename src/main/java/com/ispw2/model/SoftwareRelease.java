package com.ispw2.model;

import java.time.LocalDate;
import java.util.Objects;

public final class SoftwareRelease implements Comparable<SoftwareRelease> {
    private final String name;
    private final LocalDate releaseDate;
    private final int index;

    public SoftwareRelease(String name, LocalDate releaseDate, int index) {
        this.name = name;
        this.releaseDate = releaseDate;
        this.index = index;
    }

    public String name() {
        return name;
    }

    public LocalDate releaseDate() {
        return releaseDate;
    }

    public int index() {
        return index;
    }

    @Override
    public int compareTo(final SoftwareRelease other) {
        return this.releaseDate.compareTo(other.releaseDate);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SoftwareRelease that = (SoftwareRelease) obj;
        return index == that.index && 
               Objects.equals(name, that.name) && 
               Objects.equals(releaseDate, that.releaseDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, releaseDate, index);
    }

    @Override
    public String toString() {
        return "SoftwareRelease{" +
                "name='" + name + '\'' +
                ", releaseDate=" + releaseDate +
                ", index=" + index +
                '}';
    }
}