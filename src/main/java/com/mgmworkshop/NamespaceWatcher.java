package com.mgmworkshop;

import com.mgmworkshop.cr.OperatorResource;
import com.mgmworkshop.cr.OperatorResourceDoneable;
import com.mgmworkshop.cr.OperatorResourceList;
import com.mgmworkshop.model.CheckResult;
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
        defaultClient.watch(new Watcher<>() {
            @Override
            public void eventReceived(Action action, OperatorResource resource) {
                resource.getSpec().getNamespaces().forEach(ns -> {
                    NamespacedKubernetesClient client = new DefaultKubernetesClient().inNamespace(ns);
                    client
                            .pods()
                            .watch(new Watcher<>() {
                                @Override
                                public void eventReceived(Action action, Pod pod) {
                                    if (action.equals(Action.ADDED) || action.equals(Action.MODIFIED)) {
                                        checkRabbitPod(pod);
                                        checkOrderPod(pod, client);
                                        checkWebPod(pod, client);
                                    }
                                }

                                @Override
                                public void onClose(KubernetesClientException cause) {
                                    closeConnection(cause);
                                }
                            });
                });
            }

            @Override
            public void onClose(KubernetesClientException cause) {
                closeConnection(cause);
            }
        });
    }

    private void closeConnection(KubernetesClientException cause) {
        if (cause != null) {
            cause.printStackTrace();
            System.exit(-1);
        }
    }

    private void checkRabbitPod(Pod pod) {
        if (isPod(pod, "rabbit")) {
            checkEnvName(pod, RABBIT_ENV_LIST);
        }
    }

    private void checkOrderPod(Pod pod, NamespacedKubernetesClient client) {
        if (isPod(pod, "order")) {
            checkEnvName(pod, ORDER_ENV_LIST);
            pod.getSpec().getContainers().stream().findFirst()
                    .get().getEnv()
                    .forEach(envVar -> checkRabbitEnvVariable(pod, client, envVar));
        }
    }

    private void checkWebPod(Pod pod, NamespacedKubernetesClient client) {
        if (isPod(pod, "web")) {
            checkEnvName(pod, WEB_ENV_LIST);
            checkOrderPod(pod, client);
            pod.getSpec().getContainers().stream().findFirst()
                    .get().getEnv()
                    .forEach(envVar -> {
                        checkRabbitEnvVariable(pod, client, envVar);
                        checkOrderEnvVariable(pod, client, envVar);
                    });
        }
    }

    private void checkOrderEnvVariable(Pod pod, NamespacedKubernetesClient client, EnvVar envVar) {
        if (envVar.getName().equals("ORDER_SERVICE_HOST")) {
            CheckResult checkResult = checkOrderServiceIp(envVar, client);
            if (checkResult.hasError()) {
                System.out.println("Pod " + pod.getMetadata().getName() + " in namespace " + pod.getMetadata().getNamespace() + " has incorrect env for order service");
                checkResult.printMessage();
            }
        }
    }

    private void checkRabbitEnvVariable(Pod pod, NamespacedKubernetesClient client, EnvVar envVar) {
        if (envVar.getName().equals("RABBIT_HOST")) {
            CheckResult checkResult = checkRabbitHostEnvValue(envVar, client);
            if (checkResult.hasError()) {
                System.out.println("Pod " + pod.getMetadata().getName() + " in namespace " + pod.getMetadata().getNamespace() + " has incorrect env " + envVar.getName());
                checkResult.printMessage();
            }
        }

        if (envVar.getName().equals("RABBIT_PORT")) {
            CheckResult checkResult = checkRabbitPortEnvValue(envVar, client);
            if (checkResult.hasError()) {
                System.out.println("Pod " + pod.getMetadata().getName() + " in namespace " + pod.getMetadata().getNamespace() + " has incorrect port: " + envVar.getValue());
                checkResult.printMessage();
            }
        }

        if (envVar.getName().equals("RABBIT_USERNAME")) {
            CheckResult checkResult = checkRabbitUsernameOrPassword(envVar, value -> value.getName().equals("RABBITMQ_DEFAULT_USER"), client);
            if (checkResult.hasError()) {
                System.out.println("Pod " + pod.getMetadata().getName() + " in namespace " + pod.getMetadata().getNamespace() + " has incorrect rabbit user: " + envVar.getValue());
                checkResult.printMessage();
            }
        }

        if (envVar.getName().equals("RABBIT_PASSWORD")) {
            CheckResult checkResult = checkRabbitUsernameOrPassword(envVar, value -> value.getName().equals("RABBITMQ_DEFAULT_PASS"), client);
            if (checkResult.hasError()) {
                System.out.println("Pod " + pod.getMetadata().getName() + " in namespace " + pod.getMetadata().getNamespace() + " has incorrect rabbit password: " + envVar.getValue());
                checkResult.printMessage();
            }
        }
    }

    private void checkEnvName(Pod pod, List<String> podEnv) {
        List<EnvVar> envList = pod.getSpec().getContainers().stream().findFirst().get().getEnv();
        List<EnvVar> inCorrectEnvVar = envList.stream().filter(envVar -> !podEnv.contains(envVar.getName()))
                .collect(Collectors.toList());
        inCorrectEnvVar.forEach(envVar -> {
            System.out.println("Pod " + pod.getMetadata().getName() + " in namespace " + pod.getMetadata().getNamespace() + " has incorrect env " + envVar.getName());
        });
    }

    private boolean isPod(Pod pod, String name) {
        return pod.getMetadata().getName().startsWith(name);
    }

    private CheckResult checkRabbitHostEnvValue(EnvVar env, NamespacedKubernetesClient client) {
        String envValue = env.getValue();
        CheckResult checkResult = new CheckResult();
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
            if (!envValue.equals(rabbitIp)) {
                checkResult.addMessage("Env " + env.getName() + " should be " + rabbitIp);
            }
        } else {
            if (!envValue.startsWith("rabbit")) {
                checkResult.addMessage("Env " + env.getName() + " should start with rabbit");
            }
        }

        return checkResult;
    }

    private CheckResult checkRabbitPortEnvValue(EnvVar env, NamespacedKubernetesClient client) {
        String envValue = env.getValue();
        CheckResult checkResult = new CheckResult();
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

            if (!rabbitPort.equals(envValue)) {
                checkResult.addMessage("Env " + env.getName() + " should be " + rabbitPort);
            }
        }
        return checkResult;
    }

    private CheckResult checkRabbitUsernameOrPassword(EnvVar env, Predicate<EnvVar> rabbitDefaultEnv, NamespacedKubernetesClient client) {
        String envValue = env.getValue();
        CheckResult checkResult = new CheckResult();
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
        if (!envValue.equals(rabbitUsername)) {
            checkResult.addMessage("Env " + env.getName() + " should have value equal " + rabbitUsername);
        }
        return checkResult;
    }

    private CheckResult checkOrderServiceIp(EnvVar env, NamespacedKubernetesClient client) {
        String envValue = env.getValue();
        CheckResult checkResult = new CheckResult();
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
            if (!envValue.equals(rabbitIp)) {
                checkResult.addMessage("Env " + env.getName() + " should be " + rabbitIp);
            }
        } else {
            if (!envValue.startsWith("order")) {
                checkResult.addMessage("Env " + env.getName() + " should have value start with order");
            }
        }

        return checkResult;
    }
}
