import abstractions.Mediator;
import abstractions.behaviors.EventPipelineBehavior;
import abstractions.behaviors.RequestPipelineBehavior;
import abstractions.commands.ResultCommand;
import abstractions.events.Event;
import abstractions.events.EventHandler;
import abstractions.queries.Query;
import abstractions.requests.Request;
import abstractions.requests.RequestHandler;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.*;

class MediatorImpl implements Mediator {
    private final ConcurrentMap<Class<?>, RequestHandler<?, ?>> commandHandlers;
    private final ConcurrentMap<Class<?>, RequestHandler<?, ?>> queryHandlers;
    private final ConcurrentMap<Class<?>, List<EventHandler<?>>> eventHandlers;
    private final List<RegisteredRequestBehavior> requestBehaviors;
    private final List<RegisteredEventBehavior> eventBehaviors;
    private final Executor executor;

    public MediatorImpl() {
        this.commandHandlers = new ConcurrentHashMap<>();
        this.queryHandlers = new ConcurrentHashMap<>();
        this.eventHandlers = new ConcurrentHashMap<>();
        this.requestBehaviors = new ArrayList<>();
        this.eventBehaviors = new ArrayList<>();
        this.executor = Executors.newCachedThreadPool();
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

        RequestHandler<TRequest, TResponse> current = handler;
        ListIterator<RegisteredRequestBehavior> it = this.requestBehaviors.listIterator(this.requestBehaviors.size());

        while (it.hasPrevious()) {
            RegisteredRequestBehavior rb = it.previous();

            if (rb.requestClass == null || rb.requestClass.isAssignableFrom(requestType)) {
                current = wrapRequestHandler(rb.behavior, current);
            }
        }

        return current.handle(request);
    }

    @SuppressWarnings("unchecked")
    private <TRequest extends Request<TResponse>, TResponse> RequestHandler<TRequest, TResponse> wrapRequestHandler(
            RequestPipelineBehavior<?, ?> raw,
            RequestHandler<TRequest, TResponse> next) {
        return request -> {
            var typed = (RequestPipelineBehavior<TRequest, TResponse>) raw;
            return typed.handle(request, next);
        };
    }

    @Override
    public <TRequest extends Request<TResponse>, TResponse> CompletableFuture<TResponse> sendAsync(TRequest request) {
        return CompletableFuture.supplyAsync(() -> send(request), this.executor);
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

            var current = (EventHandler<TEvent>)handler;
            ListIterator<RegisteredEventBehavior> it = this.eventBehaviors.listIterator(this.eventBehaviors.size());

            while (it.hasPrevious()) {
                RegisteredEventBehavior rb = it.previous();

                if (rb.eventClass == null || rb.eventClass.isAssignableFrom(event.getClass())) {
                    current = wrapEventHandler(rb.behavior, current);
                }
            }


            ((EventHandler<TEvent>)handler).handle(event);
        });
    }

    @SuppressWarnings("unchecked")
    private <TEvent extends Event> EventHandler<TEvent> wrapEventHandler(
            EventPipelineBehavior<?> raw,
            EventHandler<TEvent> next) {
        return event -> {
            var typed = (EventPipelineBehavior<TEvent>) raw;
            typed.handle(event, next);
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <TEvent extends Event> CompletableFuture<Void> publishAsync(TEvent event) {
        List<EventHandler<?>> handlers = eventHandlers.get(event.getClass());

        if (handlers == null) {
            return CompletableFuture.completedFuture(null);
        }

        var futures = new ArrayList<CompletableFuture<Void>>();

        for (var rawHandler : handlers) {
            EventHandler<TEvent> current = (EventHandler<TEvent>) rawHandler;

            ListIterator<RegisteredEventBehavior> it = this.eventBehaviors.listIterator(this.eventBehaviors.size());
            while (it.hasPrevious()) {
                RegisteredEventBehavior rb = it.previous();

                if (rb.eventClass == null || rb.eventClass.isAssignableFrom(event.getClass())) {
                    current = wrapEventHandler(rb.behavior, current);
                }
            }

            var finalCurrent = current;
            futures.add(CompletableFuture.runAsync(() -> finalCurrent.handle(event), executor));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public <TRequest extends Request<TResponse>, TResponse> void registerRequestPipelineBehavior(
            RequestPipelineBehavior<TRequest, TResponse> behavior) {
        Class<?> behaviorClass = behavior.getClass();
        Type[] genericInterfaces = behaviorClass.getGenericInterfaces();

        for (var type : genericInterfaces) {
            if (type instanceof ParameterizedType pt &&
                pt.getRawType() instanceof Class raw &&
                RequestPipelineBehavior.class.isAssignableFrom(raw)){
                Type requestType = pt.getActualTypeArguments()[0];

                if (requestType instanceof Class<?> requestClass &&
                    !requestClass.equals(Request.class)) {
                    this.requestBehaviors.add(new RegisteredRequestBehavior(requestClass, behavior));
                    return;
                }
            }
        }

        this.requestBehaviors.add(new RegisteredRequestBehavior(null, behavior));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public <TEvent extends Event> void registerEventPipelineBehavior(EventPipelineBehavior<TEvent> behavior) {
        Class<?> behaviorClass = behavior.getClass();
        Type[] genericInterfaces = behaviorClass.getGenericInterfaces();

        for (var type : genericInterfaces) {
            if (type instanceof ParameterizedType pt &&
                    pt.getRawType() instanceof Class raw &&
                    EventPipelineBehavior.class.isAssignableFrom(raw)){
                Type requestType = pt.getActualTypeArguments()[0];

                if (requestType instanceof Class<?> eventClass &&
                        !eventClass.equals(Event.class)) {
                    this.eventBehaviors.add(new RegisteredEventBehavior(eventClass, behavior));
                    return;
                }
            }
        }

        this.eventBehaviors.add(new RegisteredEventBehavior(null, behavior));
    }

    private record RegisteredRequestBehavior(Class<?> requestClass, RequestPipelineBehavior<?, ?> behavior) {}

    private record RegisteredEventBehavior(Class<?> eventClass, EventPipelineBehavior<?> behavior) {}
}