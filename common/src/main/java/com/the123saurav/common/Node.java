package com.the123saurav.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import static com.the123saurav.common.Logger.log;

// This class provides common support functions for writing Maelstrom nodes. It includes an asynchronous RPC facility, and uses an executor to launch handlers.
public abstract class Node {
    // Our local node ID.
    protected String nodeId = "uninitialized";
    protected long nodeIdNumber = -1;

    // All node IDs
    protected List<String> nodeIds = new ArrayList<String>();

    // A map of RPC request message IDs we've sent to CompletableFutures which will
    // receive the response bodies.
    protected final Map<Long, CompletableFuture<JsonObject>> rpcs = new HashMap<Long, CompletableFuture<JsonObject>>();

    static final class DaemonThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) {
            Thread t = new Thread();
            t.setDaemon(true);
            t.setName("TimeoutDetector");
            return t;
        }
    }
    protected final ScheduledThreadPoolExecutor timeoutExecutor = new ScheduledThreadPoolExecutor(
            1000, Thread.ofVirtual().factory());

    // Our next message ID to generate
    public long nextMessageId = 0;

    public Node() {
    }

    // Generate a new message ID
    public long newMessageId() {
        final long id = nextMessageId;
        nextMessageId++;
        return id;
    }

    // Log a message to stderr.

    // Sending messages //////////////////////////////////////////////////////

    // Send a message to stdout
    public void send(final Message message) {
//        log("Sending  " + message.toJson());
        System.out.println(message.toJson());
//        System.out.flush();
    }

    // Send a message to a specific node. Automatically assigns a message ID if one
    // is not set.
    public void send(String dest, JsonObject body) {
        if (body.getLong("msg_id", -1) == -1) {
            body = Json.object().merge(body).set("msg_id", newMessageId());
        }
        send(new Message(nodeId, dest, body));
    }

    // Send an RPC request to another node. Returns a CompletableFuture which will
    // be delivered the response body when it arrives.
    public CompletableFuture<JsonObject> rpc(String dest, JsonObject request) {
        final CompletableFuture<JsonObject> f = new CompletableFuture<JsonObject>();
        final long id = newMessageId();
        rpcs.put(id, f);

        send(dest, Json.object().merge(request).set("msg_id", id));
        return f;
    }

    // Reply to a specific request message with a JsonObject body.
    public void reply(Message request, JsonObject body) {
        final Long msg_id = request.body.getLong("msg_id", -1);
        final JsonObject body2 = Json.object().merge(body).set("in_reply_to", msg_id);
        send(request.src, body2);
    }

    // Reply to a message with a Json-coercable object as the body.
    public void reply(Message request, IJson body) {
        reply(request, body.toJson().asObject());
    }

    // Handlers ////////////////////////////////////////////////////////////

    // Handle an init message, setting up our state.
    protected void handleInit(Message request) {
        this.nodeId = request.body.getString("node_id", null);
        this.nodeIdNumber = Long.parseLong(this.nodeId.split("n")[1]);
        for (JsonValue id : request.body.get("node_ids").asArray()) {
            this.nodeIds.add(id.asString());
        }
        log(String.format("I am %s", nodeIdNumber));
    }

    // Handle a reply to an RPC request we issued.
    public void handleReply(Message reply) {
        final JsonObject body = reply.body;
        final long in_reply_to = body.getLong("in_reply_to", -1);
        final CompletableFuture<JsonObject> f = rpcs.get(in_reply_to);
        if (f == null) {
            // Handler already triggered?
            return;
        }
        rpcs.remove(in_reply_to);
        if (body.getString("type", null).equals("error")) {
            // If we have an error, deliver an exception
            final long code = body.getLong("code", -1);
            final String text = body.getString("text", null);
            f.completeExceptionally(new Error(code, text));
        } else {
            // Normal completion
            f.complete(body);
        }
    }

    // Handle a message by looking up a request handler by the type of the message's
    // body, and calling it with the message.
    public void handleRequest(Message request) {
        final String type = request.body.getString("type", null);
        // You don't have to register a custom Init handler.
        if (type.equals("init")) {
            return;
        }
        handle(request);
    }

    protected abstract void handle(Message message);

    // Handles a parsed message from STDIN
    public void handleMessage(Message message) {
        final JsonObject body = message.body;
        final String type = body.getString("type", null);
        final long in_reply_to = body.getLong("in_reply_to", -1);
        log("Handling " + message);

        try {
            // Init messages are special: we always handle them ourselves in addition to
            // invoking any registered callback.
            if (type.equals("init")) {
                handleInit(message);
                reply(message, Json.object().add("type", "init_ok"));
            } else if (in_reply_to != -1) {
                // A reply to an RPC we issued.
                handleReply(message);
            } else {
                // Dispatch based on message type.
                handleRequest(message);
            }
        } catch (Error e) {
            // Send a message back to the client
            log(e.toString());
            reply(message, e);
        } catch (Exception e) {
            // Send a generic crash error
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String text = "Unexpected exception handling " +
                    message + ": " + e + "\n" + sw;
            log(text);
            reply(message, Error.crash(text));
        }
    }
}