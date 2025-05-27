package com.esotericsoftware.kryonet.rmi;

@FunctionalInterface
public interface TriCallback<T, U, V> {
    void call(T t, U u, V v);
}
