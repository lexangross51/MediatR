package Abstractions.RequestHandlers;


import Abstractions.Requests.ResultCommand;

public interface ResultCommandHandler<TCommand extends ResultCommand<TResponse>, TResponse>
        extends RequestHandler<TCommand, TResponse> {}