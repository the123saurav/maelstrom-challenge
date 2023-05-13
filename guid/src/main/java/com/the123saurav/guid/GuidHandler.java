package com.the123saurav.guid;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.the123saurav.common.IJson;
import com.the123saurav.common.Message;
import com.the123saurav.common.Node;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class GuidHandler extends Node implements Consumer<Message> {

    private long currMs;
    private AtomicInteger counter = new AtomicInteger();

    /*
      1 bit for sign  - so we have 63 bits of the long.

      43 bit for epoch, this should cover ~278 years since then
      8 bit for nodeId - allowing for 256 nodes
      12 bit for num id per ms - allowing ~4000 request per ms per node
     */


/*

    record GenerateGuidMessage (
            String type,
            long msg_id
    ){}
*/

    @RequiredArgsConstructor
    class GenerateGuidResponse implements IJson {
        private static final String TYPE = "generate_ok";

        private final long id;

        @Override
        public JsonValue toJson() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("type", TYPE);
            jsonObject.add("id", id);
            return jsonObject;
        }
    }

    @Override
    public void accept(Message message) {
        /*
         Its okay, as long as time doesn't jump back which NTP guarantees,
         not using System.nanoTime as that can be reset across runs
         (although that's not the aim of this and will still pass the test
         , but wanted even this toy to be meaningful)
         */
        long nowMs = System.currentTimeMillis();
        if (nowMs > currMs) {
            currMs = nowMs;
            counter.set(0);
        }
        // Do not stomp sign bit as its already set correctly(+ve)
        nowMs = nowMs << 20;
        nowMs = nowMs | nodeIdNumberShifted;
        // get only last 12 bits
        nowMs = nowMs | (counter.getAndIncrement() & 0xFFFL);
        reply(message, new GenerateGuidResponse(nowMs));
    }
}
