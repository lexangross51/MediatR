package abstractions.commands;

import abstractions.requests.Request;

public interface ResultCommand<TResponse> extends Request<TResponse> {}