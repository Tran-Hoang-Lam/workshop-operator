package com.mgmworkshop;

import com.mgmworkshop.cr.OperatorResource;
import com.mgmworkshop.cr.OperatorResourceDoneable;
import com.mgmworkshop.cr.OperatorResourceList;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.quarkus.runtime.StartupEvent;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class NamespaceWatcher {
    static List<String> systemNamespace = Arrays.asList("kube-system", "demo", "");

    @Inject
    MixedOperation<OperatorResource, OperatorResourceList, OperatorResourceDoneable, Resource<OperatorResource, OperatorResourceDoneable>> defaultClient;

    void onStartup(@Observes StartupEvent startupEvent) {
        defaultClient.watch(new Watcher<OperatorResource>() {
            @Override
            public void eventReceived(Action action, OperatorResource resource) {
                resource.getOperatorResourceSpec().getNamespaces().forEach(ns -> {
                    Pod errorPod = new DefaultKubernetesClient().inNamespace(ns)
                            .pods()
                            .list()
                            .getItems()
                            .stream()
                            .filter(isRabbitCorrect)
                            .findFirst()
                            .orElse(new Pod());
                });
            }

            @Override
            public void onClose(KubernetesClientException cause) {
                if (cause != null) {
                    cause.printStackTrace();
                    System.exit(-1);
                }
            }
        });
    }

    private final Predicate<Pod> isRabbitPod = pod -> pod.getMetadata().getName().startsWith("rabbit");
    private final Predicate<Pod> isRabbitCorrect = pod -> {
        pod.getSpec().getContainers()
                .stream()
                .findFirst()
                .map(container -> {
                    container.getEnv()
                            .stream()
                    return false;
                });
        return true;
    };
}
