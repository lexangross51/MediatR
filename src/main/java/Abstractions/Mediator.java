package Abstractions;

import Abstractions.RequestHandlers.RequestHandler;
import Abstractions.Requests.Request;

public interface Mediator {
    <TRequest extends Request<TResponse>, TResponse> void registerHandler(
            Class<TRequest> requestType, RequestHandler<TRequest, TResponse> requestHandler);

    <TRequest extends Request<TResponse>, TResponse> TResponse send(TRequest request);
}