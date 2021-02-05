package com.samtheoracle.proxy.search;

import java.util.Optional;

public enum ServiceSearchParameter {
    all, name, root, host, endpoint, port, ssl, creationDate, status;

    public static Optional<ServiceSearchParameter> from(String search) {
        try {
            return Optional.of(ServiceSearchParameter.valueOf(search));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
