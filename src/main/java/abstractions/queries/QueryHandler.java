package abstractions.queries;

import abstractions.requests.RequestHandler;

public interface QueryHandler<TQuery extends Query<TResponse>, TResponse>
        extends RequestHandler<TQuery, TResponse> {}