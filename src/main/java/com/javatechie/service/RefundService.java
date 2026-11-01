package com.javatechie.service;

import com.javatechie.exception.RefundPolicyNotFoundException;
import com.javatechie.model.RefundPolicy;
import com.javatechie.repository.RefundPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundPolicyRepository repository;

    public RefundService(RefundPolicyRepository repository) {
        this.repository = repository;
    }

    public RefundPolicy checkEligibility(String orderStatus) {
        log.info("REFUND SERVICE -> Checking status: {}", orderStatus);
        return repository.findByOrderStatus(orderStatus)
                .orElseThrow(() -> new RefundPolicyNotFoundException(orderStatus));
    }
}
