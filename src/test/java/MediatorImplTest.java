import abstractions.queries.Query;
import abstractions.requests.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.*;

public class MediatorImplTest {
    private MediatorImpl mediator;

    @BeforeEach
    void setUp() {
        mediator = new MediatorImpl();
    }

    @Test
    void shouldHandleMultiplyCommandCorrectly() {
        mediator.registerHandler(MultiplyCommand.class, new MultiplyCommandHandler());

        int result = mediator.send(new MultiplyCommand(6, 7));

        assertThat(result).isEqualTo(42);
    }

    @Test
    void shouldThrowIfUnknownQueryType() {
        class UnknownQuery implements Query<String> {}

        assertThatThrownBy(() -> mediator.send(new UnknownQuery()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No handler registered");
    }

    @Test
    void shouldThrowIfRegisterHandlerWithWrongBaseType() {
        class UnknownRequest implements Request<String> {}

        assertThatThrownBy(() ->
                mediator.registerHandler(UnknownRequest.class, _ -> "fail"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unknown request type");
    }

    @Test
    void shouldIgnorePublishIfNoHandlers() {
        mediator.publish(new UserCreatedEvent("Ghost"));
    }

    @Test
    void shouldHandleMultipleEventHandlersEvenIfOneFails() {
        mediator.registerEventHandler(UserCreatedEvent.class, new LoggingEventHandler());
        mediator.registerEventHandler(UserCreatedEvent.class, new FailingEventHandler());

        assertThatThrownBy(() -> mediator.publish(new UserCreatedEvent("FailTest")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated failure");

        var _ = assertThat(LoggingEventHandler.log)
                .actual()
                .contains("User created: FailTest");
    }

    @Test
    void shouldBeThreadSafeForCommandHandlers() throws InterruptedException, ExecutionException {
        mediator.registerHandler(MultiplyCommand.class, new MultiplyCommandHandler());

        var executor = Executors.newFixedThreadPool(10);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            final int x = i, y = 2;
            results.add(executor.submit(() -> mediator.send(new MultiplyCommand(x, y))));
        }

        for (int i = 0; i < 100; i++) {
            assertThat(results.get(i).get()).isEqualTo(i * 2);
        }

        executor.shutdown();
        var _ = executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    void shouldNotAllowNullHandlerRegistration() {
        assertThatThrownBy(() ->
                mediator.registerHandler(MultiplyCommand.class, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void shouldNotAllowNullRequestInSend() {
        assertThatThrownBy(() -> mediator.send(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void shouldNotAllowNullEventInPublish() {
        assertThatThrownBy(() -> mediator.publish(null))
                .isInstanceOf(NullPointerException.class);
    }
}