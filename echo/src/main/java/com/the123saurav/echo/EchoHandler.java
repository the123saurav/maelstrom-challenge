package com.the123saurav.echo;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.the123saurav.common.IJson;
import com.the123saurav.common.Logger;
import com.the123saurav.common.Message;
import com.the123saurav.common.Node;
import lombok.RequiredArgsConstructor;

import java.util.function.Consumer;

public class EchoHandler extends Node implements Consumer<Message> {

    record EchoMessage(
            String type,
            long msg_id,
            String echo
    ){}

    @RequiredArgsConstructor
    class EchoResponse implements IJson {
        private static final String TYPE = "echo_ok";

        private final String echo;

        @Override
        public JsonValue toJson() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("type", TYPE);
            jsonObject.add("echo", echo);
            return jsonObject;
        }
    }

    @Override
    public void accept(Message message) {
        EchoMessage echoMessage = new EchoMessage(
                message.body.getString("type", null),
                message.body.getLong("msg_id", -1),
                message.body.getString("echo", null)
        );
        Logger.log("Message received is: " + echoMessage);
        reply(message, new EchoResponse(echoMessage.echo));
    }
}
