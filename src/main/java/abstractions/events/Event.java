package abstractions.events;

import java.time.LocalDate;

public interface Event {
    default LocalDate getOccurredAt() {
        return LocalDate.now();
    }
}