package com.esotericsoftware.kryonet.rmi2;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.IntMap;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.util.ObjectIntMap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ObjectSpaceV2 {

    static final ObjectIntMap<Method> objectMethods = new ObjectIntMap<>();

    static {
        for (Method method : Object.class.getDeclaredMethods()) objectMethods.put(method, 0);
    }

    protected int nextObjectId = 0;
    protected int nextMethodId = 0;

    final ObjectIntMap<Class<?>> clsReg = new ObjectIntMap<>();

    final IntMap<Object> oidToObj = new IntMap<>();
    final ObjectIntMap<Object> objToOid = new ObjectIntMap<>();
    final IntMap<Class<?>> oidToCls = new IntMap<>();

    final IntMap<CachedMethod> midToCMet = new IntMap<>();
    final ObjectIntMap<Method> metToMid = new ObjectIntMap<>();

    final ExecutorService executor = Executors.newSingleThreadExecutor();

    final InvocationEvent.Handler invocationHandler = new InvocationEvent.Handler(this);
    final ExecutionEvent.Handler executionHandler = new ExecutionEvent.Handler(this);

    final AtomicInteger transactionIdSupplier = new AtomicInteger();
    final MultiResultLock<ExecutionEvent> executionLock = new MultiResultLock<>();

    public ObjectSpaceV2 registerRemotable(Class<?> clazz) {
        if (clsReg.containsKey(clazz)) throw new IllegalArgumentException("Class " + clazz + " is already registered.");
        clsReg.put(clazz, 0);
        registerMethods(clazz);
        return this;
    }

    protected void registerMethods(Class<?> clazz) {
        /* if (clazz.isInterface()) */
        for (Method method : clazz.getMethods()) {
            int methodId = nextMethodId++;
            midToCMet.put(methodId, new CachedMethod(methodId, method));
            metToMid.put(method, methodId);
        }
        for (Class<?> iSuper : clazz.getInterfaces()) registerMethods(iSuper);
    }

    public ObjectSpaceV2 registerEvents(Kryo kryo) {
        kryo.register(InvocationEvent.class, invocationHandler);
        kryo.register(ExecutionEvent.class, executionHandler);
        return this;
    }

    protected void saveObject(int objectId, Class<?> clazz, Object object) {
        if (objectId < nextObjectId) throw new IllegalArgumentException("Object id " + objectId + " already configured for object " + oidToObj.get(objectId));
        nextObjectId = objectId + 1;
        objToOid.put(object, objectId);
        oidToObj.put(objectId, object);
        oidToCls.put(objectId, clazz);
    }

    public void hostObject(Connection connection, int objectId, Object object) {
        // Class explicitly not required, since we only invoke the methods
        saveObject(objectId, object.getClass(), object);
        connection.addListener(invocationHandler);
    }

    public void hostObject(Connection connection, Object object) {
        hostObject(connection, nextObjectId, object);
    }

    public void hostObject(Server server, int objectId, Object object) {
        saveObject(objectId, object.getClass(), object);
        server.addListener(Listener.connected((connection) -> connection.addListener(invocationHandler)));
    }

    public void hostObject(Server server, Object object) {
        hostObject(server, nextObjectId, object);
    }

    @SuppressWarnings("unchecked")
    public <T> T createRemote(Connection connection, int objectId, Class<T> clazz) {
        Object delegate = "Proxy for " + clazz.getName();
        Object remote = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz},
            (proxy, method, args) -> invokeMethod(connection, objectId, delegate, method, args));
        saveObject(objectId, clazz, remote);
        connection.addListener(executionHandler);
        return (T) remote;
    }

    public <T> T createRemote(Connection connection, Class<T> clazz) {
        return createRemote(connection, nextObjectId, clazz);
    }

    protected boolean isObjectMethod(Method method) {
        return ObjectSpaceV2.objectMethods.containsKey(method)
//               && !method.getName().equals("toString")
               && !method.getName().equals("equals");
    }

    protected Object forceLocalInvocation(Object delegate, Method method, Object[] params) {
        try {
            return method.invoke(delegate, params);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    static Object primitize(Object object, Class<?> resClass) {
        if (object != null) return object;
        if (!resClass.isPrimitive()) return null;
        if (resClass == int.class) return 0;
        if (resClass == boolean.class) return Boolean.FALSE;
        if (resClass == float.class) return 0f;
        if (resClass == char.class) return (char) 0;
        if (resClass == long.class) return 0l;
        if (resClass == short.class) return (short) 0;
        if (resClass == byte.class) return (byte) 0;
        if (resClass == double.class) return 0d;
        return null;
    }

    protected Object invokeMethod(Connection connection, int objectId, Object delegate, Method method, Object[] params) {
        if (isObjectMethod(method)) return forceLocalInvocation(delegate, method, params);
        int transactionId = transactionIdSupplier.getAndIncrement();
        int methodId = metToMid.get(method, -1);
        CachedMethod cMethod = midToCMet.get(methodId);

        System.out.println("RemoteObjectRegistry.invokeMethod " + transactionId + " :: " + method + " >>> " + cMethod);
        connection.sendTCP(hostParams(connection, InvocationEvent.obtain(transactionId, objectId, cMethod, params)));
        System.out.println("POSSIBLY WAITING FOR ExecutionEvent ON THREAD " + Thread.currentThread().getName());
        if (cMethod.returnsNotRequired()) return primitize(null, cMethod.resClass);
        // Wait for the ExecutionEvent. which contains the result.
        // If the return type is supposed to be a remote object as well, the result must be an object id.
        // Replace the id with the actual remote object.
        // Finally, retrieve the result and free the ExecutionEvent.
        return createRemoteResult(connection, executionLock.read(transactionId)).getResultAndFree();
    }

    protected InvocationEvent hostParams(Connection connection, InvocationEvent ie) {
        for (int index : ie.method.localParamIndices) {
            Object[] params = ie.params;
            Object param = params[index];
            params[index] = -1;
            if (param == null) continue;
            if (!objToOid.containsKey(param)) hostObject(connection, param);
            params[index] = objToOid.get(param, -1);
        }
        return ie;
    }

    protected ExecutionEvent createRemoteResult(Connection connection, ExecutionEvent ee) {
        if (!ee.method.isResLocal) return ee;
        int objectId = (int) ee.result;
        ee.result = null;
        if (objectId == -1) return ee;
        ee.result = oidToObj.containsKey(objectId) ? oidToObj.get(objectId) : createRemote(connection, ee.method.resClass);
        return ee;
    }

    protected void invokeMethod(Connection connection, InvocationEvent ie) {
        Object object = oidToObj.get(ie.objectId);

        executor.submit(() -> {
            Object result = ie.method.invokeMethod(object, createRemoteParams(connection, ie));
            if (ie.method.returnsNotRequired()) return;
            connection.sendTCP(hostResult(connection, ExecutionEvent.obtain(ie.transactionId, ie.objectId, ie.method, result)));
            InvocationEvent.POOL.free(ie);
        });
    }

    protected InvocationEvent createRemoteParams(Connection connection, InvocationEvent ie) {
        for (int index : ie.method.localParamIndices) {
            Object[] params = ie.params;
            int objectId = (int) params[index];
            params[index] = null;
            if (objectId == -1) continue;
            params[index] = oidToObj.containsKey(objectId) ? oidToObj.get(objectId) : createRemote(connection, ie.method.argClasses[index]);
        }
        return ie;
    }

    protected ExecutionEvent hostResult(Connection connection, ExecutionEvent ee) {
        if (!ee.method.isResLocal) return ee;
        Object param = ee.result;
        ee.result = -1;
        if (param == null) return ee;
        if (!objToOid.containsKey(param)) hostObject(connection, param);
        ee.result = objToOid.get(param, -1);
        return ee;
    }

}
