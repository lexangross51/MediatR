package abstractions.requestHandlers;

import abstractions.requests.Query;

public interface QueryHandler<TQuery extends Query<TResponse>, TResponse>
        extends RequestHandler<TQuery, TResponse> {}