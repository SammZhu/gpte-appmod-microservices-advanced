package com.redhat.coolstore.cart.tracing;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.spring.web.client.HttpHeadersCarrier;
import io.opentracing.propagation.Format;

public class TracingRestHandlerInterceptor implements ClientHttpRequestInterceptor {

    private Tracer tracer;

    public TracingRestHandlerInterceptor(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        Optional<Span> activeSpan = Optional.ofNullable(tracer.activeSpan());
        activeSpan.map(Span::context).ifPresent(s -> tracer.inject(s, Format.Builtin.HTTP_HEADERS, new HttpHeadersCarrier(request.getHeaders())));
        return execution.execute(request, body);
    }

}