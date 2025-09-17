package com.ispw2.model;

import java.time.LocalDate;

public record SoftwareRelease(String name, LocalDate releaseDate, int index) implements Comparable<SoftwareRelease> {
    @Override
    public int compareTo(final SoftwareRelease other) {
        return this.releaseDate.compareTo(other.releaseDate);
    }
}