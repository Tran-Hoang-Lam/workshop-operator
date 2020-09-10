package com.mgmworkshop.cr;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkus.runtime.annotations.RegisterForReflection;

@JsonDeserialize
@RegisterForReflection
public class OperatorResource extends CustomResource {
    private OperatorResourceSpec operatorResourceSpec;

    public OperatorResourceSpec getOperatorResourceSpec() {
        return operatorResourceSpec;
    }

    public void setOperatorResourceSpec(OperatorResourceSpec operatorResourceSpec) {
        this.operatorResourceSpec = operatorResourceSpec;
    }
}
