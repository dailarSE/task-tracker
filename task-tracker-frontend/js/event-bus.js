/**
 * event-bus.js
 * Простая шина событий для слабой связи между компонентами UI.
 * Использует jQuery для простоты.
 */
window.eventBus = {
    _bus: $(document),

    /**
     * Подписаться на событие.
     * @param {string} eventName - Имя события (может содержать неймспейс, например, 'task:updated.editModal').
     * @param {Function} callback - Функция-обработчик.
     */
    on: function(eventName, callback) {
        this._bus.on(eventName, callback);
    },

    /**
     * Отписаться от события.
     * @param {string} eventName - Имя события с неймспейсом (например, '.editModal').
     */
    off: function(eventName) {
        this._bus.off(eventName);
    },

    /**
     * Сгенерировать событие.
     * @param {string} eventName - Имя события.
     * @param {*=} data - Данные для передачи в обработчик.
     */
    trigger: function(eventName, data) {
        this._bus.trigger(eventName, [data]);
    }
};