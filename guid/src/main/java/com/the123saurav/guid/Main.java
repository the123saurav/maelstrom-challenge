package com.the123saurav.guid;

import com.eclipsesource.json.Json;
import com.the123saurav.common.Message;

import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.the123saurav.common.Logger.log;

public class Main {
    public static void main(String[] args) {
        GuidHandler node = new GuidHandler();
        node.on("generate", node);

        final Scanner scanner = new Scanner(System.in);
        Executor executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try {
            while (true) {
                final String line = scanner.nextLine();
                final Message message = new Message(Json.parse(line).asObject());
                // This is mostly CPU intensive
                executor.execute(() -> node.handleMessage(message));
            }
        } catch (Throwable e) {
            log("Fatal error! " + e);
            e.printStackTrace();
            System.exit(1);
        } finally {
            scanner.close();
        }
    }
}