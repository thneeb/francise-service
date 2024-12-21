package de.neebs.franchise.integration;

import de.neebs.franchise.client.boundary.DefaultApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "frontendBackendBridge", url = "http://127.0.0.1:8080")
public interface FrontendBackendBridge extends DefaultApi {
}
