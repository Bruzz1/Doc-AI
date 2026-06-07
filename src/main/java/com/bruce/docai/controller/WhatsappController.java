package com.bruce.docai.controller;

import com.bruce.docai.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsappController {

    private final ChatService chatService;

    public WhatsappController(ChatService chatService) {
        this.chatService = chatService;
    }

    //TODO: webhook endpoint to ingest whatsapp messages

}
