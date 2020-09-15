package com.mgmworkshop;

import com.mgmworkshop.cr.OperatorResource;
import com.mgmworkshop.cr.OperatorResourceDoneable;
import com.mgmworkshop.cr.OperatorResourceList;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

public class OperatorConfiguration {

    @Produces
    @Singleton
    public KubernetesClient kubernetesClient() {
        return new DefaultKubernetesClient().inNamespace("operator");
    }


    @Produces
    @Singleton
    MixedOperation<OperatorResource, OperatorResourceList, OperatorResourceDoneable, Resource<OperatorResource, OperatorResourceDoneable>> defaultClient(KubernetesClient kubernetesClient) {
        KubernetesDeserializer.registerCustomKind("mgmworkshop.com/v1", "WorkshopOperator", OperatorResource.class);
        CustomResourceDefinition crd = kubernetesClient.customResourceDefinitions().list().getItems().stream().filter(customResourceDefinition -> "workshopoperators.mgmworkshop.com".equals(customResourceDefinition.getMetadata().getName())).findFirst().orElseThrow();
        return kubernetesClient.customResources(CustomResourceDefinitionContext.fromCrd(crd), OperatorResource.class, OperatorResourceList.class, OperatorResourceDoneable.class);
    }
}
