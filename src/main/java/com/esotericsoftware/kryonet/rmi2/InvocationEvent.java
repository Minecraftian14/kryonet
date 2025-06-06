package com.esotericsoftware.kryonet.rmi2;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;

import java.util.Arrays;

class InvocationEvent implements FrameworkMessage, AutoCloseable {

    int transactionId;
    int objectId;
    CachedMethod method;
    Object[] params;

    private static final Pool<InvocationEvent> POOL = new Pool<InvocationEvent>(false, false) {
        @Override
        protected InvocationEvent create() {
            return new InvocationEvent();
        }
    };

    static InvocationEvent obtainIE(int transactionId, int objectId, CachedMethod method, Object[] args) {
        InvocationEvent ie = POOL.obtain();
        ie.transactionId = transactionId;
        ie.objectId = objectId;
        ie.method = method;
        ie.params = args != null ? args : new Object[0];
        return ie;
    }

    @Override
    public String toString() {
        return "InvocationEvent{" +
               "transactionId=" + transactionId +
               ", objectId=" + objectId +
               ", method=" + method +
               ", params=" + Arrays.toString(params) +
               '}';
    }

    @Override
    public void close() {
        POOL.free(this);
    }

    static class Handler extends Serializer<InvocationEvent> implements Listener {

        final ObjectSpaceV2 registry;

        Handler(ObjectSpaceV2 registry) {
            this.registry = registry;
        }

        @Override
        public void write(Kryo kryo, Output output, InvocationEvent ie) {
            output.writeVarInt(ie.transactionId, true);
            output.writeVarInt(ie.objectId, true);
            output.writeVarInt(ie.method.id, true);
            output.writeVarInt(ie.params.length, true);
            for (int i = 0; i < ie.params.length; i++)
                kryo.writeObjectOrNull(output, ie.params[i], ie.method.serClasses[i]);
            ie.close();
        }

        @Override
        public InvocationEvent read(Kryo kryo, Input input, Class<? extends InvocationEvent> aClass) {
            InvocationEvent ie = POOL.obtain();
            ie.transactionId = input.readVarInt(true);
            ie.objectId = input.readVarInt(true);
            ie.method = registry.midToCMet.get(input.readVarInt(true));
            ie.params = new Object[input.readVarInt(true)];
            for (int i = 0; i < ie.params.length; i++)
                ie.params[i] = kryo.readObjectOrNull(input, ie.method.serClasses[i]);
            return ie;
        }

        @Override
        public void received(Connection connection, Object event) {
            if (!(event instanceof InvocationEvent)) return;
            System.out.println("InvocationEvent RECEIVED ON THREAD " + Thread.currentThread().getName());
            InvocationEvent ie = (InvocationEvent) event;
            registry.invokeMethod(connection, ie);
        }
    }
}
