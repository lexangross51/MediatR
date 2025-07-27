package abstractions;

import abstractions.events.Event;
import abstractions.events.EventHandler;
import abstractions.requests.RequestHandler;
import abstractions.requests.Request;

public interface Mediator {
    <TRequest extends Request<TResponse>, TResponse> void registerHandler(
            Class<TRequest> requestType, RequestHandler<TRequest, TResponse> requestHandler);

    <TEvent extends Event> void registerEventHandler(
            Class<TEvent> eventType, EventHandler<TEvent> eventHandler);

    <TRequest extends Request<TResponse>, TResponse> TResponse send(TRequest request);

    <TEvent extends Event> void publish(TEvent event);
}