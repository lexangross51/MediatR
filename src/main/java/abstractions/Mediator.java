package abstractions;

import abstractions.events.Event;
import abstractions.events.EventHandler;
import abstractions.requests.RequestHandler;
import abstractions.requests.Request;

import java.util.concurrent.CompletableFuture;

public interface Mediator {
    <TRequest extends Request<TResponse>, TResponse> void registerHandler(
            Class<TRequest> requestType, RequestHandler<TRequest, TResponse> requestHandler);

    <TEvent extends Event> void registerEventHandler(
            Class<TEvent> eventType, EventHandler<TEvent> eventHandler);

    <TRequest extends Request<TResponse>, TResponse> TResponse send(TRequest request);

    <TRequest extends Request<TResponse>, TResponse> CompletableFuture<TResponse> sendAsync(TRequest request);

    <TEvent extends Event> void publish(TEvent event);

    <TEvent extends Event> CompletableFuture<Void> publishAsync(TEvent event);
}