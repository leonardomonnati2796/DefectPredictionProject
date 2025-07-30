package com.ispw2.connectors;

import com.ispw2.model.JiraTicket;
import com.ispw2.model.ProjectRelease;
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

public class JiraConnector {
    private static final Logger log = LoggerFactory.getLogger(JiraConnector.class);
    private final String projectKey;
    private static final String JIRA_URL = "https://issues.apache.org/jira";
    private static final DateTimeFormatter JIRA_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public JiraConnector(final String projectKey) {
        this.projectKey = projectKey;
    }

    public List<ProjectRelease> getProjectReleases() throws IOException {
        log.info("Fetching project releases from JIRA...");
        final String url = String.format("%s/rest/api/2/project/%s/versions", JIRA_URL, projectKey);
        final String jsonResponse = sendGetRequest(url);

        final JSONArray versions = new JSONArray(jsonResponse);
        final List<ProjectRelease> releases = new ArrayList<>();
        for (int i = 0; i < versions.length(); i++) {
            final JSONObject version = versions.getJSONObject(i);
            if (version.optBoolean("released") && version.has("releaseDate")) {
                final LocalDate releaseDate = LocalDate.parse(version.getString("releaseDate"));
                releases.add(new ProjectRelease(version.getString("name"), releaseDate, 0));
            }
        }

        releases.sort(Comparator.naturalOrder());
        return IntStream.range(0, releases.size())
                .mapToObj(i -> new ProjectRelease(releases.get(i).name(), releases.get(i).releaseDate(), i + 1))
                .collect(Collectors.toList());
    }

    public List<JiraTicket> getBugTickets() throws IOException {
        log.info("Fetching bug tickets from JIRA (this may take a while)...");
        final List<JiraTicket> tickets = new ArrayList<>();
        int startAt = 0;
        final int maxResults = 100;
        int total;

        do {
            final String jql = String.format("project = '%s' AND issuetype = Bug AND status in (Resolved, Closed) AND resolution = Fixed ORDER BY created ASC", projectKey);
            final String url = String.format("%s/rest/api/2/search?jql=%s&fields=key,created,resolutiondate,versions&startAt=%d&maxResults=%d",
                    JIRA_URL, URLEncoder.encode(jql, StandardCharsets.UTF_8), startAt, maxResults);

            final String jsonResponse = sendGetRequest(url);
            final JSONObject response = new JSONObject(jsonResponse);
            final JSONArray issues = response.getJSONArray("issues");

            if (issues.isEmpty()) {
                break;
            }

            for (int i = 0; i < issues.length(); i++) {
                parseTicketFromJson(issues.getJSONObject(i)).ifPresent(tickets::add);
            }
            
            total = response.getInt("total");
            startAt += issues.length();
            log.info("  -> Fetched {} of {} tickets...", startAt, total);
        } while (startAt < total);
        
        log.info("Total valid bug tickets fetched: {}", tickets.size());
        return tickets;
    }

    private Optional<JiraTicket> parseTicketFromJson(final JSONObject issueJson) {
        try {
            final String key = issueJson.getString("key");
            final JSONObject fields = issueJson.getJSONObject("fields");
            final String createdString = fields.getString("created");

            if (key.isEmpty() || createdString.isEmpty()) {
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
            return Optional.of(new JiraTicket(key, created, affectedVersions));
        } catch (final JSONException e) {
            log.warn("Skipping malformed JIRA ticket for project {}. Reason: {}", this.projectKey, e.getMessage());
            return Optional.empty();
        }
    }

    private String sendGetRequest(final String url) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final HttpGet request = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new IOException("JIRA API request failed: " + response.getStatusLine().toString() + " for URL: " + url);
                }
                return EntityUtils.toString(response.getEntity());
            }
        }
    }
}