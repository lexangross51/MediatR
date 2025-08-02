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
        Class<?> requestClass = request.getClass();

        if (request instanceof ResultCommand) {
            handler = (RequestHandler<TRequest, TResponse>)this.commandHandlers.get(requestClass);
        } else if (request instanceof Query) {
            handler = (RequestHandler<TRequest, TResponse>)this.queryHandlers.get(requestClass);
        } else {
            throw new RuntimeException("Unknown request type");
        }

        if (handler == null) {
            throw new IllegalArgumentException("No handler registered");
        }

        return buildRequestPipelineChain(request, handler).handle(request);
    }

    @Override
    public <TRequest extends Request<TResponse>, TResponse> CompletableFuture<TResponse> sendAsync(TRequest request) {
        return CompletableFuture.supplyAsync(() -> send(request), this.executor);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <TEvent extends Event> void publish(TEvent event) {
        Class<?> eventClass = event.getClass();
        List<EventHandler<?>> handlers = this.eventHandlers.get(eventClass);

        if (handlers == null) {
            return;
        }

        handlers.forEach(handler
                -> buildEventPipelineChain(event, (EventHandler<TEvent>)handler).handle(event));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <TEvent extends Event> CompletableFuture<Void> publishAsync(TEvent event) {
        List<EventHandler<?>> handlers = eventHandlers.get(event.getClass());

        if (handlers == null) {
            return CompletableFuture.completedFuture(null);
        }

        var futures = new ArrayList<CompletableFuture<Void>>();

        for (var handler : handlers) {
            EventHandler<TEvent> finalCurrent = buildEventPipelineChain(event, (EventHandler<TEvent>) handler);
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
                    EventPipelineBehavior.class.isAssignableFrom(raw)) {
                Type eventType = pt.getActualTypeArguments()[0];

                if (eventType instanceof Class eventClass &&
                        !eventClass.equals(Event.class)) {
                    this.eventBehaviors.add(new RegisteredEventBehavior(eventClass, behavior));
                    return;
                }
            }
        }

        this.eventBehaviors.add(new RegisteredEventBehavior(null, behavior));
    }

    @SuppressWarnings("unchecked")
    private <TRequest extends Request<TResponse>, TResponse> RequestHandler<TRequest, TResponse> wrapRequestHandler(
            RequestPipelineBehavior<?, ?> raw,
            RequestHandler<TRequest, TResponse> next) {
        return request -> {
            var typed = (RequestPipelineBehavior<TRequest, TResponse>)raw;
            return typed.handle(request, next);
        };
    }

    private <TRequest extends Request<TResponse>, TResponse> RequestHandler<TRequest, TResponse>
        buildRequestPipelineChain(TRequest request, RequestHandler<TRequest, TResponse> handler) {
        RequestHandler<TRequest, TResponse> current = handler;
        ListIterator<RegisteredRequestBehavior> it = this.requestBehaviors.listIterator(this.requestBehaviors.size());

        while (it.hasPrevious()) {
            RegisteredRequestBehavior rb = it.previous();

            if (rb.requestClass == null || rb.requestClass.isAssignableFrom(request.getClass())) {
                current = wrapRequestHandler(rb.behavior, current);
            }
        }

        return current;
    }

    @SuppressWarnings("unchecked")
    private <TEvent extends Event> EventHandler<TEvent> wrapEventHandler(
            EventPipelineBehavior<?> raw,
            EventHandler<TEvent> next) {
        return event -> {
            var typed = (EventPipelineBehavior<TEvent>)raw;
            typed.handle(event, next);
        };
    }

    private <TEvent extends Event> EventHandler<TEvent> buildEventPipelineChain(TEvent event, EventHandler<TEvent> handler) {
        var current = handler;
        ListIterator<RegisteredEventBehavior> it = this.eventBehaviors.listIterator(this.eventBehaviors.size());

        while (it.hasPrevious()) {
            RegisteredEventBehavior eb = it.previous();

            if (eb.eventClass == null || eb.eventClass.isAssignableFrom(event.getClass())) {
                current = wrapEventHandler(eb.behavior, current);
            }
        }

        return current;
    }

    private record RegisteredRequestBehavior(Class<?> requestClass, RequestPipelineBehavior<?, ?> behavior) {}

    private record RegisteredEventBehavior(Class<?> eventClass, EventPipelineBehavior<?> behavior) {}
}