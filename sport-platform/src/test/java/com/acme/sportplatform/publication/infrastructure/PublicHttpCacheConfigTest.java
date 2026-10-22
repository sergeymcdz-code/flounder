package com.acme.sportplatform.publication.infrastructure;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertSame;

class PublicHttpCacheConfigTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/public/events/cup/start-list.pdf",
            "/api/v1/public/events/cup/results.pdf",
            "/api/v1/public/documents/document-id/content"
    })
    void downloadsReachTheChainWithoutAResponseCachingWrapper(String path) throws Exception {
        var request = new MockHttpServletRequest("GET", "/sportgate" + path);
        request.setContextPath("/sportgate");
        var response = new MockHttpServletResponse();
        var filter = new PublicHttpCacheConfig().publicEtagFilter().getFilter();

        filter.doFilter(request, response, (downstreamRequest, downstreamResponse) ->
                assertSame(response, downstreamResponse));
    }
}
