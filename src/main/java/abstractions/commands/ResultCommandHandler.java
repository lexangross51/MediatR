package abstractions.commands;


import abstractions.requests.RequestHandler;

public interface ResultCommandHandler<TCommand extends ResultCommand<TResponse>, TResponse>
        extends RequestHandler<TCommand, TResponse> {}