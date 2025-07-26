package Abstractions.RequestHandlers;

import Abstractions.Requests.Event;

public interface EventHandler<TEvent extends Event> extends RequestHandler<TEvent, Void> {}