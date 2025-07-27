import abstractions.events.Event;

public record UserCreatedEvent(String userName) implements Event {}