package com.samtheoracle.proxy.search;

import java.util.Optional;

public enum ServiceSearchParameter {
    all, name, root;

    public static Optional<ServiceSearchParameter> from(String search) {
        try {
            return Optional.of(ServiceSearchParameter.valueOf(search));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
