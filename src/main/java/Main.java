import abstractions.behaviors.RequestPipelineBehavior;
import abstractions.commands.ResultCommand;
import abstractions.commands.ResultCommandHandler;
import abstractions.requests.Request;
import abstractions.requests.RequestHandler;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    public static void main(String... args) {
        var mediator = new MediatorImpl();
        mediator.registerHandler(CreateUserCommand.class, new CreateUserCommandHandler());
        mediator.registerHandler(CreateToDoCommand.class, new CreateToDoCommandHandler());
        mediator.registerPipelineBehavior(new LoggingPipelineBehavior<>());
        mediator.registerPipelineBehavior(new ValidatePipelineBehavior());

        mediator.send(new CreateUserCommand("Алексей"));
        System.out.println("\n\n");
        System.out.println("-".repeat(30));
        mediator.send(new CreateToDoCommand("first"));
    }
}

record CreateUserCommand(String name) implements ResultCommand<Integer> {}

record CreateToDoCommand(String title) implements ResultCommand<Integer> {}

class CreateUserCommandHandler implements ResultCommandHandler<CreateUserCommand, Integer>{
    @Override
    public Integer handle(CreateUserCommand request) {
        System.out.println("Новый пользователь добавлен '" + request.name() + "'");
        return 12;
    }
}

class CreateToDoCommandHandler implements ResultCommandHandler<CreateToDoCommand, Integer> {
    @Override
    public Integer handle(CreateToDoCommand request) {
        System.out.println("Новая задача добавлена '" + request.title() + "'");
        return 12;
    }
}

class LoggingPipelineBehavior<TRequest extends Request<TResponse>, TResponse>
        implements RequestPipelineBehavior<TRequest, TResponse> {
    private final Logger logger = Logger.getLogger(LoggingPipelineBehavior.class.getName());

    @Override
    public TResponse handle(TRequest request, RequestHandler<TRequest, TResponse> next) {
        this.logger.info("Начали выполнять запрос '" + request.getClass().getName() + "'");

        try {
            TResponse result = next.handle(request);
            this.logger.info("Запрос '" + request.getClass().getSimpleName() + "' успешно выполнен");
            return result;
        } catch (Exception ex) {
            this.logger.log(Level.WARNING, "Запрос '" + request.getClass().getSimpleName() + "' завершился с ошибкой");
            throw ex;
        }
    }
}

class ValidatePipelineBehavior implements RequestPipelineBehavior<CreateToDoCommand, Integer> {
    private final Logger logger = Logger.getLogger(ValidatePipelineBehavior.class.getName());

    @Override
    public Integer handle(CreateToDoCommand request, RequestHandler<CreateToDoCommand, Integer> next) {
        this.logger.info("Начали выполнять валидацию запроса '" + request.getClass().getName() + "'");

        try {
            Integer result = next.handle(request);
            this.logger.info("Запрос '" + request.getClass().getSimpleName() + "' успешно провалидирован");
            return result;
        } catch (Exception ex) {
            this.logger.info("Запрос '" + request.getClass().getSimpleName() + "' не прошел валидацию");
            throw ex;
        }
    }
}