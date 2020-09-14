package com.mgmworkshop.model;

import java.util.HashSet;
import java.util.Set;

public class CheckResult {
    private final Set<String> message;

    public CheckResult() {
        this.message = new HashSet<>();
    }

    public boolean hasError() {
        return !message.isEmpty();
    }

    public void addMessage(String message) {
        this.message.add(message);
    }

    public void printMessage() {
        this.message.forEach(System.out::println);
    }
}
