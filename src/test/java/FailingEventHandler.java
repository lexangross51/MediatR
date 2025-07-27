import abstractions.events.EventHandler;

public class FailingEventHandler implements EventHandler<UserCreatedEvent> {
    @Override
    public void handle(UserCreatedEvent event) {
        throw new RuntimeException("Simulated failure");
    }
}
