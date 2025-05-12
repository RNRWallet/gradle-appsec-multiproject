
package com.fanniemae.appsec.ci

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException
import java.net.HttpURLConnection
import java.net.URL

class AppSecPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.task('checkHighSeverityVulnerabilities') {
            doLast {
                println '✅ Running Live OSS Index vulnerability scan...'

                // Dynamically collect Maven coordinates from runtimeClasspath
                def coordinates = []
                project.configurations.runtimeClasspath.resolvedConfiguration.resolvedArtifacts.each { artifact ->
                    def group = artifact.moduleVersion.id.group
                    def name = artifact.moduleVersion.id.name
                    def version = artifact.moduleVersion.id.version
                    coordinates << "pkg:maven/${group}/${name}@${version}"
                }

                println "\n Scanning ${coordinates.size()} dependencies..."
                coordinates.each { coordinate ->
                    println "Component: ${coordinate}"
                }

                def highSeverityThreshold = 7.0
                def foundVulnerabilities = []

                def vulnerabilities = fetchVulnerabilities(coordinates)

                vulnerabilities.each { component ->
                    component.vulnerabilities.each { issue ->
                        if (issue.cvssScore) {
                            def entry = [
                                score: issue.cvssScore,
                                severity: getSeverityLabel(issue.cvssScore),
                                line: issue.title
                            ]
                            foundVulnerabilities << entry
                        }
                    }
                }

                // === Print formatted vulnerability report ===
                println "\n==========================================="
                println "         Vulnerability Scan Report         "
                println "==========================================="
                println "High Severity Threshold: ${highSeverityThreshold}"
                println "Number of High Severity Vulnerabilities Found: ${foundVulnerabilities.size()}"
                println "===========================================\n"

                if (foundVulnerabilities.isEmpty()) {
                    println "✅ No high severity vulnerabilities found."
                } else {
                    println "❌ High severity vulnerabilities detected:\n"
                    println "----------------------------------------------------------------------------"
                    println String.format("| %-6s | %-9s | %-50s |", "Score", "Severity", "Details")
                    println "----------------------------------------------------------------------------"

                    foundVulnerabilities.each { vuln ->
                        println String.format("| %-6.1f | %-9s | %-50s |", vuln.score, vuln.severity, vuln.line)
                    }

                    println "----------------------------------------------------------------------------"
                    println "\n❌ Build failed due to ${foundVulnerabilities.size()} high severity vulnerabilities.\n"
                    throw new GradleException("Build failed due to ${foundVulnerabilities.size()} high severity vulnerabilities.")
                }
            }
        }
        // ✅ This is the magic line: make "build" depend on "checkHighSeverityVulnerabilities"
        // project.tasks.named('build') {
        //     dependsOn 'checkHighSeverityVulnerabilities'
        // }
    }

    private static List fetchVulnerabilities(List<String> coordinates) {
        def apiUrl = "https://ossindex.sonatype.org/api/v3/component-report"
        def jsonPayload = new JsonBuilder([coordinates: coordinates]).toPrettyString()

        def url = new URL(apiUrl)
        HttpURLConnection connection = (HttpURLConnection) url.openConnection()
        connection.setRequestMethod("POST")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        connection.outputStream.withWriter("UTF-8") { writer ->
            writer << jsonPayload
        }

        int responseCode = connection.responseCode
        if (responseCode != 200) {
            throw new GradleException("❌ OSS Index API call failed with status code: \$responseCode")
        }

        def responseJson = new JsonSlurper().parse(connection.inputStream)
        return responseJson
    }

    private static String getSeverityLabel(float score) {
        if (score >= 9.0) return "Critical"
        if (score >= 7.0) return "High"
        if (score >= 4.0) return "Medium"
        if (score > 0.0) return "Low"
        return "None"
    }
}
