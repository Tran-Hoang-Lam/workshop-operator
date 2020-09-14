package com.mgmworkshop.cr;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkus.runtime.annotations.RegisterForReflection;

@JsonDeserialize
@RegisterForReflection
public class OperatorResource extends CustomResource {
    private OperatorResourceSpec spec;

    public OperatorResourceSpec getSpec() {
        return spec;
    }

    public void setSpec(OperatorResourceSpec spec) {
        this.spec = spec;
    }
}
