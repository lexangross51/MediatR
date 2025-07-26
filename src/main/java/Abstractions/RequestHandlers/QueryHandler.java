package Abstractions.RequestHandlers;

import Abstractions.Requests.Query;

public interface QueryHandler<TQuery extends Query<TResponse>, TResponse>
        extends RequestHandler<TQuery, TResponse> {}