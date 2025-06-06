package com.esotericsoftware.kryonet.rmi2;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;

class ExecutionEvent implements FrameworkMessage, AutoCloseable {

    int transactionId;
    int objectId;
    CachedMethod method;
    Object result;

    private static final Pool<ExecutionEvent> POOL = new Pool<ExecutionEvent>(false, false) {
        @Override
        protected ExecutionEvent create() {
            return new ExecutionEvent();
        }
    };

    public static ExecutionEvent obtainEE(int transactionId, int objectId, CachedMethod method, Object result) {
        ExecutionEvent ee = POOL.obtain();
        ee.transactionId = transactionId;
        ee.objectId = objectId;
        ee.method = method;
        ee.result = RemoteSpace.primitize(result, method.resClass);
        return ee;
    }

    public Object use() {
        Object object = result;
        close();
        return object;
    }

    @Override
    public String toString() {
        return "ExecutionEvent{" +
               "transactionId=" + transactionId +
               ", objectId=" + objectId +
               ", method=" + method +
               ", result=" + result +
               '}';
    }

    @Override
    public void close() {
        POOL.free(this);
    }

    static final class Handler extends Serializer<ExecutionEvent> implements Listener {

        final RemoteSpace registry;

        Handler(RemoteSpace registry) {
            this.registry = registry;
        }

        @Override
        public void write(Kryo kryo, Output output, ExecutionEvent ee) {
            output.writeVarInt(ee.transactionId, true);
            output.writeVarInt(ee.objectId, true);
            output.writeVarInt(ee.method.id, true);
            if (ee.method.isResLocal) output.writeVarInt((Integer) ee.result, true);
            else kryo.writeObjectOrNull(output, ee.result, ee.method.resClass);
            ee.close();
        }

        @Override
        public ExecutionEvent read(Kryo kryo, Input input, Class<? extends ExecutionEvent> aClass) {
            ExecutionEvent ee = POOL.obtain();
            ee.transactionId = input.readVarInt(true);
            ee.objectId = input.readVarInt(true);
            ee.method = registry.midToCMet.get(input.readVarInt(true));
            if (ee.method.isResLocal) input.readVarInt(true);
            else ee.result = RemoteSpace.primitize(kryo.readObjectOrNull(input, ee.method.resClass), ee.method.resClass);
            return ee;
        }

        @Override
        public void received(Connection connection, Object event) {
            if (!(event instanceof ExecutionEvent)) return;
            System.out.println("ExecutionEvent RECEIVED ON THREAD " + Thread.currentThread().getName());
            ExecutionEvent ee = (ExecutionEvent) event;
            registry.executionLock.write(ee.transactionId, ee);
        }
    }
}
