package com.bruce.docai.controller;

import com.bruce.docai.config.WhatsappProperties;
import com.bruce.docai.service.WhatsappService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WhatsApp Cloud API webhook ({@code /webhook/whatsapp}) — the {@code WHATSAPP}
 * channel adapter.
 *
 * <ul>
 *   <li>{@code GET} performs Meta's subscription verification handshake.</li>
 *   <li>{@code POST} receives inbound messages: the raw body is signature-verified,
 *       then handed to {@link WhatsappService} for asynchronous processing so the
 *       endpoint acknowledges with 200 immediately.</li>
 * </ul>
 */
@RestController
@RequestMapping("/webhook/whatsapp")
@Slf4j
@RequiredArgsConstructor
public class WhatsappController {

    private final WhatsappService whatsappService;
    private final WhatsappProperties properties;

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        if ("subscribe".equals(mode)
                && properties.getVerifyToken() != null
                && !properties.getVerifyToken().isBlank()
                && properties.getVerifyToken().equals(verifyToken)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody byte[] payload,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {

        if (!whatsappService.isValidSignature(payload, signature)) {
            log.warn("Rejected WhatsApp webhook with invalid signature.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        whatsappService.handleInboundAsync(payload);
        return ResponseEntity.ok().build();
    }
}
