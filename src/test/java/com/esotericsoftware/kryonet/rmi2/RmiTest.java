package com.esotericsoftware.kryonet.rmi2;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.*;
import com.esotericsoftware.kryonet.rmi.RemoteObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RmiTest extends KryoNetTestCase {

    static class TestHelper {
        Server server;

        public TestHelper(Server server) {
            this.server = server;
        }

        void send(String message) {
            server.sendToAllTCP(message);
        }
    }

    public static class Holder {
        public long value;

        public Holder() {
        }

        public Holder(long value) {
            this.value = value;
        }
    }

    interface SimpleFunctions {
        void runnable();

        void consumer(int object);

        double supplier();

        String function(Holder object);
    }

    static class SimpleFunctionsImpl extends TestHelper implements SimpleFunctions {

        public SimpleFunctionsImpl(Server server) {
            super(server);
        }

        @Override
        public void runnable() {
            send("runnable");
        }

        @Override
        public void consumer(int object) {
            send("consumer" + object);
        }

        @Override
        public double supplier() {
            send("supplier");
            return Math.PI;
        }

        @Override
        public String function(Holder object) {
            send("function" + object.value);
            return "Server";
        }
    }

    interface ClosureTest {
        void forwardConsumer(@RMI.Closure IntConsumer consumer);

        void forwardSupplier(@RMI.Closure IntSupplier supplier);

        @RMI.Closure
        IntConsumer backwardConsumer();

        @RMI.Closure
        IntSupplier backwardSupplier();
    }

    class ClosureTestImpl extends TestHelper implements ClosureTest {

        public ClosureTestImpl(Server server) {
            super(server);
        }

        @Override
        public void forwardConsumer(IntConsumer consumer) {
            send("forwardConsumer");
            consumer.accept(1114_1);
        }

        @Override
        public void forwardSupplier(IntSupplier supplier) {
            int asInt = supplier.getAsInt();
            send("forwardSupplier" + asInt);
            assertEquals(1114_2, asInt);
        }

        @Override
        public IntConsumer backwardConsumer() {
            return integer -> {
                send("backwardConsumer" + integer);
                assertEquals(1114_3, integer);
            };
        }

        @Override
        public IntSupplier backwardSupplier() {
            send("backwardSupplier");
            return () -> 1114_4;
        }
    }

    interface A {
        void a();
    }

    interface B {
        void b();
    }

    interface C {
        void c();
    }

    interface D {
        void d();
    }

    class AImpl extends TestHelper implements A {
        public AImpl(Server server) {
            super(server);
        }

        @Override
        public void a() {
            send("a");
        }
    }

    class BImpl extends TestHelper implements B {
        public BImpl(Server server) {
            super(server);
        }

        @Override
        public void b() {
            send("b");
        }
    }

    class CImpl extends TestHelper implements C {
        public CImpl(Server server) {
            super(server);
        }

        @Override
        public void c() {
            send("c");
        }
    }

    class DImpl implements D {
        Connection connection;

        public DImpl(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void d() {
            connection.sendTCP("d");
        }
    }

    interface Annotations {

        default int process1(int a) {
            return a | 0b1;
        }

        @RMI.Local
        default int process2(int a) {
            return a | 0b10;
        }

        @RMI.UDP
        default int process4(int a) {
            return a | 0b100;
        }

        @RMI.NonBlocking
        default int process8(int a) {
            return a | 0b1000;
        }

        @RMI.NoReturns
        default int process16(int a) {
            return a | 0b10000;
        }

        @RMI.ResponseTimeout(10_000)
        default int process32(int a) {
            return a | 0b100000;
        }

        @RMI.LocalExceptions
        default int process64(int a) {
            return a | 0b1000000;
        }

        // TODO MOVE THESE TO TYPE INSTEAD OF METHOD
        @RMI.RemoteToString
        default int process128(int a) {
            return a | 0b10000000;
        }

        @RMI.RemoteHashCode
        default int process256(int a) {
            return a | 0b100000000;
        }

        @RMI(local = true)
        default int process512(int a) {
            return a | 0b1000000000;
        }

        @RMI(useUdp = true, nonBlocking = true)
        default int process1024(int a) {
            return a | 0b10000000000;
        }

        @RMI(noReturns = true)
        default int process2048(int a) {
            return a | 0b100000000000;
        }

        @RMI(responseTimeout = 10000)
        default int process4096(int a) {
            return a | 0b1000000000000;
        }

        @RMI(remoteToString = true, remoteHashCode = true)
        default int process8192(int a) {
            return a | 0b10000000000000;
        }

        @RMI(closed = true)
        default int process16384(int a) {
            return a | 0b100000000000000;
        }

    }

    class AnnotationsImpl implements Annotations {
        @Override
        public int process32(int a) {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Annotations.super.process32(a);
        }

        @Override
        public int process4096(int a) {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Annotations.super.process4096(a);
        }
    }

    public void register(Kryo kryo, ObjectSpaceV2 registry) {
        super.register(kryo);

        registry.registerEvents(kryo);
        registry.registerRemotable(Object.class);
        registry.registerRemotable(SimpleFunctions.class);
        registry.registerRemotable(ClosureTest.class);
        registry.registerRemotable(IntConsumer.class);
        registry.registerRemotable(IntSupplier.class);
        registry.registerRemotable(A.class);
        registry.registerRemotable(B.class);
        registry.registerRemotable(C.class);
        registry.registerRemotable(D.class);
        registry.registerRemotable(Annotations.class);

        kryo.register(Holder.class);
    }

    @Test
    void testSimpleFunctions() throws IOException {
        List<String> receivedEvents = new ArrayList<>(4);

        Server server = new Server();
        ObjectSpaceV2 serverRegistry = new ObjectSpaceV2();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);
        serverRegistry.hostObject(server, new SimpleFunctionsImpl(server));

        Client client = new Client();
        ObjectSpaceV2 clientRegistry = new ObjectSpaceV2();
        register(client.getKryo(), clientRegistry);
        client.addListener(Listener.received(String.class, (connection, object) -> receivedEvents.add((String) object)));
        SimpleFunctions functions = clientRegistry.createRemote(client, SimpleFunctions.class);
        startEndPoint(client);
        client.connect(5000, host, tcpPort, udpPort);

        functions.runnable();
        functions.consumer(1234554321);
        assertEquals(Math.PI, functions.supplier());
        assertEquals("Server", functions.function(new Holder(1234567890987654321L)));

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();

        assertEquals("runnable", receivedEvents.get(0));
        assertEquals("consumer1234554321", receivedEvents.get(1));
        assertEquals("supplier", receivedEvents.get(2));
        assertEquals("function1234567890987654321", receivedEvents.get(3));
    }

    @Test
    void testClosures() throws IOException {
        List<String> receivedEvents = new ArrayList<>(4);
        List<String> receivedClosures = new ArrayList<>(4);

        Server server = new Server();
        ObjectSpaceV2 serverRegistry = new ObjectSpaceV2();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);
        serverRegistry.hostObject(server, new ClosureTestImpl(server));

        Client client = new Client();
        ObjectSpaceV2 clientRegistry = new ObjectSpaceV2();
        register(client.getKryo(), clientRegistry);
        client.addListener(Listener.received(String.class, (connection, object) -> receivedEvents.add((String) object)));
        ClosureTest functions = clientRegistry.createRemote(client, ClosureTest.class);
        clientRegistry.nextProxyId = 10;
        startEndPoint(client);
        client.connect(5000, host, tcpPort, udpPort);

        functions.forwardConsumer(integer -> {
            receivedClosures.add("forwardConsumer" + integer);
            assertEquals(1114_1, integer);
        });
        functions.forwardSupplier(() -> {
            receivedClosures.add("forwardSupplier");
            return 1114_2;
        });
        functions.backwardConsumer()
            .andThen(i -> receivedClosures.add("backwardConsumer"))
            .accept(1114_3);
//        receivedClosures.add("backwardConsumer");
        int asInt = functions.backwardSupplier().getAsInt();
        assertEquals(1114_4, asInt);
        receivedClosures.add("backwardSupplier" + asInt);

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();

        assertEquals("forwardConsumer", receivedEvents.get(0));
        assertEquals("forwardConsumer11141", receivedClosures.get(0));
        assertEquals("forwardSupplier11142", receivedEvents.get(1));
        assertEquals("forwardSupplier", receivedClosures.get(1));
        assertEquals("backwardConsumer11143", receivedEvents.get(2));
        assertEquals("backwardConsumer", receivedClosures.get(2));
        assertEquals("backwardSupplier", receivedEvents.get(3));
        assertEquals("backwardSupplier11144", receivedClosures.get(3));
    }

    @Test
    void testJumble() throws IOException {
        List<String> receivedEvents = new ArrayList<>(4);

        Server server = new Server();
        ObjectSpaceV2 serverRegistry = new ObjectSpaceV2();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);
        serverRegistry.hostObject(server, 3, new CImpl(server));
        serverRegistry.hostObject(server, 2, new BImpl(server));
        serverRegistry.hostObject(server, 1, new AImpl(server));
        D[] d = new D[1];
        server.addListener(Listener.connected(connection -> {
            d[0] = serverRegistry.createRemote(connection, 4, D.class);
            connection.addListener(Listener.received(String.class, (con, object) -> receivedEvents.add((String) object)));
        }));

        Client client = new Client();
        ObjectSpaceV2 clientRegistry = new ObjectSpaceV2();
        register(client.getKryo(), clientRegistry);
        client.addListener(Listener.received(String.class, (connection, object) -> receivedEvents.add((String) object)));
        C c = clientRegistry.createRemote(client, 3, C.class);
        clientRegistry.hostObject(client, 4, new DImpl(client));
        A a = clientRegistry.createRemote(client, 1, A.class);
        B b = clientRegistry.createRemote(client, 2, B.class);
        clientRegistry.nextProxyId = 10;
        startEndPoint(client);
        client.connect(5000, host, tcpPort, udpPort);

        a.a();
        b.b();
        c.c();
        d[0].d();

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();

        assertTrue(receivedEvents.remove("d"));
        assertEquals("a", receivedEvents.get(0));
        assertEquals("b", receivedEvents.get(1));
        assertEquals("c", receivedEvents.get(2));
    }

    @Test
    void testAnnotations() throws IOException {
        Server server = new Server();
        ObjectSpaceV2 serverRegistry = new ObjectSpaceV2();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);
        serverRegistry.hostObject(server, new AnnotationsImpl());

        Client client = new Client();
        ObjectSpaceV2 clientRegistry = new ObjectSpaceV2();
        register(client.getKryo(), clientRegistry);
        Annotations a = clientRegistry.createRemote(client, Annotations.class);
        startEndPoint(client);
        client.connect(5000, host, tcpPort, udpPort);

        int x = 0;
        x = a.process1(x);
//      x = a.process2(x); // Local only
        x = a.process4(x);
        a.process8(x); // Non Blocking
        x = (int) clientRegistry.getLastResult();
        a.process16(x); // No Returns
        x = a.process32(x);
        x = a.process64(x);
        x = a.process128(x);
        x = a.process256(x);
//      x = a.process512(x); // Local only
        a.process1024(x); // Non Blocking
        x = (int) clientRegistry.getLastResult();
        a.process2048(x); // No Returns
        x = a.process4096(x);
        x = a.process8192(x);
        a.process16384(x); // Closed
        assertEquals(0b011010111101101, x);

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();
    }

    @Test
    void testDelegate() throws IOException {
        Server server = new Server();
        ObjectSpaceV2 serverRegistry = new ObjectSpaceV2();
        register(server.getKryo(), serverRegistry);
        startEndPoint(server);
        server.bind(tcpPort, udpPort);
        serverRegistry.hostObject(server, new SimpleFunctionsImpl(server));

        Client client = new Client();
        ObjectSpaceV2 clientRegistry = new ObjectSpaceV2();
        register(client.getKryo(), clientRegistry);
        SimpleFunctions s = clientRegistry.createRemote(client, SimpleFunctions.class, new DelegateObject(clientRegistry, client), RemoteObject.class);
        RemoteObject r = (RemoteObject) s;
        startEndPoint(client);
        client.connect(5000, host, tcpPort, udpPort);

        assertEquals(Math.PI, s.supplier());
        r.setNonBlocking(true);
        assertEquals(0.0, s.supplier());
        assertEquals(Math.PI, r.waitForLastResponse());

        stopEndPoints(2000);
        waitForThreads();
        server.stop();
        server.close();
    }
}
