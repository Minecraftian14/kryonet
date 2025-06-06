package com.esotericsoftware.kryonet.rmi2;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RMI {

    boolean local() default false;

    boolean useUdp() default false;

    boolean nonBlocking() default false;

    boolean noReturns() default false;

    int responseTimeout() default 3000;

    boolean transmitExceptions() default true;

    boolean remoteToString() default false;

    boolean remoteHashCode() default false;

    boolean closed() default false;

    interface RMISupplier {
        RMI getRMI();
    }

    class RMIImpl implements RMI {

        final boolean isLocal;
        final boolean isUdp;
        final boolean isNonBlocking;
        final boolean returnsNotRequired;
        final int responseTimeout;
        final boolean transmitExceptions;
        final boolean remoteToString;
        final boolean remoteHashCode;
        final boolean closed;
        final Class<? extends Annotation> annotationType;

        public RMIImpl(boolean isLocal, boolean isUdp, boolean isNonBlocking, boolean returnsNotRequired, int responseTimeout, boolean transmitExceptions, boolean remoteToString, boolean remoteHashCode, boolean closed, Class<? extends Annotation> annotationType) {
            this.isLocal = isLocal;
            this.isUdp = isUdp;
            this.isNonBlocking = isNonBlocking;
            this.returnsNotRequired = returnsNotRequired;
            this.responseTimeout = responseTimeout;
            this.transmitExceptions = transmitExceptions;
            this.remoteToString = remoteToString;
            this.remoteHashCode = remoteHashCode;
            this.closed = closed;
            this.annotationType = annotationType;
        }

        public boolean local() {
            return isLocal;
        }

        public boolean useUdp() {
            return isUdp;
        }

        public boolean nonBlocking() {
            return isNonBlocking;
        }

        @Override
        public boolean noReturns() {
            return returnsNotRequired;
        }

        @Override
        public int responseTimeout() {
            return responseTimeout;
        }

        @Override
        public boolean transmitExceptions() {
            return transmitExceptions;
        }

        @Override
        public boolean remoteToString() {
            return remoteToString;
        }

        @Override
        public boolean remoteHashCode() {
            return remoteHashCode;
        }

        @Override
        public boolean closed() {
            return closed;
        }

        public Class<? extends Annotation> annotationType() {
            return annotationType;
        }

        @Override
        public String toString() {
            return "RMIImpl{" + "isLocal=" + isLocal + ", isUdp=" + isUdp + ", isNonBlocking=" + isNonBlocking + ", returnsNotRequired=" + returnsNotRequired + ", responseTimeout=" + responseTimeout + ", transmitExceptions=" + transmitExceptions + ", remoteToString=" + remoteToString + ", remoteHashCode=" + remoteHashCode + ", closed=" + closed + ", annotationType=" + annotationType + '}';
        }
    }

    class Helper {

        static RMI getRMI(Method method) {
            boolean isLocal = false;
            boolean isUdp = false;
            boolean isNonBlocking = false;
            boolean returnsNotRequired = false;
            int responseTimeout = 3000;
            boolean transmitExceptions = true;
            boolean remoteToString = false;
            boolean remoteHashCode = false;
            boolean closed = false;
            Class<? extends Annotation> annotationType = RMI.class;

            if (method.isAnnotationPresent(RMI.class)) {
                RMI rmi = method.getAnnotation(RMI.class);
                isLocal = rmi.local();
                isUdp = rmi.useUdp();
                isNonBlocking = rmi.nonBlocking();
                returnsNotRequired = rmi.noReturns();
                responseTimeout = rmi.responseTimeout();
                annotationType = rmi.annotationType();
                transmitExceptions = rmi.transmitExceptions();
                remoteToString = rmi.remoteToString();
                remoteHashCode = rmi.remoteHashCode();
                closed = rmi.closed();
            }

            isLocal |= method.isAnnotationPresent(Local.class);
            isUdp |= method.isAnnotationPresent(UDP.class);
            isNonBlocking |= method.isAnnotationPresent(NonBlocking.class);
            returnsNotRequired |= method.isAnnotationPresent(NoReturns.class);
            returnsNotRequired |= void.class.equals(method.getReturnType());
            transmitExceptions &= method.isAnnotationPresent(LocalExceptions.class);
            remoteToString |= method.isAnnotationPresent(RemoteToString.class);
            remoteHashCode |= method.isAnnotationPresent(RemoteHashCode.class);
            if (method.isAnnotationPresent(ResponseTimeout.class)) responseTimeout = method.getAnnotation(ResponseTimeout.class).value();

            return new RMIImpl(isLocal, isUdp, isNonBlocking, returnsNotRequired, responseTimeout, transmitExceptions, remoteToString, remoteHashCode, closed, annotationType);
        }

        static boolean isLocal(Parameter parameter) {
            return parameter.isAnnotationPresent(Local.class)
                   || isValidClosure(parameter);
        }

        public static boolean isLocal(Method method) {
            return method.isAnnotationPresent(Local.class)
                   || (method.isAnnotationPresent(RMI.class) && method.getAnnotation(RMI.class).local());
        }

        static boolean isValidClosure(Parameter parameter) {
            return parameter.getType().isInterface()
                   && parameter.getType().isAnnotationPresent(FunctionalInterface.class)
                   && parameter.isAnnotationPresent(Closure.class);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.PARAMETER})
    @interface Local {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface UDP {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface NonBlocking {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface NoReturns {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ResponseTimeout {
        int value() default 3000;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.PARAMETER})
    @interface Closure {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface LocalExceptions {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface RemoteToString {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface RemoteHashCode {
    }

}
