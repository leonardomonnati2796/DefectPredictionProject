package com.ispw2.model;

import java.time.LocalDate;

public record ProjectRelease(String name, LocalDate releaseDate, int index) implements Comparable<ProjectRelease> {
    @Override
    public int compareTo(final ProjectRelease other) {
        return this.releaseDate.compareTo(other.releaseDate);
    }
}