package com.example.tasktracker.emailsender.pipeline.sender;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Асинхронный оркестратор отправки почтовых сообщений.
 * Отвечает за маппинг результатов выполнения задач в статусы элементов {@link PipelineItem}.
 */
public interface Sender {
    /**
     * Запускает процесс асинхронной отправки для одного элемента.
     * <p>
     * Метод гарантирует, что возвращаемая {@link CompletableFuture} всегда завершается
     * <b>успешно</b>, даже если при отправке произошла ошибка.
     * Все исключения перехватываются внутри цепочки и транслируются в терминальные
     * статусы объекта {@link PipelineItem} (SENT, RETRY или FAILED).
     * <p>
     * Это позволяет вызывающему компоненту использовать результат без необходимости обрабатывать
     * исключения для каждого отдельного письма.
     *
     * @param item Элемент, содержащий данные для отправки и контекст для записи результата.
     * @return Фьюча, сигнализирующая о завершении логической обработки элемента.
     */
    CompletableFuture<Void> sendAsync(PipelineItem item);

    /**
     * Группирует список элементов в единый асинхронный чанк.
     * <p>
     * Создает агрегирующую фьючу, которая завершится только тогда, когда все письма
     * в чанке будут либо отправлены, либо помечены как ошибочные.
     *
     * @param chunk Список элементов для параллельной обработки.
     * @return Фьюча, представляющая собой завершение обработки всего чанка.
     */
    default CompletableFuture<Void> sendChunkAsync(List<PipelineItem> chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = chunk.stream()
                .map(this::sendAsync)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

}
