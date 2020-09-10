package com.mgmworkshop.cr;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class OperatorResourceDoneable extends CustomResourceDoneable<OperatorResource> {
    public OperatorResourceDoneable(OperatorResource resource, Function<OperatorResource, OperatorResource> function) {
        super(resource, function);
    }
}
