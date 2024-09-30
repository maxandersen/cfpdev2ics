import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/api")
@RegisterRestClient(configKey = "devoxxcfp")
public interface DevoxxCfp {

    @GET()
    @Path("/public/schedules")
    ScheduleLinks getSchedules();

    @GET
    @Path("/public/schedules/{day}")
    List<Event> getScheduleForDay(@PathParam("day") String day);

     // https://dvbe24.cfp.dev/swagger-ui/index.html

     public record ScheduleLinks(List<ScheduleLink> links) {
    }

    public record ScheduleLink(String href, String title) {
    }

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
    int totalFavourites
) {

        String fullTitle() {
            if(proposal()==null) {
                return String.format("%s - %s", eventName, sessionType.name());
            } else {
                return String.format("%s - %s", proposal().title(), sessionType().name());
            }
        }

        String fullLocation() {
            if(proposal()==null || proposal().speakers()==null) {
                return room.name();
            } else {
                return String.format("%s (%s)", room.name(), proposal().speakers().stream().map(Speaker::fullName).collect(Collectors.joining(", ")));
            }
        }

        String link() {
            //turn "Back to the 80s - NES Assembly programming" into "back-to-the-80s---nes-assembly-programming"
            String slug = proposal()==null?null:proposal().title().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "").replaceAll("-+$", "");
            return "https://devoxx.be/talk/" + slug;
        }

        String fullDescription() {
            if(proposal()==null) {
                return "";
            } else {
                // didn't locate where this is in the rest api so handcrafting it.
                return "%s\n\n%s".formatted(link(), proposal().description());
            }
        }
      /*   String description() {
            return String.format(
                    "%s\n\n%s", 
                    Objects.toString(speaker(),""), 
                    Objects.toString(link(),""));  
                 }*/

}

public record Room(
    int id,
    String name,
    int weight,
    int capacity
) {}

public record SessionType(
    int id,
    String name,
    int duration,
    boolean pause,
    String description,
    String cssColor
) {}

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
    List<String> timeSlots
) {}

public record Track(
    int id,
    String name,
    String description,
    String imageURL
) {}

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
    String linkedInUsername
) {}

public record Keyword(
    String name
) {}

}