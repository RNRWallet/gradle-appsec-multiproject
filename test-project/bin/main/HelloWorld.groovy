@Grab(group='org.apache.groovy', module='groovy-json', version='4.0.15')
@Grab(group='org.apache.httpcomponents.client5', module='httpclient5', version='5.3')

import groovy.json.JsonSlurper
import org.apache.hc.client5.http.fluent.Request

class HelloWorld {
    static void main(String[] args) {
        println "Calling API..."

        // Call API and get JSON string
        String url = 'https://jsonplaceholder.typicode.com/todos/1'
        String response = Request.get(url)
                                  .execute()
                                  .returnContent()
                                  .asString()

        // Parse JSON
        def json = new JsonSlurper().parseText(response)

        // Print fields
        println "########################################"
        println "ID      : ${json.id}"
        println "Title   : ${json.title}"
        println "Completed: ${json.completed}"
        println "########################################"
    }
}
