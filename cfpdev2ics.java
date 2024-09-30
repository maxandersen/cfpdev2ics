///usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+
//JAVAC_OPTIONS -parameters

//SOURCES DevoxxCfp.java


// Update the Quarkus version to what you want here or run jbang with
// `-Dquarkus.version=<version>` to override it.
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.15.1}@pom
//DEPS io.quarkus:quarkus-rest-client
//DEPS io.quarkus:quarkus-rest-client-jackson
//DEPS io.quarkus:quarkus-picocli
//DEPS com.google.api-client:google-api-client:1.23.0
//DEPS com.google.oauth-client:google-oauth-client-jetty:1.23.0
//DEPS com.google.apis:google-api-services-calendar:v3-rev305-1.23.0


//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=WARN
//Q:CONFIG quarkus.rest-client.devoxxcfp.url=https://dvbe24.cfp.dev
import static java.lang.System.out;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;
import picocli.CommandLine;
import picocli.CommandLine.Option;

// https://dvbe24.cfp.dev/swagger-ui/index.html

@CommandLine.Command
public class cfpdev2ics implements Runnable {

    static final String APPLICATION_NAME = "cfpdev2ics";

    static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    static final String TOKENS_DIRECTORY_PATH = "tokens";

    @Option(names = { "--credentials" }, defaultValue = "credentials.json", description = "path to credentials")
    private File credentials;

    @Option(names = { "-c" }, defaultValue = "devoxxbe2024", description = "which calendar to use")
    private String calendar;

    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        var in = new FileInputStream(credentials);

        var clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        var flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        var receiver = new LocalServerReceiver.Builder().setPort(8765).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }


    Calendar buildCalendarService() {
        Calendar service = null;
        try {

            // Build a new authorized API client service.
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (GeneralSecurityException | IOException fe) {
            throw new IllegalStateException(
                    """
                    You are missing credentials for accessing Google API's.
                    Do the following:
                        1. Go to https://developers.google.com/calendar/quickstart/java
                        2. click 'Enable the Google Calendar API' 
                        3. Download credentials.json and put it in current working directory.
                        4. run it again
                    """,
                    fe);
        }
        return service;
    }

    

    @RestClient
    DevoxxCfp devoxxCfp;

    

    @Override
    public void run() {

        
        
        List<String> days = List.of("monday");//, "tuesday", "wednesday", "thursday", "friday");

        days.forEach(day -> {

            var schedule = devoxxCfp.getScheduleForDay(day);

            out.printf("%s has %s events\n", day, schedule.size());
        });

        Calendar calendarService = buildCalendarService();
        
        var devoxxcall = getDevoxxcall(calendarService);

        out.println("Devoxx Belgium 2024 calendar id: " + devoxxcall.getId());

        deleteEvents(calendarService, devoxxcall);

        days.forEach(day -> {

            var schedule = devoxxCfp.getScheduleForDay(day);

            addEvents(schedule, calendarService, devoxxcall);
        });
    }

    @Singleton
    static public class RegisterCustomModuleCustomizer implements ObjectMapperCustomizer {

        public void customize(ObjectMapper mapper) {
            // just in case to spot errors.
            // if want to optimize and only map extact fields requested, remove this.
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        }
    }

    private void addEvents(List<DevoxxCfp.Event> devents, Calendar service, CalendarListEntry devoxxcall) {
        for (DevoxxCfp.Event event : devents) {
            out.println("adding " + event.id() + " " + (event.proposal() != null ? event.proposal().title() : event.eventName()));
            try {
                service.events().insert(devoxxcall.getId(), new Event()
                        .setSummary(event.fullTitle())
                        .setLocation(event.fullLocation())
                        
                        .setDescription(event.fullDescription())
                        .setStart(new EventDateTime()
                                .setDateTime(new DateTime(event.fromDate()))
                                .setTimeZone("Europe/Brussels"))
                        .setEnd(new EventDateTime()
                                .setDateTime(new DateTime(event.toDate()))
                                .setTimeZone("Europe/Brussels"))
                       // .setStart(new EventDateTime()
                      //          .setDateTime(event.get new DateTime(event.date() + "T" + event.start() + ":00+02:00"))
                       //         .setTimeZone("Europe/Brussels"))
                      //  .setEnd(new EventDateTime()
                       //         .setDateTime(new DateTime(event.date() + "T" + event.finish() + ":00+02:00"))
                       //         .setTimeZone("Europe/Brussels"))
                       )
                        .execute();
            } catch (IOException e) {
                throw new IllegalStateException("Error adding event " + event, e);
            }   
        }
    }

    private void deleteEvents(Calendar service, CalendarListEntry devoxxcall) {
        // Fetch the current date
        DateTime now = new DateTime(System.currentTimeMillis());
        // delete the events from the devoxx calendar.
        Events events;
        try {
            events = service.events().list(devoxxcall.getId())
                   // .setMaxResults(1000)
                    .setTimeMin(now)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
        
        List<Event> items = events.getItems();
        for (Event event : items) {
            try {
                out.println("\uD83D\uDC80 deleting " + event.getSummary());
                
                service.events().delete(devoxxcall.getId(), event.getId()).execute();
            } catch (IOException e1) {
                out.println(e1);
            }  
        }
    } catch (IOException e) {
       throw new IllegalStateException("Error deleting events", e);
    }
    }

    private CalendarListEntry getDevoxxcall(Calendar service) {
        try {
            return service.calendarList()
                    .list()
                    .execute()
                    .getItems()
                    .stream()
                    .filter(calendarListEntry -> calendar.equals(calendarListEntry.getSummary()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Calendar not found"));
        } catch (IllegalArgumentException | IOException e) {
            throw new IllegalStateException("Calendar " + calendar + " not found", e);
        }
    }

   
}
