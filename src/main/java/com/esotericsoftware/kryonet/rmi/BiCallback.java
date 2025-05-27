package com.esotericsoftware.kryonet.rmi;

@FunctionalInterface
public interface BiCallback<T, U> {
    void call(T t, U u);
}
