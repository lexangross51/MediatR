package abstractions.requestHandlers;

import abstractions.requests.Command;

public interface CommandHandler<TCommand extends Command> extends RequestHandler<TCommand, Void> {}