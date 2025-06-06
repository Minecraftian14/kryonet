package com.esotericsoftware.kryonet.rmi2;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RMI {

    boolean isLocal() default false;

    boolean isUdp() default false;

    boolean isNonBlocking() default false;

    boolean returnsNotRequired() default false;

    int responseTimeout() default 3000;

    class RMIImpl implements RMI {

        final boolean isLocal;
        final boolean isUdp;
        final boolean isNonBlocking;
        final boolean returnsNotRequired;
        final int responseTimeout;
        final Class<? extends Annotation> annotationType;

        public RMIImpl(boolean isLocal, boolean isUdp, boolean isNonBlocking, boolean returnsNotRequired, int responseTimeout, Class<? extends Annotation> annotationType) {
            this.isLocal = isLocal;
            this.isUdp = isUdp;
            this.isNonBlocking = isNonBlocking;
            this.returnsNotRequired = returnsNotRequired;
            this.responseTimeout = responseTimeout;
            this.annotationType = annotationType;
        }

        public boolean isLocal() {
            return isLocal;
        }

        public boolean isUdp() {
            return isUdp;
        }

        public boolean isNonBlocking() {
            return isNonBlocking;
        }

        @Override
        public boolean returnsNotRequired() {
            return returnsNotRequired;
        }

        public int responseTimeout() {
            return responseTimeout;
        }

        public Class<? extends Annotation> annotationType() {
            return annotationType;
        }

        @Override
        public String toString() {
            return "RMIImpl{" +
                   "isLocal=" + isLocal +
                   ", isUdp=" + isUdp +
                   ", isNonBlocking=" + isNonBlocking +
                   ", returnsNotRequired=" + returnsNotRequired +
                   ", responseTimeout=" + responseTimeout +
                   '}';
        }
    }

    class Helper {

        static RMI getRMI(Method method) {
            boolean isLocal = false;
            boolean isUdp = false;
            boolean isNonBlocking = false;
            boolean returnsNotRequired = false;
            int responseTimeout = 3000;
            Class<? extends Annotation> annotationType = RMI.class;

            if (method.isAnnotationPresent(RMI.class)) {
                RMI rmi = method.getAnnotation(RMI.class);
                isLocal = rmi.isLocal();
                isUdp = rmi.isUdp();
                isNonBlocking = rmi.isNonBlocking();
                returnsNotRequired = rmi.returnsNotRequired();
                responseTimeout = rmi.responseTimeout();
                annotationType = rmi.annotationType();
            }

            isLocal |= method.isAnnotationPresent(Local.class);
            isUdp |= method.isAnnotationPresent(UDP.class);
            isNonBlocking |= method.isAnnotationPresent(NonBlocking.class);
            returnsNotRequired |= method.isAnnotationPresent(NoReturns.class);
            if (method.isAnnotationPresent(ResponseTimeout.class)) responseTimeout = method.getAnnotation(ResponseTimeout.class).value();

            return new RMIImpl(isLocal, isUdp, isNonBlocking, returnsNotRequired, responseTimeout, annotationType);
        }

        static boolean isRMIAnnotationPresent(Method method) {
            return method.isAnnotationPresent(RMI.class)
                   || method.isAnnotationPresent(Local.class);
            // TODO
        }

        static boolean isLocal(Parameter parameter) {
            return parameter.isAnnotationPresent(Local.class)
                   || isValidClosure(parameter);
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
    @Target(ElementType.TYPE)
    @interface NonBlocking {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface NoReturns {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface ResponseTimeout {
        int value() default 3000;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.PARAMETER})
    @interface Closure {
    }
}
