package abstractions.requestHandlers;

import abstractions.requests.Event;

public interface EventHandler<TEvent extends Event> extends RequestHandler<TEvent, Void> {}