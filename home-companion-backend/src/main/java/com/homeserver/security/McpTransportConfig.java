package com.homeserver.security;

import com.homeserver.core.accounts.api.*;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.util.*;
import java.util.function.Predicate;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.context.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class McpTransportConfig {
  @Bean
  WebMvcStatelessServerTransport mcpTransport(JsonMapper mapper, AccessFacade access) {
    return WebMvcStatelessServerTransport.builder()
        .jsonMapper(new JacksonMcpJsonMapper(mapper))
        .messageEndpoint("/mcp")
        .contextExtractor(
            request -> {
              var auth = SecurityContextHolder.getContext().getAuthentication();
              if (auth == null
                  || !(auth.getPrincipal() instanceof Identity i)
                  || !i.kind().equals("KEY")) return McpTransportContext.EMPTY;
              // Capture the verified identity explicitly; SDK worker threads need no servlet
              // ThreadLocal.
              Predicate<Boolean> permission = write -> access.allowed(i, write);
              return McpTransportContext.create(
                  Map.of("permission", permission, "credentialScope", i.credentialId().toString()));
            })
        .build();
  }
}
