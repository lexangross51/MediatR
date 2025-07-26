package abstractions;

import abstractions.requestHandlers.RequestHandler;
import abstractions.requests.Request;

public interface Mediator {
    <TRequest extends Request<TResponse>, TResponse> void registerHandler(
            Class<TRequest> requestType, RequestHandler<TRequest, TResponse> requestHandler);

    <TRequest extends Request<TResponse>, TResponse> TResponse send(TRequest request);
}