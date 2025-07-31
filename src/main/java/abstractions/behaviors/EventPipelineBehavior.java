package abstractions.behaviors;

import abstractions.events.Event;
import abstractions.events.EventHandler;

public interface EventPipelineBehavior<TEvent extends Event> {
    void handle(TEvent request, EventHandler<TEvent> next);
}