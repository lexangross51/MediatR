import abstractions.commands.ResultCommandHandler;

public class MultiplyCommandHandler implements ResultCommandHandler<MultiplyCommand, Integer> {
    @Override
    public Integer handle(MultiplyCommand request) {
        return request.x * request.y;
    }
}