/**
 * Модуль для управления UI-компонентами, связанными с задачами.
 */
window.tasksUi = {
    // --- Кэшированные элементы UI ---
    $tasksContainer: $('#tasksContainer'),
    $createTaskForm: $('#createTaskForm'),
    $newTaskTitleInput: $('#newTaskTitle'),
    $undoneTasksList: $('#undoneTasksList'),
    $doneTasksList: $('#doneTasksList'),

    show: function() { this.$tasksContainer.show(); },
    hide: function() { this.$tasksContainer.hide(); },

    /**
     * Создает HTML-разметку для одной задачи.
     * @param {object} task - Объект задачи, полученный от API.
     * @returns {string} HTML-строка для <li> элемента.
     */
    renderTask: function(task) {
        // Добавляем класс 'done' для стилизации выполненных задач
        const doneClass = task.status === 'COMPLETED' ? 'class="done"' : '';
        // Используем data-атрибуты для хранения ID и других данных
        // И экранируем title, чтобы предотвратить XSS
        const escapedTitle = $('<div/>').text(task.title).html();
        return `<li data-task-id="${task.id}" ${doneClass}>${escapedTitle}</li>`;
    },

    /**
     * Очищает и заполняет списки задач на странице.
     * @param {Array<object>} tasks - Массив объектов задач.
     */
    renderTaskLists: function(tasks) {
        // Очищаем оба списка перед рендерингом
        this.$undoneTasksList.empty();
        this.$doneTasksList.empty();

        if (!tasks || tasks.length === 0) {
            // Отображаем сообщение, если задач нет
            this.$undoneTasksList.html('<li class="placeholder-item">У вас пока нет невыполненных задач.</li>');
            this.$doneTasksList.html('<li class="placeholder-item">Здесь будут появляться выполненные задачи.</li>');
            return;
        }

        tasks.forEach(task => {
            const taskHtml = this.renderTask(task);
            if (task.status === 'COMPLETED') {
                this.$doneTasksList.append(taskHtml);
            } else {
                this.$undoneTasksList.append(taskHtml);
            }
        });
    },

    clearCreateTaskForm: function() {
        window.ui.clearFormErrors(this.$createTaskForm);
        this.$newTaskTitleInput.val('');
    }
};