package abstractions.requestHandlers;


import abstractions.requests.ResultCommand;

public interface ResultCommandHandler<TCommand extends ResultCommand<TResponse>, TResponse>
        extends RequestHandler<TCommand, TResponse> {}