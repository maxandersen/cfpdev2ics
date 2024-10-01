///usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+
//JAVAC_OPTIONS -parameters

//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.15.1}@pom
//DEPS io.quarkus:quarkus-rest-client
//DEPS io.quarkus:quarkus-rest-client-jackson
//DEPS io.quarkus:quarkus-picocli
//DEPS net.sf.biweekly:biweekly:0.6.8

//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=WARN
//Q:CONFIG quarkus.rest-client.devoxxcfp.url=https://dvbe24.cfp.dev

import static java.lang.System.out;
import static java.time.ZonedDateTime.parse;
import static java.util.Date.from;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestResponse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.io.TimezoneAssignment;
import biweekly.io.text.ICalReader;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import picocli.CommandLine;

// api docs:https://dvbe24.cfp.dev/swagger-ui/index.html

@CommandLine.Command
public class cfpdev2ics implements Runnable {

    @CommandLine.Option(names = "-o", description = "Output file", defaultValue = "devoxxbe-2024.ics")
    java.nio.file.Path outputFile;

    @RestClient
    DevoxxCfp devoxxCfp;

    @Override
    public void run() {

        String existingEtag = null;

        Optional<ICalendar> existingCalendar = getExistingCalendar();

        if(existingCalendar.isPresent()) {
            existingEtag = existingCalendar.get().getExperimentalProperty("X-ETAG").getValue();
        }

        ICalendar ical = setupCalendar();

        List<String> days = List.of("monday", "tuesday", "wednesday", "thursday", "friday");

        List<DevoxxCfp.Event> allEvents = new ArrayList<>();


        List<String> etags = new ArrayList<>();

        days.forEach(day -> {
            out.printf("Getting schedule for %s\n", day);
            var response = devoxxCfp.getScheduleForDay(day);
            var schedule = response.getEntity();
            allEvents.addAll(schedule);

            etags.add(response.getEntityTag().getValue());

            out.printf("%s has %s events with etag %s\n", day, schedule.size(), response.getEntityTag().getValue());

        });

        String combinedEtags = etags.stream().collect(Collectors.joining(":"));
        out.printf("Combined etag: %s\n", combinedEtags);
        out.printf("Existing etag: %s\n", existingEtag);    
        if(combinedEtags.equals(existingEtag)) {
            out.println("No new events, skipping update");
            return;
        }

        ical.setExperimentalProperty("X-ETAG", etags.stream().collect(Collectors.joining(":")));
        
        for (DevoxxCfp.Event event : allEvents) {
            VEvent vevent = setupVEvent(event);
            ical.addEvent(vevent);
        }

        try {
                out.printf("Writing to %s\n", outputFile);
                Files.writeString(outputFile, Biweekly.write(ical).go());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write ical to file", e);
        }
        
    }

    @Singleton
    static public class RegisterCustomModuleCustomizer implements ObjectMapperCustomizer {

        public void customize(ObjectMapper mapper) {
            // just in case to spot errors.
            // if want to optimize and only map extact fields requested, remove this.
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        }
    }

    
    private VEvent setupVEvent(DevoxxCfp.Event event) {
        VEvent vevent = new VEvent();
        vevent.setUid(event.id() + "-" + slug(event) + "@cfp.dev"); // a stable unique id
        vevent.setSummary(fullTitle(event));

        vevent.setDateStart(from(parse(event.fromDate()).toInstant()));
        vevent.setDateEnd(from(parse(event.toDate()).toInstant()));

        vevent.setLocation(fullLocation(event));
        vevent.setDescription(fullDescription(event));
        return vevent;
    }

    Optional<ICalendar> getExistingCalendar() {
        if(Files.exists(outputFile)) {
            try (var reader = new ICalReader(outputFile.toFile())) {
                return Optional.of(reader.readNext());
            } catch (IOException e) {
                System.err.println("Failed to read existing calendar: %s".formatted(e.getMessage()));
                return Optional.empty();
            }
            
        } else {
            return Optional.empty();
        }

    }

    private ICalendar setupCalendar() {
        ICalendar ical = new ICalendar();
        ical.setProductId("-//cfp2dev2ics 1.0//EN");

        TimeZone javaTz = TimeZone.getTimeZone("Europe/Brussels");
        TimezoneAssignment brussels = TimezoneAssignment.download(javaTz, false);

        ical.getTimezoneInfo().setDefaultTimezone(brussels);
        return ical;
    }

    // used llm to find emojis for each session type
    public static final Map<String, String> styleIcons = new HashMap<>() {
        {
            put("Afterparty", "\uD83C\uDF89"); // 🎉
            put("BOF", "\uD83D\uDCAC"); // 💬
            put("Break", "\uD83D\uDE34"); // 😴
            put("Breakfast & Exhibition opens", "\uD83C\uDF73"); // 🍳
            put("Breakfast", "\uD83C\uDF73"); // 🍳
            put("Closing Keynote", "\uD83D\uDCAC"); // 💬
            put("Coffee Break", "\u2615"); // ☕
            put("Conference", "\uD83D\uDCCB"); // 📋
            put("Deep Dive", "\uD83D\uDD0D"); // 🔍
            put("Hands-On Lab", "\uD83D\uDCBB"); // 💻
            put("Hands-on Lab", "\uD83D\uDCBB"); // 💻
            put("Keynote", "\uD83D\uDCAC"); // 💬
            put("Lunch Talk", "\uD83C\uDF74"); // 🍴
            put("Lunch", "\uD83C\uDF74"); // 🍴
            put("Meet & Greet in exhibition floor until 20h00", "\uD83D\uDC6B"); // 👫
            put("Meet and Greet", "\uD83D\uDC6B"); // 👫
            put("Movie", "\uD83C\uDFAC"); // 🎬
            put("Registration & Breakfast", "\uD83C\uDF73"); // 🍳
            put("Safe travels home", "\uD83D\uDEEB"); // 🛫
            put("Tools-in-Action", "\uD83D\uDEE0"); // 🛠
        }
    };

    String fullTitle(DevoxxCfp.Event event) {
        if (event.proposal() == null) {
            return String.format("%s: %s", styleIcons.get(event.sessionType().name()), event.eventName());
        } else {
            return String.format("%s: %s", styleIcons.get(event.sessionType().name()), event.proposal().title());
        }
    }

    String fullLocation(DevoxxCfp.Event event) {
        if (event.proposal() == null || event.proposal().speakers() == null) {
            return event.room().name();
        } else {
            return String.format("%s (%s) [%s]",
                    event.room().name(),
                    event.proposal().speakers().stream().map(DevoxxCfp.Speaker::fullName)
                            .collect(Collectors.joining(", ")),
                    event.proposal().totalFavourites());
        }
    }

    String slug(DevoxxCfp.Event event) {
        return event.proposal() == null ? null
                : event.proposal().title().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "")
                        .replaceAll("-+$", "");
    }

    String link(DevoxxCfp.Event event) {
        // didn't locate where this is in the rest api so handcrafting it.
        // turn "Back to the 80s - NES Assembly programming" into
        // "back-to-the-80s---nes-assembly-programming"
        return "https://devoxx.be/talk/" + slug(event);
    }

    String fullDescription(DevoxxCfp.Event event) {
        if (event.proposal() == null) {
            return "";
        } else {
            return "<a href=\"%s\">%s - %s - %s</a><br><br>%s<br><br>Speakers:<br>%s<br><br>Favourites:<br>%s<br><br>Keywords:<br>%s<br>"
                    .formatted(
                            link(event),
                            event.sessionType().name(),
                            event.proposal().track().name(),
                            event.proposal().audienceLevel(),
                            event.proposal().description(),
                            event.proposal().speakers().stream().map(DevoxxCfp.Speaker::fullName)
                                    .collect(Collectors.joining("<br>")),
                            event.proposal().totalFavourites(),
                            event.proposal().keywords().stream().map(DevoxxCfp.Keyword::name)
                                    .collect(Collectors.joining("<br>")),
                            link(event));
        }
    }

    /**
     * Rest client for the Devoxx CFP API
     * Generated the records classes using llm.
     */
    @Path("/api")
    @RegisterRestClient(configKey = "devoxxcfp")
    public interface DevoxxCfp {

        @GET
        @Path("/public/schedules/{day}")
        RestResponse<List<Event>> getScheduleForDay(@PathParam("day") String day);
        // https://dvbe24.cfp.dev/swagger-ui/index.html

        public record Event(
                int id,
                String fromDate,
                String toDate,
                boolean overflow,
                boolean reserved,
                String remark,
                int eventId,
                String eventName,
                Room room,
                String streamId,
                SessionType sessionType,
                String track,
                Proposal proposal,
                String audienceLevel,
                String langName,
                String timezone,
                List<Speaker> speakers,
                List<String> tags,
                int totalFavourites) {
        }

        public record Room(
                int id,
                String name,
                int weight,
                int capacity) {
        }

        public record SessionType(
                int id,
                String name,
                int duration,
                boolean pause,
                String description,
                String cssColor) {
        }

        public record Proposal(
                int id,
                String title,
                String description,
                String summary,
                String afterVideoURL,
                String podcastURL,
                String audienceLevel,
                String language,
                int totalFavourites,
                Track track,
                SessionType sessionType,
                List<Speaker> speakers,
                List<Keyword> keywords,
                List<String> timeSlots) {
        }

        public record Track(
                int id,
                String name,
                String description,
                String imageURL) {
        }

        public record Speaker(
                int id,
                String firstName,
                String lastName,
                String fullName,
                String bio,
                String anonymizedBio,
                String company,
                String imageUrl,
                String twitterHandle,
                String linkedInUsername) {
        }

        public record Keyword(
                String name) {
        }

    }
}
