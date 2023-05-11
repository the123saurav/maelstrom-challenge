package com.the123saurav.echo;

import com.eclipsesource.json.Json;
import com.the123saurav.common.Message;
import com.the123saurav.common.Node;

import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.the123saurav.common.Logger.log;

public class Main {
    public static ExecutorService executor = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        EchoHandler node = new EchoHandler();
        node.on("echo", node);

        final Scanner scanner = new Scanner(System.in);
        try {
            while (true) {
                final String line = scanner.nextLine();
                final Message message = new Message(Json.parse(line).asObject());
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