import abstractions.Mediator;
import abstractions.commands.ResultCommand;
import abstractions.events.Event;
import abstractions.events.EventHandler;
import abstractions.queries.Query;
import abstractions.requests.Request;
import abstractions.requests.RequestHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class MediatorImpl implements Mediator {
    private final ConcurrentMap<Class<?>, RequestHandler<?, ?>> commandHandlers;
    private final ConcurrentMap<Class<?>, RequestHandler<?, ?>> queryHandlers;
    private final ConcurrentMap<Class<?>, List<EventHandler<?>>> eventHandlers;

    public MediatorImpl() {
        this.commandHandlers = new ConcurrentHashMap<>();
        this.queryHandlers = new ConcurrentHashMap<>();
        this.eventHandlers = new ConcurrentHashMap<>();
    }

    @Override
    public <TRequest extends Request<TResponse>, TResponse> void registerHandler(
            Class<TRequest> requestType, RequestHandler<TRequest, TResponse> requestHandler) {

        if (ResultCommand.class.isAssignableFrom(requestType)) {
            if (this.commandHandlers.containsKey(requestType)) {
                throw new RuntimeException("A handler for this request type has already been added");
            }

            this.commandHandlers.put(requestType, requestHandler);
            return;
        }

        if (Query.class.isAssignableFrom(requestType)) {
            if (this.queryHandlers.containsKey(requestType)) {
                throw new RuntimeException("A handler for this request type has already been added");
            }

            this.queryHandlers.put(requestType, requestHandler);
            return;
        }

        throw new RuntimeException("Unknown request type");
    }

    @Override
    public <TEvent extends Event> void registerEventHandler(Class<TEvent> eventType, EventHandler<TEvent> eventHandler) {
        List<EventHandler<?>> handlers = eventHandlers.computeIfAbsent(eventType, _ -> new ArrayList<>());

        synchronized (handlers) {
            handlers.add(eventHandler);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <TRequest extends Request<TResponse>, TResponse> TResponse send(TRequest request) {
        RequestHandler<TRequest, TResponse> handler;
        Class<?> requestType = request.getClass();

        if (request instanceof ResultCommand) {
            handler = (RequestHandler<TRequest, TResponse>)this.commandHandlers.get(requestType);
        } else if (request instanceof Query) {
            handler = (RequestHandler<TRequest, TResponse>)this.queryHandlers.get(requestType);
        } else {
            throw new RuntimeException("Unknown request type");
        }

        if (handler == null) {
            throw new IllegalArgumentException("No handler registered");
        }

        return handler.handle(request);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <TEvent extends Event> void publish(TEvent event) {
        List<EventHandler<?>> handlers = this.eventHandlers.get(event.getClass());

        if (handlers == null) {
            return;
        }

        handlers.forEach(handler -> {
            if (handler == null) {
                throw new IllegalArgumentException("Unknown request type");
            }

            ((EventHandler<TEvent>)handler).handle(event);
        });
    }
}