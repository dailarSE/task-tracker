package com.example.tasktracker.backend.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
@RequestMapping("/api/v1/throw")
public class ErrorThrowController {
    Random rnd = new Random();
    RuntimeException[] errs = new RuntimeException[]{
            new UnsupportedOperationException(),
            new IllegalStateException(),
            new NullPointerException(),
            new IllegalArgumentException(),
            new UnsupportedOperationException()};

    @GetMapping()
    public String generateException() {
        int i = rnd.nextInt(errs.length);
        throw errs[i];
    }
}
