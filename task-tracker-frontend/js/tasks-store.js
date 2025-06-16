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
    _addOrUpdate: function(taskData) {
        this._tasks.set(taskData.id, taskData);
        window.eventBus.trigger('task:updated', taskData);
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