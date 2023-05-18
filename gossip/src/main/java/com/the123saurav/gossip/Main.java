package com.the123saurav.gossip;

import com.eclipsesource.json.Json;
import com.the123saurav.common.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.the123saurav.common.Logger.log;

public class Main {
    public static void main(String[] args) throws IOException {
        GossipHandler node = new GossipHandler();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        Executor executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            while (true) {
                final String line = reader.readLine();
                final Message message = new Message(Json.parse(line).asObject());
                // This is mostly CPU intensive
                executor.execute(() -> node.handleMessage(message));
            }
        } catch (Throwable e) {
            log("Fatal error! " + e);
            e.printStackTrace();
            System.exit(1);
        } finally {
            reader.close();
        }
    }
}