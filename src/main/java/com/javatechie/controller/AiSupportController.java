package com.javatechie.controller;

import com.javatechie.agents.WorkflowSupportAgent;
import com.javatechie.service.JavaTechieAIAgent;
import com.javatechie.service.OrderSupportAIAgent;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-support")
public class AiSupportController {

    private final JavaTechieAIAgent agent;

    private final OrderSupportAIAgent orderSupportAIAgent;

    private final WorkflowSupportAgent workflowSupportAgent;

    private final SupervisorAgent supervisorAgent;


    public AiSupportController(
            JavaTechieAIAgent agent,
            OrderSupportAIAgent orderSupportAIAgent,
            WorkflowSupportAgent workflowSupportAgent,
            SupervisorAgent supervisorAgent) {
        this.agent = agent;
        this.orderSupportAIAgent = orderSupportAIAgent;
        this.workflowSupportAgent = workflowSupportAgent;
        this.supervisorAgent = supervisorAgent;
    }

    @GetMapping
    public String chat(
            @RequestParam String message) {
        return agent.chat(message);
    }

    @GetMapping("/order")
    public String orderSupport(
            @RequestParam String message) {
        return orderSupportAIAgent.chat(message);
    }

    @GetMapping("/workflow")
    public String workflowSupport(
            @RequestParam String request) {
        return workflowSupportAgent.handle(request);
    }

    @GetMapping("/supervisor")
    public String supervisorSupport(
            @RequestParam String request) {
        return supervisorAgent.invoke(request);
    }
}
