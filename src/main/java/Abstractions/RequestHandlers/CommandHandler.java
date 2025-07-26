package Abstractions.RequestHandlers;

import Abstractions.Requests.Command;

public interface CommandHandler<TCommand extends Command> extends RequestHandler<TCommand, Void> {}