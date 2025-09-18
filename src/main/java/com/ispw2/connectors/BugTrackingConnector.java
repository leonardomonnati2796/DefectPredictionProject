package com.ispw2.connectors;

import com.ispw2.model.BugReport;
import com.ispw2.model.SoftwareRelease;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BugTrackingConnector {
    private static final Logger log = LoggerFactory.getLogger(BugTrackingConnector.class);
    private final String projectKey;
    private static final String JIRA_URL = "https://issues.apache.org/jira";
    private static final DateTimeFormatter JIRA_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    /**
     * Constructs a new BugTrackingConnector for JIRA operations.
     * 
     * @param projectKey JIRA project key (e.g., "BOOKKEEPER", "OPENJPA")
     */
    public BugTrackingConnector(final String projectKey) {
        this.projectKey = projectKey;
        log.debug("BugTrackingConnector initialized for project key: {}", projectKey);
    }

    /**
     * Fetches all project releases from JIRA that have been released and have a release date.
     * 
     * @return List of software releases sorted by date with assigned indices
     * @throws IOException If JIRA API request fails
     */
    public List<SoftwareRelease> getProjectReleases() throws IOException {
        log.info("Fetching project releases from JIRA for project {}...", projectKey);
        final String url = String.format("%s/rest/api/2/project/%s/versions", JIRA_URL, projectKey);
        final String jsonResponse = sendGetRequest(url);

        final JSONArray versions = new JSONArray(jsonResponse);
        final List<SoftwareRelease> releases = new ArrayList<>();
        log.debug("Found {} versions in total from JIRA API. Filtering for released versions with a date...", versions.length());
        for (int i = 0; i < versions.length(); i++) {
            final JSONObject version = versions.getJSONObject(i);
            if (version.optBoolean("released") && version.has("releaseDate")) {
                final LocalDate releaseDate = LocalDate.parse(version.getString("releaseDate"));
                releases.add(new SoftwareRelease(version.getString("name"), releaseDate, 0));
                if(log.isDebugEnabled()){
                    log.debug("  -> Found valid release: {} ({})", version.getString("name"), releaseDate);
                }
            }
        }

        releases.sort(Comparator.naturalOrder());
        
        List<SoftwareRelease> indexedReleases = IntStream.range(0, releases.size())
                .mapToObj(i -> new SoftwareRelease(releases.get(i).name(), releases.get(i).releaseDate(), i + 1))
                .collect(Collectors.toList());
        
        log.info("Found {} valid and sorted releases for project {}.", indexedReleases.size(), projectKey);
        return indexedReleases;
    }

    /**
     * Fetches all bug tickets from JIRA that are resolved/closed with status Fixed.
     * Uses pagination to handle large result sets.
     * 
     * @return List of bug reports
     * @throws IOException If JIRA API request fails
     */
    public List<BugReport> getBugTickets() throws IOException {
        log.info("Fetching bug tickets from JIRA for project {} (this may take a while)...", projectKey);
        final List<BugReport> tickets = new ArrayList<>();
        int startAt = 0;
        final int maxResults = 100;
        int total;

        do {
            final String jql = String.format("project = '%s' AND issuetype = Bug AND status in (Resolved, Closed) AND resolution = Fixed ORDER BY created ASC", projectKey);
            final String url = String.format("%s/rest/api/2/search?jql=%s&fields=key,created,resolutiondate,versions&startAt=%d&maxResults=%d",
                    JIRA_URL, URLEncoder.encode(jql, StandardCharsets.UTF_8), startAt, maxResults);
            
            log.debug("Executing JQL query with URL: {}", url);

            final String jsonResponse = sendGetRequest(url);
            final JSONObject response = new JSONObject(jsonResponse);
            final JSONArray issues = response.getJSONArray("issues");
            log.debug("  -> Received {} issues in the current page.", issues.length());

            if (issues.isEmpty()) {
                log.warn("Received an empty page of issues before reaching total. Stopping fetch.");
                break;
            }

            for (int i = 0; i < issues.length(); i++) {
                parseTicketFromJson(issues.getJSONObject(i)).ifPresent(tickets::add);
            }
            
            total = response.getInt("total");
            startAt += issues.length();
            if (log.isInfoEnabled()) {
                log.info("  -> Fetched {} of {} tickets...", startAt, total);
            }
        } while (startAt < total);
        
        log.info("Total valid bug tickets fetched for {}: {}", projectKey, tickets.size());
        return tickets;
    }

    /**
     * Parses a JIRA issue JSON object into a BugReport object.
     * 
     * @param issueJson The JSON object representing a JIRA issue
     * @return Optional containing the parsed BugReport or empty if parsing fails
     */
    private Optional<BugReport> parseTicketFromJson(final JSONObject issueJson) {
        try {
            final String key = issueJson.getString("key");
            final JSONObject fields = issueJson.getJSONObject("fields");
            final String createdString = fields.getString("created");

            log.debug("    Parsing ticket with key: {}", key);

            if (key.isEmpty() || createdString.isEmpty()) {
                log.warn("    Skipping ticket {} due to missing key or creation date.", key);
                return Optional.empty();
            }

            final LocalDateTime created = ZonedDateTime.parse(createdString, JIRA_DATE_FORMATTER).toLocalDateTime();
            final List<String> affectedVersions = new ArrayList<>();
            if (fields.has("versions")) {
                final JSONArray avs = fields.getJSONArray("versions");
                for (int j = 0; j < avs.length(); j++) {
                    affectedVersions.add(avs.getJSONObject(j).getString("name"));
                }
            }
            log.debug("      -> Successfully parsed ticket {} with {} affected versions.", key, affectedVersions.size());
            return Optional.of(new BugReport(key, created, affectedVersions));
        } catch (final JSONException e) {
            log.warn("Skipping malformed JIRA ticket for project {}. Reason: {}", this.projectKey, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Sends an HTTP GET request to the specified URL and returns the response body.
     * 
     * @param url The URL to send the request to
     * @return The response body as a string
     * @throws IOException If the HTTP request fails
     */
    private String sendGetRequest(final String url) throws IOException {
        log.debug("Sending HTTP GET request to: {}", url);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final HttpGet request = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                log.debug("  -> Received HTTP status code: {}", statusCode);
                if (statusCode != 200) {
                    throw new IOException("JIRA API request failed: " + response.getStatusLine().toString() + " for URL: " + url);
                }
                return EntityUtils.toString(response.getEntity());
            }
        }
    }
}