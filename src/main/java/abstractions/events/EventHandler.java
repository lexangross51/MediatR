package abstractions.events;

public interface EventHandler<TEvent extends Event> {
    void handle(TEvent event);
}