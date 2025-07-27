import abstractions.events.EventHandler;
import java.util.ArrayList;
import java.util.List;

public class LoggingEventHandler implements EventHandler<UserCreatedEvent> {
    public static List<String> log = new ArrayList<>();

    public void handle(UserCreatedEvent event) {
        log.add("User created: " + event.userName());
    }
}