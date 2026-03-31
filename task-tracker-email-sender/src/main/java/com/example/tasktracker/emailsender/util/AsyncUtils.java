package com.example.tasktracker.emailsender.util;

import lombok.experimental.UtilityClass;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@UtilityClass
public class AsyncUtils {

    /**
     * Рекурсивно извлекает первопричину из стандартных оберток асинхронности.
     */
    public static Throwable unwrap(Throwable t) {
        Throwable result = t;
        while ((result instanceof CompletionException || result instanceof ExecutionException)
                && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }
}
