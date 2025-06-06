package com.esotericsoftware.kryonet.rmi2;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.IntMap;
import com.esotericsoftware.kryo.util.ObjectMap;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.util.ObjectIntMap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.esotericsoftware.kryonet.rmi2.ExecutionEvent.obtainEE;

public class ObjectSpaceV2 {

    protected int nextObjectId = 0;
    protected int nextMethodId = 0;
    protected int nextProxyId = 0;

    final ObjectIntMap<Class<?>> clsReg = new ObjectIntMap<>();

    final IntMap<CachedMethod> midToCMet = new IntMap<>();
    final ObjectIntMap<Method> metToMid = new ObjectIntMap<>();

    // Hosts
    final IntMap<Object> oidToObj = new IntMap<>();
    final ObjectIntMap<Object> objToOid = new ObjectIntMap<>();

    // Proxies
    final ObjectMap<Connection, IntMap<Object>> proxies = new ObjectMap<>();

    final ExecutorService executor = Executors.newSingleThreadExecutor();

    final InvocationEvent.Handler invocationHandler = new InvocationEvent.Handler(this);
    final ExecutionEvent.Handler executionHandler = new ExecutionEvent.Handler(this);
    final IntMap<AsyncExecution> asyncExecutions = new IntMap<>();
    protected int lastTransactionId = -1;

    final AtomicInteger transactionIdSupplier = new AtomicInteger();
    final MultiResultLock<ExecutionEvent> executionLock = new MultiResultLock<>();

    // Class Initialization

    public ObjectSpaceV2 registerRemotable(Class<?> clazz) {
        if (clsReg.containsKey(clazz)) throw new IllegalArgumentException("Class " + clazz + " is already registered.");
        clsReg.put(clazz, 0);
        registerMethods(clazz);
        return this;
    }

    protected void registerMethods(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            int modifiers = method.getModifiers();
            if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)) continue;
            if (RMI.Helper.isLocal(method)) continue;
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

    // Host Management

    protected void saveObject(int objectId, Class<?> clazz, Object object) {
        if (oidToObj.containsKey(objectId)) throw new IllegalArgumentException("Object id " + objectId + " already configured for object " + oidToObj.get(objectId));
        nextObjectId = objectId + 1;
        objToOid.put(object, objectId);
        oidToObj.put(objectId, object);
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

    // Remote Management

    @SuppressWarnings("unchecked")
    public <T> T createRemote(
        final Connection connection,
        final int objectId, final Class<T> clazz,
        final RMI.RMISupplier delegate,
        final Class<?> delegationClass) {

        assert (delegate == null) == (delegationClass == null);
        assert (delegate == null) || (delegationClass.isInstance(delegate));

        if (!proxies.containsKey(connection)) proxies.put(connection, new IntMap<>());
        IntMap<Object> cache = proxies.get(connection);
        if (cache.containsKey(objectId)) return (T) cache.get(objectId);

        Object remote = Proxy.newProxyInstance(clazz.getClassLoader(), delegate == null ? new Class[]{clazz} : new Class[]{delegationClass, clazz},
            (proxy, method, args) -> invokeMethod(connection, objectId, delegate, method, args));

        cache.put(objectId, remote);

        connection.addListener(executionHandler);
        return (T) remote;
    }

    public <T> T createRemote(Connection connection, int objectId, Class<T> clazz) {
        return createRemote(connection, objectId, clazz, null, null);
    }

    public <T> T createRemote(Connection connection, Class<T> clazz, RMI.RMISupplier delegate, Class<?> delegationClass) {
        return createRemote(connection, nextProxyId++, clazz, delegate, delegationClass);
    }

    public <T> T createRemote(Connection connection, Class<T> clazz) {
        return createRemote(connection, clazz, null, null);
    }

    // Invocation Management

    void send(CachedMethod method, Connection connection, Object object) {
        if (method.rmi.useUdp()) connection.sendUDP(object);
        else connection.sendTCP(object);
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

    boolean delegationRequired(RMI.RMISupplier remote, Method method) {
        return remote != null && method.getDeclaringClass().isAssignableFrom(remote.getClass());
    }

    Object delegate(RMI.RMISupplier delegate, Method method, Object[] params) {
        try {
            return method.invoke(delegate, params);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    Object invokeMethod(Connection connection, int objectId, RMI.RMISupplier delegate, Method method, Object[] params) {
        if (delegationRequired(delegate, method))
            return delegate(delegate, method, params);
        int methodId = metToMid.get(method, -1);
        CachedMethod cMethod = midToCMet.get(methodId);
        RMI rmi = delegate != null ? delegate.getRMI() : cMethod.rmi;
        if (rmi.closed()) return primitize(null, cMethod.resClass);
        int transactionId = lastTransactionId = transactionIdSupplier.getAndIncrement();

        System.out.println("RemoteObjectRegistry.invokeMethod " + transactionId + " :: " + method + " >>> " + cMethod);
        send(cMethod, connection, hostParams(connection, InvocationEvent.obtainIE(transactionId, objectId, cMethod, params)));
        System.out.println("POSSIBLY WAITING FOR ExecutionEvent ON THREAD " + Thread.currentThread().getName());
        if (rmi.noReturns()) return primitize(null, cMethod.resClass);
        if (rmi.nonBlocking()) {
            asyncExecutions.put(transactionId, AsyncExecution.obtainAE(connection, rmi.responseTimeout()));
            return primitize(null, cMethod.resClass);
        }
        // Wait for the ExecutionEvent. which contains the result.
        // If the return type is supposed to be a remote object as well, the result must be an object id.
        // Replace the id with the actual remote object.
        // Finally, retrieve the result and free the ExecutionEvent.
        return createRemoteResult(connection, executionLock.read(transactionId, rmi.responseTimeout())).use();
    }

    InvocationEvent hostParams(Connection connection, InvocationEvent ie) {
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

    ExecutionEvent createRemoteResult(Connection connection, ExecutionEvent ee) {
        if (!ee.method.isResLocal) return ee;
        int objectId = (int) ee.result;
        ee.result = null;
        if (objectId == -1) return ee;
        ee.result = createRemote(connection, objectId, ee.method.resClass);
        return ee;
    }

    void invokeMethod(Connection connection, InvocationEvent ie) {
        Object object = oidToObj.get(ie.objectId);

        executor.submit(() -> {
            Object result = ie.method.invokeMethod(object, createRemoteParams(connection, ie));
            if (ie.method.rmi.noReturns()) return;
            send(ie.method, connection, hostResult(connection, obtainEE(ie.transactionId, ie.objectId, ie.method, result)));
            ie.close();
        });
    }

    protected InvocationEvent createRemoteParams(Connection connection, InvocationEvent ie) {
        for (int index : ie.method.localParamIndices) {
            Object[] params = ie.params;
            int objectId = (int) params[index];
            params[index] = null;
            if (objectId == -1) continue;
            params[index] = createRemote(connection, objectId, ie.method.argClasses[index]);
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

    // Result Management

    public boolean hasAnyTransaction() {
        return hasTransaction(lastTransactionId);
    }

    public boolean hasTransaction(int transactionId) {
        return executionLock.containsKey(transactionId);
    }

    public boolean hasLastResult() {
        return hasResult(lastTransactionId);
    }

    public boolean hasResult(int transactionId) {
        return executionLock.containsValue(transactionId);
    }

    public int getLastTransactionId() {
        return lastTransactionId;
    }

    public Object getLastResult() {
        return getLastResult(-1);
    }

    public Object getLastResult(long responseTimeout) {
        return getResult(lastTransactionId, responseTimeout);
    }

    public Object getResult(int transactionId) {
        return getResult(transactionId, -1);
    }

    public Object getResult(int transactionId, long responseTimeout) {
        AsyncExecution ae = asyncExecutions.get(transactionId);
        Connection connection = ae.connection;
        responseTimeout = Math.max(responseTimeout, ae.responseTimeout);
        ae.close();
        return createRemoteResult(connection, executionLock.read(transactionId, responseTimeout)).use();
    }

}
