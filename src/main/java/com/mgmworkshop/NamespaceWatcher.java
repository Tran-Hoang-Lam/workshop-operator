package com.mgmworkshop;

import com.mgmworkshop.cr.OperatorResource;
import com.mgmworkshop.cr.OperatorResourceDoneable;
import com.mgmworkshop.cr.OperatorResourceList;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.quarkus.runtime.StartupEvent;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NamespaceWatcher {
    static List<String> RABBIT_ENV_LIST = Arrays.asList("RABBITMQ_DEFAULT_USER", "RABBITMQ_DEFAULT_PASS");
    static List<String> ORDER_ENV_LIST = Arrays.asList("RABBIT_HOST", "RABBIT_PORT", "RABBIT_USERNAME", "RABBIT_PASSWORD");
    static List<String> WEB_ENV_LIST = Arrays.asList("ORDER_SERVICE_HOST", "RABBIT_HOST", "RABBIT_PORT", "RABBIT_USERNAME", "RABBIT_PASSWORD");
    static String IP_REGEX = "[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}";

    @Inject
    MixedOperation<OperatorResource, OperatorResourceList, OperatorResourceDoneable, Resource<OperatorResource, OperatorResourceDoneable>> defaultClient;

    void onStartup(@Observes StartupEvent startupEvent) {
        defaultClient.watch(new Watcher<OperatorResource>() {
            @Override
            public void eventReceived(Action action, OperatorResource resource) {
                resource.getOperatorResourceSpec().getNamespaces().forEach(ns -> {
                    NamespacedKubernetesClient client = new DefaultKubernetesClient().inNamespace(ns);
                    List<Pod> pods = client
                            .pods()
                            .list()
                            .getItems();

                    pods.forEach(pod -> {
                        checkRabbitPod(pod);
                        List<EnvVar> envList = pod.getSpec().getContainers().stream().findFirst().get().getEnv();

                    });
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

    private void checkRabbitPod(Pod pod) {
        if (isPod(pod, "rabbit")) {
            checkEnvName(pod, RABBIT_ENV_LIST);
        }
    }

    private void checkOrderPod (Pod pod, DefaultKubernetesClient client) {
        if (isPod(pod, "order")) {
            checkEnvName(pod, ORDER_ENV_LIST);
            pod.getSpec().getContainers().stream().findFirst()
                    .get().getEnv()
                    .forEach(envVar -> {
                        if (envVar.getName().equals("RABBIT_HOST") && isRabbitHostEnvValueCorrect(envVar, client)) {
                            System.out.println("Pod " + pod.getMetadata().getName() + " has incorrect env " + envVar.getName());
                        }
                    });
        }
    }

    private void checkEnvName(Pod pod, List<String> podEnv) {
        List<EnvVar> envList = pod.getSpec().getContainers().stream().findFirst().get().getEnv();
        List<EnvVar> inCorrectEnvVar = envList.stream().filter(envVar -> !podEnv.contains(envVar.getValue()))
                .collect(Collectors.toList());
        inCorrectEnvVar.forEach(envVar -> {
            System.out.println("Pod " + pod.getMetadata().getName() + " has incorrect env " + envVar.getName());
        });
    }

    private boolean isPod(Pod pod, String name) {
        return pod.getMetadata().getName().startsWith(name);
    }

    private boolean isRabbitHostEnvValueCorrect(EnvVar env, DefaultKubernetesClient client) {
        String envValue = env.getValue();
        if (envValue.matches(IP_REGEX)) {
            String rabbitIp = client.pods()
                    .list()
                    .getItems()
                    .stream()
                    .filter(pod -> isPod(pod, "rabbit"))
                    .findFirst()
                    .get()
                    .getStatus()
                    .getPodIP();
            return envValue.equals(rabbitIp);
        } else {
            return envValue.startsWith("rabbit");
        }
    }

    private boolean isRabbitPortEnvValueCorrect(EnvVar env, DefaultKubernetesClient client) {
        String envValue = env.getValue();
        if (envValue.matches("[0-9]{4}")) {
            String rabbitPort = client.pods()
                    .list()
                    .getItems()
                    .stream()
                    .filter(pod -> isPod(pod, "rabbit"))
                    .findFirst()
                    .get()
                    .getSpec()
                    .getContainers()
                    .stream().findFirst().get()
                    .getPorts()
                    .stream().filter(containerPort -> containerPort.getContainerPort() < 9999)
                    .findFirst()
                    .get()
                    .getContainerPort().toString();

            return rabbitPort.equals(envValue);
        }
        return false;
    }

    private boolean isRabbitUsernameOrPasswordCorrect(EnvVar env, Predicate<EnvVar> rabbitDefaultEnv, DefaultKubernetesClient client) {
        String envValue = env.getValue();
        String rabbitUsername = client.pods()
                .list()
                .getItems()
                .stream()
                .filter(pod -> isPod(pod, "rabbit"))
                .findFirst()
                .get()
                .getSpec()
                .getContainers()
                .stream().findFirst()
                .get()
                .getEnv().stream().filter(rabbitDefaultEnv)
                .findFirst()
                .get()
                .getValue();
        return envValue.equals(rabbitUsername);
    }

    private boolean isOrderServiceIpCorrect(EnvVar env, DefaultKubernetesClient client) {
        String envValue = env.getValue();
        if (envValue.matches(IP_REGEX)) {
            String rabbitIp = client.pods()
                    .list()
                    .getItems()
                    .stream()
                    .filter(pod -> isPod(pod, "order"))
                    .findFirst()
                    .get()
                    .getStatus()
                    .getPodIP();
            return envValue.equals(rabbitIp);
        } else {
            return envValue.startsWith("order");
        }
    }
}
