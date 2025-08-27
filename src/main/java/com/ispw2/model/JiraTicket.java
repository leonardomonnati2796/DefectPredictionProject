package com.ispw2.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class JiraTicket {

    private static final Logger log = LoggerFactory.getLogger(JiraTicket.class);

    private final String key;
    private final LocalDateTime creationDate;
    private final List<String> affectedVersions;
    private String fixCommitHash;
    private LocalDateTime resolutionDate;
    private int introductionVersionIndex = -1;
    private int openingVersionIndex = -1;
    private int fixedVersionIndex = -1;

    public JiraTicket(final String key, final LocalDateTime creationDate, final List<String> affectedVersions) {
        this.key = key;
        this.creationDate = creationDate;
        this.affectedVersions = affectedVersions;
        if (log.isDebugEnabled()) {
            log.debug("New JiraTicket created -> Key: {}, CreationDate: {}, AffectedVersions: {}", key, creationDate, affectedVersions);
        }
    }

    // Getters
    public String getKey() { return key; }
    public LocalDateTime getCreationDate() { return creationDate; }
    public LocalDateTime getResolutionDate() { return resolutionDate; }
    public String getFixCommitHash() { return fixCommitHash; }
    public List<String> getAffectedVersions() { return affectedVersions; }
    public int getIntroductionVersionIndex() { return introductionVersionIndex; }
    public int getOpeningVersionIndex() { return openingVersionIndex; }
    public int getFixedVersionIndex() { return fixedVersionIndex; }

    // Setters
    public void setResolutionDate(final LocalDateTime resolutionDate) { 
        log.debug("Setting ResolutionDate for ticket {}: {}", this.key, resolutionDate);
        this.resolutionDate = resolutionDate; 
    }
    public void setFixCommitHash(final String fixCommitHash) { 
        log.debug("Setting FixCommitHash for ticket {}: {}", this.key, fixCommitHash);
        this.fixCommitHash = fixCommitHash; 
    }
    public void setIntroductionVersionIndex(final int iv) { 
        log.debug("Setting IntroductionVersionIndex for ticket {}: {}", this.key, iv);
        this.introductionVersionIndex = iv; 
    }
    public void setOpeningVersionIndex(final int ov) { 
        log.debug("Setting OpeningVersionIndex for ticket {}: {}", this.key, ov);
        this.openingVersionIndex = ov; 
    }
    public void setFixedVersionIndex(final int fv) { 
        log.debug("Setting FixedVersionIndex for ticket {}: {}", this.key, fv);
        this.fixedVersionIndex = fv; 
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return key.equals(((JiraTicket) o).key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "JiraTicket{" +
                "key='" + key + '\'' +
                ", creationDate=" + creationDate +
                ", resolutionDate=" + resolutionDate +
                ", fixCommitHash='" + fixCommitHash + '\'' +
                ", iv=" + introductionVersionIndex +
                ", ov=" + openingVersionIndex +
                ", fv=" + fixedVersionIndex +
                '}';
    }
}