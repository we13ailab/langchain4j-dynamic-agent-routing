package com.javatechie.tool;

import com.javatechie.service.RefundService;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RefundTool {

    private static final Logger log = LoggerFactory.getLogger(RefundTool.class);

    private final RefundService refundService;

    public RefundTool(RefundService refundService) {
        this.refundService = refundService;
    }

    @Tool("Check whether an order is eligible for a refund based on order status")
    public String checkRefundEligibility(String orderStatus) {
        log.info("TOOL CALLED -> checkRefundEligibility({})", orderStatus);
        return refundService.checkEligibility(orderStatus).toString();
    }
}
