package com.esotericsoftware.kryonet.rmi;

@FunctionalInterface
public interface MonoCallback<T> {
    void call(T t);
}
