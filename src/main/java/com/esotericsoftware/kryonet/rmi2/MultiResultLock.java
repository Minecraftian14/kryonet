package com.esotericsoftware.kryonet.rmi2;

import com.esotericsoftware.kryo.util.ObjectMap;

import java.util.concurrent.*;

public class MultiResultLock<Type> {

    final ObjectMap<Integer, CompletableFuture<Type>> transactions = new ObjectMap<>();

    public void write(int transactionId, Type object) {
        synchronized (transactions) {
            if (transactions.containsKey(transactionId))
                transactions.get(transactionId).complete(object);
            else transactions.put(transactionId, CompletableFuture.completedFuture(object));
        }
    }

    public Type read(int transactionId) {
        CompletableFuture<Type> future;
        synchronized (transactions) {
            if (transactions.containsKey(transactionId))
                future = transactions.get(transactionId);
            else transactions.put(transactionId, future = new CompletableFuture<>());
        }
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            synchronized (transactions) {
                transactions.remove(transactionId);
            }
            throw new RuntimeException("Transaction ID " + transactionId, e);
        }
    }

}
