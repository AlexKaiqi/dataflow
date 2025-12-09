package com.tencent.dataflow.adapter.web;

import com.tencent.dataflow.client.dto.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health Check Controller
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Response health() {
        return Response.buildSuccess();
    }
}
