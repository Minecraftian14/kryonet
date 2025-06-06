package com.esotericsoftware.kryonet.rmi2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

class CachedMethod {
    final int id;
    final Method reflection;
    final RMI rmi;
    final Class<?>[] argClasses;
    final Class<?>[] serClasses;
    final Class<?> resClass;
    final boolean isResLocal;

    final int[] localParamIndices;

    public CachedMethod(int id, Method method) {
        this.id = id;
        this.reflection = method;

        this.rmi = RMI.Helper.getRMI(method);
        this.argClasses = method.getParameterTypes();
        this.serClasses = argClasses.clone();
        this.resClass = method.getReturnType();
        this.isResLocal = method.isAnnotationPresent(RMI.Closure.class);

        Parameter[] parameters = method.getParameters();
        int localParamCount = 0;
        for (Parameter parameter : parameters)
            if (RMI.Helper.isLocal(parameter))
                localParamCount++;
        this.localParamIndices = new int[localParamCount];
        localParamCount = 0;
        for (int i = 0; i < parameters.length; i++)
            if (RMI.Helper.isLocal(parameters[i]))
                localParamIndices[localParamCount++] = i;

        // BOGUS TRICK...
        for (int paramIndex : localParamIndices)
            serClasses[paramIndex] = int.class;
    }

    @Override
    public String toString() {
        return "CachedMethod{" +
               "id=" + id +
               ", reflection=" + reflection +
               ", rmi=" + rmi +
               ", localParamIndices=" + Arrays.toString(localParamIndices) +
               '}';
    }

    Object invokeMethod(Object object, InvocationEvent it) {
        String x = "CachedMethod.invokeMethod(" + object + ",\n" + it + ")";
        try {
            System.out.println(x);
            return reflection.invoke(object, it.params);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(x, e);
        }
    }

    public boolean returnsNotRequired() {
        return void.class.equals(resClass) || rmi.returnsNotRequired();
    }
}
