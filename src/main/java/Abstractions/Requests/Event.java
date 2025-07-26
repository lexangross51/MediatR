package Abstractions.Requests;

import java.time.LocalDate;

public interface Event extends Request<Void> {
    default LocalDate getOccurredAt() {
        return LocalDate.now();
    }
}