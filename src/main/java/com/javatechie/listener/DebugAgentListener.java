package com.javatechie.listener;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugAgentListener implements AgentListener {

    private static final Logger log = LoggerFactory.getLogger(DebugAgentListener.class);

    @Override
    public void afterAgenticScopeCreated(AgenticScope scope) {
        log.info("=== SCOPE CREATED ===");
        log.info("{}", scope);
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        log.info("=== AGENT START ===");
        log.info("{}", request);
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        log.info("=== AGENT END ===");
        log.info("{}", response);
        log.info("Scope: {}", response.agenticScope());
    }

    @Override
    public void beforeAgenticScopeDestroyed(AgenticScope scope) {
        log.info("=== FINAL AGENTIC SCOPE ===");
        log.info("{}", scope);
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }
}
