package abstractions.commands;

public interface CommandHandler<TCommand extends Command> extends ResultCommandHandler<TCommand, Void> {}