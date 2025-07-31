package abstractions.behaviors;

import abstractions.requests.Request;
import abstractions.requests.RequestHandler;

public interface RequestPipelineBehavior<TRequest extends Request<TResponse>, TResponse> {
    TResponse handle(TRequest request, RequestHandler<TRequest, TResponse> next);
}