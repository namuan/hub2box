#!/usr/bin/groovy

@Grapes([
	@Grab(group='org.apache.camel', module='camel-core', version='2.10.4'),
    @Grab(group='org.apache.camel', module='camel-mail', version='2.10.4'),
	@Grab(group='org.apache.camel', module='camel-exec', version='2.10.4'),
	@Grab('org.slf4j:slf4j-simple:1.6.6')
])

import org.apache.camel.*
import org.apache.camel.impl.*
import org.apache.camel.builder.*
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.util.jndi.*
import org.apache.camel.component.file.*
import org.apache.camel.Exchange.*

public class CloneJob {

    public static String regex = /(github.com\/.*)/
    public static String emailRegex = /.*\<(.*)\>/

    String name
    String url
    
    def void setName(fromEmail) {
        def matcher = fromEmail =~ emailRegex
        this.name = (matcher.size() ? matcher[0][1] : null) 
    }

    def void setUrl(mailBody) {
        def matcher = mailBody =~ regex
        this.url = "http://" + (matcher.size() ? matcher[0][1] : null)
    }

    def String getProjectName() {
        def projectName = url.substring(url.indexOf('com/') + 4, url.size()).replace('/', '_')

        if (projectName.getAt(projectName.size() - 1) == '_') {
            return projectName.substring(0, projectName.size() - 1)
        } else {
            return projectName
        }
    }
}

class GroovyMailRoute extends RouteBuilder {

    // TODO: To handle multipart messages
    static def String getBody(exchangeBody) {
        String mailBody = ''
        def bodyParts = exchangeBody.count
        println ("Total body parts: ${bodyParts}")
        for(int i = 0; i < bodyParts; i++) {
            def imapBodyPart = exchangeBody.getBodyPart(i)
            println imapBodyPart?.lineCount
            println imapBodyPart.getContentStream().getText()
            // mailBody << partBody
        }

        println ("mailbody --> ${mailBody}")
        return mailBody
    }

    def getUser(emailAddress) {
        def matcher = emailAddress =~ /(.*)@.*/
        if (matcher.size())
            return matcher[0][1]

        return null
    }

    @Override
    void configure(){

        def env = System.getenv()

        // externalise configuration
        def emailAddress = env['EMAIL']
        def emailPassword = env['PASSWORD']
        def smtpUser = getUser(emailAddress)

        if (!emailAddress || !emailPassword || !smtpUser)
            throw new IllegalArgumentException("Email address/SMTP user or password missing. Set environment variables EMAIL and PASSWORD")

        println "Using email: ${emailAddress}"

        from("imaps://imap.gmail.com?username=" + emailAddress
                                 + "&password=" + emailPassword
                                 + "&consumer.delay=30000")
            .filter(header("Subject").contains('GitHub'))
            .process(new Processor() {
                def void process(Exchange exchange) {
                    def cloneJob = new CloneJob()
                    cloneJob.setName(exchange.in.headers.from)
                    cloneJob.setUrl(exchange.in.body)
                    exchange.out.body = cloneJob.url
                    exchange.out.setHeader(Exchange.FILE_NAME, cloneJob.projectName)
                    exchange.out.setHeader("to", cloneJob.name)
                    exchange.out.setHeader("from", emailAddress)
                    exchange.out.setHeader("subject", ">> Hub2Box notification for repo: ${cloneJob.url}")

                    println("Picked up new message from: ${cloneJob.name} for repo: ${cloneJob.url}")
                }
            })
            .to("smtps://smtp.gmail.com?username=" + smtpUser 
                    + "&password=" + emailPassword
                    + "&contentType=text/html")
            .to('file://jobs')
            .to('exec://sh?args=clone_and_sync.sh')
            .to("log:groovymail?showAll=true&multiline=true")
    }
}


def camelCtx = new DefaultCamelContext()
camelCtx.addRoutes(new GroovyMailRoute())
//camelCtx.setTracing(true)
camelCtx.start()

addShutdownHook{ camelContext.stop() }
synchronized(this){ this.wait() }