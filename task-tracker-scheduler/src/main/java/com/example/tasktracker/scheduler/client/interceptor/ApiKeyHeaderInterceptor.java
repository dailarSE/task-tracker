package com.example.tasktracker.scheduler.client.interceptor;

import com.example.tasktracker.scheduler.config.SchedulerAppProperties;
import lombok.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * ClientHttpRequestInterceptor, который автоматически добавляет заголовки,
 * необходимые для аутентификации M2M (machine-to-machine) вызовов к Backend API.
 * <p>
 * Этот интерцептор добавляет два заголовка:
 * <ul>
 *     <li>{@value #API_KEY_HEADER_NAME}: Статический API-ключ, идентифицирующий
 *     этот сервис как доверенного клиента.</li>
 *     <li>{@value #INSTANCE_ID_HEADER_NAME}: Уникальный идентификатор этого
 *     конкретного экземпляра (инстанса) сервиса, генерируемый при старте.</li>
 * </ul>
 * Он регистрируется как бин и внедряется в {@link org.springframework.web.client.RestClient}
 * для автоматического применения ко всем исходящим запросам.
 */
@Component
public class ApiKeyHeaderInterceptor implements ClientHttpRequestInterceptor {

    /**
     * Имя HTTP-заголовка для передачи API-ключа.
     */
    public static final String API_KEY_HEADER_NAME = "X-API-Key";
    /**
     * Имя HTTP-заголовка для передачи уникального ID экземпляра сервиса.
     */
    public static final String INSTANCE_ID_HEADER_NAME = "X-Service-Instance-Id";

    private final String apiKey;
    private final String instanceId;

    /**
     * Конструктор, который инициализирует интерцептор.
     * <p>
     * Извлекает API-ключ из предоставленных конфигурационных свойств и генерирует
     * уникальный {@code instanceId} для этого экземпляра приложения.
     *
     * @param properties Конфигурационные свойства приложения, содержащие API-ключ.
     */
    public ApiKeyHeaderInterceptor(SchedulerAppProperties properties) {
        this.apiKey = properties.getBackendClient().getApiKey();
        this.instanceId = UUID.randomUUID().toString();
    }

    /**
     * Перехватывает исходящий HTTP-запрос, добавляет в него заголовки
     * {@value #API_KEY_HEADER_NAME} и {@value #INSTANCE_ID_HEADER_NAME}
     * и передает выполнение дальше по цепочке.
     *
     * @param request  исходящий запрос
     * @param body     тело запроса
     * @param execution объект для продолжения выполнения запроса
     * @return ответ от сервера
     * @throws IOException в случае ошибки ввода-вывода
     */
    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull HttpRequest request, byte @NonNull [] body,
                                        @NonNull ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();
        headers.set(API_KEY_HEADER_NAME, apiKey);
        headers.set(INSTANCE_ID_HEADER_NAME, instanceId);
        return execution.execute(request, body);
    }
}