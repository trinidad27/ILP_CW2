package uk.ac.ed.acp.cw2.service;

import org.springframework.stereotype.Service;

import java.util.function.Supplier;
@Service
public class IlpCallerService {
    private final Supplier<String> ilpEndpointSupplier;

    public IlpCallerService(Supplier<String> ilpEndpointSupplier) {
        this.ilpEndpointSupplier = ilpEndpointSupplier;
    }

    public void doWork() {
        String endpoint = ilpEndpointSupplier.get();
    }
}
