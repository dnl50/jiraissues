package de.lxklssn.jira

import groovyx.net.http.HttpResponseException
import groovyx.net.http.RESTClient
import org.apache.http.conn.scheme.Scheme
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.net.ssl.SSLContext

class JiraRESTClient extends RESTClient {

    private static Logger LOG = LoggerFactory.getLogger(JiraRESTClient)

    private static final String HTTPS = "https"
    private static final int HTTPS_PORT = 443
    private static final String TLS = "TLS"

    private static final String DATA = "data"
    private static final String ISSUES = "issues"

    private String url
    private String username
    private String password
    private def credentials = [:]

    JiraRESTClient(String url, String username, String password) {
        super(url)
        this.url = url
        getClient().getConnectionManager().getSchemeRegistry().register(new Scheme(HTTPS, HTTPS_PORT, createTLSSocketFactory()))

        this.username = username
        this.password = password

        credentials['os_username'] = this.username
        credentials['os_password'] = this.password
    }

    private TLSSocketFactory createTLSSocketFactory() {
        SSLContext sslContext = SSLContext.getInstance(TLS)
        sslContext.init(null, null, null)
        return new TLSSocketFactory(sslContext)
    }

    private def get(String path, def query) {

        try {
            def response = get(path: path, contentType: "application/json", query: query)
            return response
        } catch (HttpResponseException e) {
            if (e.response.status == 400) {
                throw new IllegalArgumentException("JIRA query failed, response data=${e.response.data}", e)
            } else {
                throw new IOException("JIRA connection failed, got HTTP status ${e.response.status}, response data=${e.response.data}", e)
            }
        }
    }

    private def post(String path, def body, def query) {

        try {
            def response = post(path: path, contentType: "application/json", body: body, query: query)
            return response
        } catch (HttpResponseException e) {
            if (e.response.status == 400) {
                throw new IllegalArgumentException("JIRA query failed, got HTTP status 400, response data=${e.response.data}", e)
            } else {
                throw new IOException("JIRA connection failed, got HTTP status ${e.response.status}, response data=${e.response.data}", e)
            }
        }
    }

    private List search(String jql) {
        return searchPaginated(jql, new ArrayList(), 0, 0)
    }

    private List searchPaginated(String jql, List allIssues, int startAtIssue, int totalResultsSoFar) {
        def query = [:]
        query << credentials
        query['jql'] = jql

        query['startAt'] = startAtIssue
        query['maxResults'] = 50

        def issuesResponse = get("search", query)
        def responseData = issuesResponse.getProperties().get(DATA)

        def newIssues = responseData.getAt(ISSUES) as List

        def maxResults = responseData.getAt("maxResults")
        def totalResults = responseData.getAt("total")

        totalResultsSoFar += newIssues.size()
        allIssues.addAll(newIssues)

        if (totalResultsSoFar < totalResults) {
            startAtIssue += maxResults
            searchPaginated(jql, allIssues, startAtIssue, totalResultsSoFar)
        }

        return allIssues
    }

    List getIssues(String jiraVersion, String jql) {
        jql += " AND fixVersion in ('${jiraVersion}')"
        LOG.debug("Querying JIRA server '{}' using JQL '{}'", this.url, jql)
        return search(jql)
    }
}
