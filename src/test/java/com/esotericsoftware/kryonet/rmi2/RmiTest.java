package com.esotericsoftware.kryonet.rmi2;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RmiTest extends KryoNetTestCase {

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

    class SimpleFunctionsImpl implements SimpleFunctions {
        Server server;

        public SimpleFunctionsImpl(Server server) {
            this.server = server;
        }

        @Override
        public void runnable() {
            server.sendToAllTCP("runnable");
        }

        @Override
        public void consumer(int object) {
            server.sendToAllTCP("consumer" + object);
        }

        @Override
        public double supplier() {
            server.sendToAllTCP("supplier");
            return Math.PI;
        }

        @Override
        public String function(Holder object) {
            server.sendToAllTCP("function" + object.value);
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

    class ClosureTestImpl implements ClosureTest {
        Server server;

        public ClosureTestImpl(Server server) {
            this.server = server;
        }

        @Override
        public void forwardConsumer(IntConsumer consumer) {
            server.sendToAllTCP("forwardConsumer");
            consumer.accept(1114_1);
        }

        @Override
        public void forwardSupplier(IntSupplier supplier) {
            int asInt = supplier.getAsInt();
            server.sendToAllTCP("forwardSupplier" + asInt);
            assertEquals(1114_2, asInt);
        }

        @Override
        public IntConsumer backwardConsumer() {
            return integer -> {
                server.sendToAllTCP("backwardConsumer" + integer);
                assertEquals(1114_3, integer);
            };
        }

        @Override
        public IntSupplier backwardSupplier() {
            server.sendToAllTCP("backwardSupplier");
            return () -> 1114_4;
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

        kryo.register(Holder.class);
        kryo.register(SimpleFunctions.class);
        kryo.register(ClosureTest.class);
    }

    @Test
    void testRmiUsingRegistry() throws IOException {
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
        functions.backwardConsumer().accept(1114_3);
        receivedClosures.add("backwardConsumer");
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
}
