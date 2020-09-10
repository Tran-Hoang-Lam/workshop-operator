package com.mgmworkshop.cr;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Set;

@JsonDeserialize
@RegisterForReflection
public class OperatorResourceSpec {

    @JsonProperty("namespace")
    private Set<String> namespaces;

    public Set<String> getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(Set<String> namespaces) {
        this.namespaces = namespaces;
    }

    @Override
    public String toString() {
        return "[" + String.join(",", namespaces.toArray(new String[]{})) + "]";
    }
}
