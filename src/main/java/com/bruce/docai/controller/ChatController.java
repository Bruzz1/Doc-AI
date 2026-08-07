package com.bruce.docai.controller;

import com.bruce.docai.service.ChatService;
import com.bruce.docai.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@Slf4j
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;


    @GetMapping("/chat")
    public String chat(@RequestParam(value = "message") String question) {
        return chatService.chat(question);
    }

    @GetMapping("/faqs")
    public String getKnownInfo(@RequestParam(value = "question") String question, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return chatService.getKnownInfo(question, user.getOrganizationId());

    }


}
