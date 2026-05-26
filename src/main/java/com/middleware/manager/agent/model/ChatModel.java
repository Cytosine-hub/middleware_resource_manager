package com.middleware.manager.agent.model;

import java.util.List;

public interface ChatModel {
    String generate(List<Message> messages);

    record Message(String role, String content) {
        public static Message system(String content) { return new Message("system", content); }
        public static Message user(String content) { return new Message("user", content); }
        public static Message assistant(String content) { return new Message("assistant", content); }
    }
}
