package com.the123saurav.gossip;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.the123saurav.common.Error;
import com.the123saurav.common.IJson;
import com.the123saurav.common.Logger;
import com.the123saurav.common.Message;
import com.the123saurav.common.Node;
import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.concurrent.*;

public class GossipHandler extends Node {

    private ArrayList<String> gossipNodes = new ArrayList<>();

    private final IJson broadcastResponse = new BroadcastResponse();
    private final IJson gossipResponse = new GossipResponse();
    private final IJson areYouThereResponse = new AreYouThereResponse();
    private final IJson topologyResponse = new TopologyResponse();
    private final ConcurrentHashMap<Long, Boolean> messages = new ConcurrentHashMap(10000);
    private final ConcurrentHashMap<String, ArrayList<Long>> pendingMessages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> communicationLog = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> preferredNodes = new ConcurrentHashMap();
    private final Executor pendingMessageExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @RequiredArgsConstructor
    class BroadcastResponse implements IJson {
        private static final String TYPE = "broadcast_ok";

        @Override
        public JsonValue toJson() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("type", TYPE);
            return jsonObject;
        }
    }

    @RequiredArgsConstructor
    class GossipMessage {
        private final long message;
//        private final int round;

        public JsonObject toJson() {
            return new JsonObject()
                    .add("type", "gossip")
                    .add("message", message);
//                    .add("round", round);
        }

    }

    @RequiredArgsConstructor
    class GossipResponse implements IJson {
        private static final String TYPE = "gossip_ok";

        @Override
        public JsonValue toJson() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("type", TYPE);
            return jsonObject;
        }
    }

    @RequiredArgsConstructor
    class AreYouThereResponse implements IJson {
        private static final String TYPE = "areyouthere_ok";

        @Override
        public JsonValue toJson() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("type", TYPE);
            return jsonObject;
        }
    }

    @RequiredArgsConstructor
    class ReadResponse implements IJson {
        private static final String TYPE = "read_ok";

        @Override
        public JsonValue toJson() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("type", TYPE);

            JsonArray jsonValues = new JsonArray();
            messages.keys().asIterator().forEachRemaining(jsonValues::add);

            jsonObject.add("messages", jsonValues);
            return jsonObject;
        }
    }

    @RequiredArgsConstructor
    class TopologyResponse implements IJson {
        private static final String TYPE = "topology_ok";

        @Override
        public JsonValue toJson() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("type", TYPE);
            return jsonObject;
        }
    }


    private void setGossipNodes() {
        int numNodes = this.nodeIds.size();
        for (int i = 1; i <= Math.log(numNodes); i++) {
            gossipNodes.add(this.nodeIds.get((int) ((this.nodeIdNumber + (int) Math.pow(2, i - 1)) % numNodes)));
        }
        Logger.log(String.format("Gossip nodes for node %s is %s", nodeId, gossipNodes));
    }

    @Override
    protected void handleInit(Message message) {
        super.handleInit(message);
        setGossipNodes();
    }

    @Override
    public void handle(Message message) {
        switch (message.body.getString("type", null)) {
            case "broadcast":
                handleBroadcast(message);
                break;
            case "gossip":
                handleGossip(message);
                break;
            case "areyouthere":
                handleAreYouThere(message);
                break;
            case "read":
                handleRead(message);
                break;
            case "topology":
                handleTopology(message);
                break;
            default:
                throw new RuntimeException("unhandled message type: " + message.body.getString("type", null));
        }
    }

    private void handleTopology(Message message) {
        reply(message, topologyResponse);
    }

    private void handleBroadcast(Message message) {
        // Ack first
        reply(message, broadcastResponse);
        doHandleGossip(message);
    }

    private void handleGossip(Message message) {
        // Ack first
        reply(message, gossipResponse);

        // Update that we have heard from the node
        communicationLog.put(message.src, System.currentTimeMillis());

        doHandleGossip(message);
    }

    private void doHandleGossip(Message message) {
        long val = message.body.getLong("message", -1);

        // Check if message is already seen
        if (messages.get(val) != null) {
//            Logger.log("Received duplicate message: " + val);
            return;
        }
        messages.put(val, true);

//        int round = message.body.getInt("round", 0);

        preferredNodes.keySet().forEach(node -> sendMessage(node, val));
        gossipNodes.forEach(node -> sendMessage(node, val));
    }

    private void handleAreYouThere(Message message) {
        Logger.log("Received are-you-there from node:" + message.src);
        preferredNodes.put(message.src, true);
        reply(message, areYouThereResponse);
    }

    private void handleRead(Message message) {
        Logger.log("Received read from client:" + message.src);
        reply(message, new ReadResponse());
    }

    private void sendMessage(String dest, long val) {
//        Logger.log("sending message:  " + val + " to node: " + dest);
        CompletableFuture<JsonObject> f = rpc(dest, new GossipMessage(val).toJson());

        f.handle((result, exception) -> {
            if (exception != null) {
                Error error = (Error) exception;
                Logger.log("RPC call completed with error" + error.toJson());
                if (error.code == 0 || error.code == 11) {
                    pendingMessages.compute(dest, (k, msgs) -> {
                        if (msgs == null) {
                            msgs = new ArrayList<>();
                        }
                        msgs.add(val);
                        return msgs;
                    });
                }
            } else {
                // Trigger pending message handling
                ArrayList<Long> pendingMessagesForNode = pendingMessages.get(dest);
                if (pendingMessagesForNode != null && pendingMessagesForNode.size() > 0) {
                    // Remove to prevent duplicate processing
                    pendingMessages.remove(dest);

                    pendingMessageExecutor.execute(() -> {
                        pendingMessagesForNode.forEach(msg -> sendMessage(dest, msg));
                    });
                }
            }
            return null;
        });

        // Add to timeout detector
        timeoutExecutor.schedule(() -> {
            if (!f.isDone()) {
                Logger.log("Timeout on message: " + val + " to node: " + dest);
                pendingMessages.compute(dest, (k, msgs) -> {
                    if (msgs == null) {
                        msgs = new ArrayList<>();
                    }
                    msgs.add(val);
                    return msgs;
                });
            }
        }, 10, TimeUnit.MILLISECONDS);
    }
}
