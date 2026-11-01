package com.javatechie.service;

import com.javatechie.exception.ShipmentTrackingNotFoundException;
import com.javatechie.model.ShipmentTracking;
import com.javatechie.repository.ShipmentTrackingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ShippingService {

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);

    private final ShipmentTrackingRepository repository;

    public ShippingService(ShipmentTrackingRepository repository) {
        this.repository = repository;
    }

    public ShipmentTracking getTracking(String orderId) {
        log.info("SHIPPING SERVICE -> Looking up tracking for order: {}", orderId);
        return repository.findByOrderId(orderId)
                .orElseThrow(() -> new ShipmentTrackingNotFoundException(orderId));
    }
}
