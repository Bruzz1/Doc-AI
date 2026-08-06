package com.bruce.docai.service;

public class UnsupportedDocumentTypeException extends IllegalArgumentException {

    public UnsupportedDocumentTypeException(String message) {
        super(message);
    }
}

