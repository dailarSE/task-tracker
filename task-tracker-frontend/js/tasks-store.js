/**
 * tasks-store.js
 * Синхронный кэш состояния задач (Single Source of Truth).
 * Генерирует события при изменении.
 */
window.tasksStore = {
    _tasks: new Map(),

    // --- ПУБЛИЧНЫЕ СИНХРОННЫЕ МЕТОДЫ ---
    get: function(taskId) { return this._tasks.get(taskId); },
    getAll: function() { return Array.from(this._tasks.values()).sort((a, b) => b.id - a.id); },

    // --- ПРИВАТНЫЕ МЕТОДЫ ДЛЯ ИЗМЕНЕНИЯ СОСТОЯНИЯ (вызываются из tasks.js) ---
    /**
     * Метод для обновления или добавления задачи в Store.
     * Сравнивает старое и новое состояние и генерирует гранулярные события.
     * @param {object} taskData - Полный объект задачи.
     * @private
     */
    _addOrUpdate: function(taskData) {
        const taskId = taskData.id;
        const oldTask = this._tasks.get(taskId);

        if (!oldTask) {
            this._tasks.set(taskId, taskData);
            window.eventBus.trigger('task:added', taskData);
            return;
        }

        // Обновляем кэш, чтобы подписчики получили актуальные данные
        this._tasks.set(taskId, taskData);

        // Проверяем каждое поле и генерируем событие, если оно изменилось
        if (oldTask.title !== taskData.title) {
            window.eventBus.trigger('task:title-updated', taskData);
        }
        if (oldTask.description !== taskData.description) {
            window.eventBus.trigger('task:description-updated', taskData);
        }
        if (oldTask.status !== taskData.status) {
            window.eventBus.trigger('task:status-changed', taskData);
        }
    },

    _remove: function(taskId) {
        if (this._tasks.delete(taskId)) {
            window.eventBus.trigger('task:deleted', { id: taskId });
        }
    },

    _init: function(tasksArray) {
        this._tasks.clear();
        if (tasksArray && tasksArray.length) {
            tasksArray.forEach(task => this._tasks.set(task.id, task));
        }
        window.eventBus.trigger('tasks:refreshed', this.getAll());
    }
};