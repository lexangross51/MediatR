import abstractions.commands.ResultCommand;

public class MultiplyCommand implements ResultCommand<Integer> {
    public final int x;
    public final int y;

    public MultiplyCommand(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

